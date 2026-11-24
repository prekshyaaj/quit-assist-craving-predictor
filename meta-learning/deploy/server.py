"""
Server for the MAML + FcNet smoking model ("will this user smoke in the next 10 minutes?").

POST /adapt    "Authorization: Bearer <Supabase token>", no body. Reads the user's first day
               (MIN_HISTORY_ROWS, default 144, + 1 slot) from public.sensor_data and the profile from
               public.users. MAML adapts the meta-learned model to that user (fast adaptation, same settings as
               eval_maml_fcnet.py) and saves the adapted weights to public.users.personal_model
               (torch.save bytes, same layout as tabular/outputs/logs_maml_fcnet/Epoch_*.pt) and
               personal_model_updated_at. Returns no prediction.

POST /predict_adapted    The current slot + token: predicts with the user's saved weights
                         (mode "saved_model"); 404 if nothing is saved yet.
DELETE /personal_model   Token: clears the user's saved weights (to show /adapt again in a demo).
POST /predict_coldstart  The current slot: predicts with the meta-learned weights directly
                         (mode "cold_start", same as eval_cold_maml_fcnet.py). No token needed.

Feature engineering and scaling reuse tabular/scripts/Preprocessing.py and the scaler saved
in dataset_prepro_routine_generated_meta.pt, so inputs are processed exactly as in training.
Needs SUPABASE_URL + SUPABASE_KEY (Azure secrets) for /adapt and saved-model predictions.
"""

import io
import logging
import os
import sys
import threading
from datetime import datetime, timezone
from pathlib import Path
from typing import List, Literal, Optional

import numpy as np
import pandas as pd
import torch
from fastapi import FastAPI, Header, HTTPException
from pydantic import BaseModel, Field

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
sys.path.insert(0, str(ROOT / "tabular" / "scripts"))

from Preprocessing import (  # noqa: E402
    CONTINUOUS_COLS,
    EMP_STAT_CLASSES,
    FEATURE_COLS,
    LOCATION_CLASSES,
    TARGET_COL,
    engineer_features,
)
from Maml import Maml  # noqa: E402
from _utils import FocalLoss  # noqa: E402
import json  # noqa: E402

API_KEY = os.environ.get("API_KEY")
MIN_HISTORY_ROWS = int(os.environ.get("MIN_HISTORY_ROWS", "144"))
LOG_DIR = ROOT / "tabular" / "outputs" / "logs_maml_fcnet"
META_PATH = ROOT / "tabular" / "data" / "processed" / "dataset_prepro_routine_generated_meta.pt"
STEPS_PER_DAY = 144

torch.set_num_threads(1)

# ----------------------------------------------------------------------
# Load config, scaler and model once at startup
# ----------------------------------------------------------------------
with open(ROOT / "tabular" / "config.json") as f:
    CFG = json.load(f)
FCNET_CFG = CFG["fcnet"]
EVAL_CFG = CFG["fast_adaptation_eval"]
COLD_CFG = CFG["cold_start_eval"]
THRESHOLD = float(EVAL_CFG.get("classification_threshold", 0.5))
COLD_THRESHOLD = float(COLD_CFG.get("classification_threshold", 0.5))

META = torch.load(META_PATH, weights_only=False)
assert list(META["feature_cols"]) == list(FEATURE_COLS), "feature order differs from training"
assert list(META["continuous_cols"]) == list(CONTINUOUS_COLS), "continuous cols differ from training"
SCALER_MEAN = np.asarray(META["scaler_mean"], dtype=np.float32)
SCALER_STD = np.asarray(META["scaler_std"], dtype=np.float32)
TSLS_CAP = float(META["tsls_cap"])

CHECKPOINT_EPOCH = int(os.environ.get("CHECKPOINT_EPOCH") or max(
    int(p.stem.split("_")[1]) for p in LOG_DIR.glob("Epoch_*.pt")
))


def _split(eps_data, **kwargs):
    return {"x_t": eps_data["x_s"].squeeze(0), "y_t": eps_data["y_s"].squeeze(0)}


loss_cfg = EVAL_CFG.get("focal_loss", {"alpha": 0.25, "gamma": 2.0})
MAML_CONFIG = {
    "num_ways": 2,
    "k_shot": STEPS_PER_DAY * int(EVAL_CFG.get("support_days", 1)),
    "device": torch.device("cpu"),
    "network_architecture": "FcNet",
    "num_hidden_units": FCNET_CFG.get("num_hidden_units", (40, 40)),
    "activation": FCNET_CFG.get("activation", "relu"),
    "dropout_rate": FCNET_CFG.get("dropout_rate", 0.0),
    "use_layernorm": FCNET_CFG.get("use_layernorm", False),
    "train_val_split_function": _split,
    "num_inner_updates": EVAL_CFG.get("num_inner_updates", 5),
    "inner_lr": EVAL_CFG.get("inner_lr", 0.01),
    "meta_lr": 1e-3,
    "KL_weight": EVAL_CFG.get("KL_weight", 1e-6),
    "num_models": EVAL_CFG.get("num_models", 4),
    "first_order": EVAL_CFG.get("first_order", True),
    "train_flag": False,
    "resume_epoch": CHECKPOINT_EPOCH,
    "logdir": str(LOG_DIR),
    "loss_function": FocalLoss(alpha=loss_cfg.get("alpha", 0.25), gamma=loss_cfg.get("gamma", 2.0)),
}

MAML = Maml(config=MAML_CONFIG)
_dummy = [{"x_s": torch.zeros(1, 2, len(FEATURE_COLS)), "y_s": torch.zeros(1, 2, dtype=torch.int64)}]
MODEL = MAML.load_model(resume_epoch=CHECKPOINT_EPOCH, hyper_net_class=MAML.hyper_net_class, eps_dataloader=_dummy)
MODEL["optimizer"] = None  # never trained here
LOCK = threading.Lock()
PARAM_SHAPES = [tuple(p.shape) for p in MODEL["hyper_net"].params]

# Base checkpoint, reused so saved personal models keep the exact Epoch_*.pt layout.
BASE_CKPT = torch.load(LOG_DIR / f"Epoch_{CHECKPOINT_EPOCH}.pt", map_location="cpu", weights_only=True)

log = logging.getLogger("predict")
logging.basicConfig(level=logging.INFO)

# ----------------------------------------------------------------------
# Supabase: personal models in public.users.personal_model
# ----------------------------------------------------------------------
SUPABASE = None
if os.environ.get("SUPABASE_URL") and os.environ.get("SUPABASE_KEY"):
    from supabase import create_client
    # Only used for auth.get_user(token) and table reads/writes; never sign_in, so the
    # client always stays on the server key.
    SUPABASE = create_client(os.environ["SUPABASE_URL"], os.environ["SUPABASE_KEY"])


def _user_id_from_token(authorization: Optional[str]) -> str:
    """The Supabase user id of a valid "Bearer <token>"; 401 if missing or invalid."""
    if not authorization:
        raise HTTPException(401, "Missing sign-in token: send 'Authorization: Bearer <token>'")
    if SUPABASE is None:
        raise HTTPException(503, "Saved personal models are not configured on this server")
    if not authorization.startswith("Bearer "):
        raise HTTPException(401, "Authorization must be 'Bearer <token>'")
    try:
        res = SUPABASE.auth.get_user(authorization.removeprefix("Bearer ").strip())
    except Exception:
        res = None
    if not res or not res.user:
        raise HTTPException(401, "Invalid or expired sign-in token")
    return res.user.id


def _save_personal_model(user_id: str, adapted, history_rows_used: int, hr_baseline: Optional[float]) -> str:
    """Writes the adapted weights to users.personal_model and returns personal_model_updated_at."""
    ckpt = {
        "epoch": CHECKPOINT_EPOCH,
        "hyper_net_state_dict": {f"params.{i}": p.detach().clone() for i, p in enumerate(adapted.fast_params)},
        "opt_state_dict": BASE_CKPT["opt_state_dict"],
        "personalized": True,
        "history_rows_used": history_rows_used,
        "hr_baseline": hr_baseline,  # resting HR (bpm) estimated from the adaptation day, None if HR was a delta
    }
    buf = io.BytesIO()
    torch.save(ckpt, buf)
    updated_at = datetime.now(timezone.utc).isoformat()
    try:
        res = SUPABASE.table("users").update({
            "personal_model": "\\x" + buf.getvalue().hex(),   # bytea is sent as hex text
            "personal_model_updated_at": updated_at,
        }).eq("id", user_id).execute()
    except Exception:
        log.exception("Saving personal model failed for user %s", user_id)
        raise HTTPException(502, "Could not save the personal model to Supabase")
    if not res.data:
        raise HTTPException(404, "No public.users row for this user, so the personal model was not saved")
    return updated_at


def _load_personal_model(user_id: str):
    """(params, hr_baseline, updated_at) of the user's saved weights, or None
    (no row, NULL, unreadable or from another base epoch)."""
    try:
        res = (SUPABASE.table("users").select("personal_model, personal_model_updated_at")
               .eq("id", user_id).limit(1).execute())
        row = res.data[0] if res.data else None
        raw = row["personal_model"] if row else None
        if not raw:
            return None
        blob = bytes.fromhex(raw[2:]) if raw.startswith("\\x") else bytes.fromhex(raw)
        ckpt = torch.load(io.BytesIO(blob), map_location="cpu", weights_only=True)  # tensors only, no code
        if int(ckpt.get("epoch", -1)) != CHECKPOINT_EPOCH:
            log.info("Saved model of user %s is from epoch %s, server uses %s: ignored",
                     user_id, ckpt.get("epoch"), CHECKPOINT_EPOCH)
            return None
        state = ckpt["hyper_net_state_dict"]
        params = [state[f"params.{i}"].float() for i in range(len(PARAM_SHAPES))]
        if [tuple(p.shape) for p in params] != PARAM_SHAPES:
            return None
        return params, ckpt.get("hr_baseline"), row.get("personal_model_updated_at")
    except Exception:
        log.exception("Loading personal model failed for user %s", user_id)
        return None


# ----------------------------------------------------------------------
# API schema
# ----------------------------------------------------------------------
class Slot(BaseModel):
    time: float = Field(..., ge=0, le=24, description="Hour of day, e.g. 14.5 = 14:30")
    age: float = Field(..., gt=0)
    sex: Literal[1, 2] = Field(..., description="1 = male, 2 = female")
    cigs_per_day: float = Field(..., ge=0)
    emp_stat: int = Field(..., description=f"one of {EMP_STAT_CLASSES}")
    is_sleeping: Literal[0, 1]
    location: str = Field(..., description=f"one of {LOCATION_CLASSES}")
    tsls: float = Field(..., ge=0, description="Minutes since last smoke")
    hr: float = Field(..., description="Heart rate (bpm, or delta from resting)")


class HistorySlot(Slot):
    smoked: Literal[0, 1] = Field(..., description="Did the user smoke in this 10-minute slot")


class AdaptResponse(BaseModel):
    saved: bool
    history_rows_used: int
    resting_hr: Optional[float] = Field(None, description="Resting HR (bpm) used for this user, None if HR was a delta")
    checkpoint_epoch: int
    personal_model_updated_at: str


class PredictRequest(BaseModel):
    current: Slot
    resting_hr: Optional[float] = Field(
        None, description="User's resting HR in bpm, if hr is absolute bpm (else the value saved by /adapt is used)")


class PredictResponse(BaseModel):
    probability: float
    will_smoke: bool
    threshold: float
    mode: Literal["saved_model", "cold_start"]
    checkpoint_epoch: int
    personal_model_updated_at: Optional[str] = Field(None, description="When the saved model used was made")


# ----------------------------------------------------------------------
# Supabase: day-1 history from public.sensor_data (+ profile from public.users)
# ----------------------------------------------------------------------
SENSOR_TABLE = "sensor_data"
SENSOR_NEXT_SMOKE_COL = "Did the user smoke in next 10 mins?"


def _load_history_from_db(user_id: str) -> List["HistorySlot"]:
    """The user's first day (+1 slot for its label) from sensor_data, ordered by recorded_at,
    with the static profile from users (the questionnaire answers)."""
    if SUPABASE is None:
        raise HTTPException(503, "Supabase is not configured on this server")
    try:
        prof = (SUPABASE.table("users").select("AGEP_A, SEX_A, CIGNOW_A, EMP_STAT")
                .eq("id", user_id).limit(1).execute()).data
        rows = (SUPABASE.table(SENSOR_TABLE).select("*").eq("user_id", user_id)
                .order("recorded_at").order("id").limit(MIN_HISTORY_ROWS + 1).execute()).data
    except Exception:
        log.exception("Reading history from Supabase failed for user %s", user_id)
        raise HTTPException(502, "Could not read the user's history from Supabase")
    if not prof:
        raise HTTPException(404, "No public.users row for this user")
    p = prof[0]
    missing = [k for k in ("AGEP_A", "SEX_A", "CIGNOW_A", "EMP_STAT") if p.get(k) is None]
    if missing:
        raise HTTPException(422, f"Questionnaire not complete for this user (missing {', '.join(missing)})")
    if len(rows) < MIN_HISTORY_ROWS:
        raise HTTPException(
            422, f"Need at least {MIN_HISTORY_ROWS} rows in {SENSOR_TABLE} for this user, found {len(rows)}")
    # sensor_data stores "smoked in the NEXT slot"; /adapt works with "smoked in THIS slot",
    # so smoked[i] = next[i-1]. The shift in engineer_features then gives back next[i] as the label.
    nxt = [int(r.get(SENSOR_NEXT_SMOKE_COL) or 0) for r in rows]
    smoked = [0] + nxt[:-1]
    return [HistorySlot(
        time=float(r["time"]),
        age=float(p["AGEP_A"]),
        sex=int(p["SEX_A"]),
        cigs_per_day=float(p["CIGNOW_A"]),
        emp_stat=int(p["EMP_STAT"]),
        is_sleeping=int(r["is_sleeping"]),
        location=str(r["Location"]),
        tsls=float(r["TSLS"]),
        hr=float(r["HR"]),
        smoked=smoked[i],
    ) for i, r in enumerate(rows)]


# ----------------------------------------------------------------------
# Feature preparation (mirrors Preprocessing.process_csv)
# ----------------------------------------------------------------------
def _to_raw_df(rows: list, resting_hr: Optional[float]) -> pd.DataFrame:
    df = pd.DataFrame([{
        "time": r.time,
        "AGEP_A": r.age,
        "SEX_A": r.sex,
        "CIGNOW_A": r.cigs_per_day,
        "EMP_STAT": r.emp_stat,
        "is_sleeping": r.is_sleeping,
        "Location": r.location,
        "TSLS": r.tsls,
        "HR": r.hr,
        "Did The User Smoke?": getattr(r, "smoked", 0),
    } for r in rows])
    if resting_hr is not None and df["HR"].mean() > 40.0:
        df["HR"] = df["HR"] - resting_hr  # already a delta -> engineer_features leaves it alone
    return df


def _features(df_raw: pd.DataFrame):
    df = engineer_features(df_raw, tsls_cap=TSLS_CAP)
    hr_delta = df["HR"].values.astype(np.float32)
    df[CONTINUOUS_COLS] = (df[CONTINUOUS_COLS].values.astype(np.float32) - SCALER_MEAN) / SCALER_STD
    x = torch.tensor(df[FEATURE_COLS].values.astype(np.float32))
    y = torch.tensor(df[TARGET_COL].values.astype(np.int64))
    return x, y, hr_delta


def _validate(rows: list):
    for r in rows:
        if r.location not in LOCATION_CLASSES:
            raise HTTPException(422, f"location '{r.location}' must be one of {LOCATION_CLASSES}")
        if r.emp_stat not in EMP_STAT_CLASSES:
            raise HTTPException(422, f"emp_stat {r.emp_stat} must be one of {EMP_STAT_CLASSES}")


def _check_api_key(x_api_key: Optional[str]):
    if API_KEY and x_api_key != API_KEY:
        raise HTTPException(401, "Invalid API key")


# ----------------------------------------------------------------------
# Routes
# ----------------------------------------------------------------------
app = FastAPI(title="MAML + FcNet smoking prediction")


@app.get("/health")
def health():
    return {"status": "ok", "model": "MAML+FcNet", "checkpoint_epoch": CHECKPOINT_EPOCH}


@app.post("/adapt", response_model=AdaptResponse)
def adapt(
    x_api_key: Optional[str] = Header(default=None),
    authorization: Optional[str] = Header(default=None),
):
    """Personalize the model on the user's day 1 (public.sensor_data) and save it to public.users.personal_model."""
    _check_api_key(x_api_key)
    user_id = _user_id_from_token(authorization)
    history = _load_history_from_db(user_id)

    # One day of support + 1 so every support slot has a "smoked in next 10 min" label (as in training).
    history = history[-(MAML_CONFIG["k_shot"] + 1):]
    _validate(history)

    # HR baseline (when hr is absolute bpm) is estimated over the day, like per-task in Preprocessing.
    raw = _to_raw_df(history, None)
    x, y, hr_delta = _features(raw)
    # Resting HR that was subtracted (bpm), kept so /predict can convert single absolute readings.
    offset = float(history[0].hr - hr_delta[0])
    hr_baseline = offset if abs(offset) > 1e-6 else None

    # label of history[i] = smoked in history[i+1]; the last row's label is unknown, so it is dropped.
    x_s, y_s = x[:-1], y[:-1]
    with LOCK:
        adapted = MAML.adaptation(x=x_s, y=y_s, model=MODEL)
    updated_at = _save_personal_model(user_id, adapted, len(x_s), hr_baseline)

    return AdaptResponse(
        saved=True,
        history_rows_used=len(x_s),
        resting_hr=round(hr_baseline, 1) if hr_baseline is not None else None,
        checkpoint_epoch=CHECKPOINT_EPOCH,
        personal_model_updated_at=updated_at,
    )


def _predict_current(req: PredictRequest, saved) -> PredictResponse:
    """Predicts the current slot with saved personal weights, or the meta-learned weights if saved is None."""
    _validate([req.current])
    resting_hr = req.resting_hr
    if resting_hr is None and saved is not None:
        resting_hr = saved[1]
    x, _, _ = _features(_to_raw_df([req.current], resting_hr))

    with LOCK, torch.no_grad():
        if saved is not None:
            logits = MODEL["f_base_net"].forward(x, params=saved[0])
            mode, threshold, updated_at = "saved_model", THRESHOLD, saved[2]
        else:
            logits = MAML.prediction(x=x, adapted_hyper_net=MODEL["hyper_net"], model=MODEL)
            mode, threshold, updated_at = "cold_start", COLD_THRESHOLD, None

    prob = float(torch.softmax(logits, dim=1)[0, 1])
    return PredictResponse(
        probability=round(prob, 4),
        will_smoke=prob >= threshold,
        threshold=threshold,
        mode=mode,
        checkpoint_epoch=CHECKPOINT_EPOCH,
        personal_model_updated_at=updated_at,
    )


@app.post("/predict_adapted", response_model=PredictResponse)
def predict_adapted(
    req: PredictRequest,
    x_api_key: Optional[str] = Header(default=None),
    authorization: Optional[str] = Header(default=None),
):
    """Predict with the signed-in user's saved personal model (made by /adapt). Token required;
    404 if the user has no usable saved model."""
    _check_api_key(x_api_key)
    saved = _load_personal_model(_user_id_from_token(authorization))
    if saved is None:
        raise HTTPException(404, "No saved personal model for this user: call /adapt first")
    return _predict_current(req, saved)


@app.delete("/personal_model")
def delete_personal_model(
    x_api_key: Optional[str] = Header(default=None),
    authorization: Optional[str] = Header(default=None),
):
    """Deletes the signed-in user's saved personal model (users.personal_model -> NULL), so /adapt
    can be shown again from scratch. /predict_adapted returns 404 until /adapt runs again."""
    _check_api_key(x_api_key)
    user_id = _user_id_from_token(authorization)
    try:
        res = SUPABASE.table("users").update({
            "personal_model": None,
            "personal_model_updated_at": None,
        }).eq("id", user_id).execute()
    except Exception:
        log.exception("Deleting personal model failed for user %s", user_id)
        raise HTTPException(502, "Could not delete the personal model in Supabase")
    if not res.data:
        raise HTTPException(404, "No public.users row for this user")
    return {"deleted": True}


class Metrics(BaseModel):
    smoking_caught: int
    smoking_events: int
    false_alarms: int
    recall: float = Field(..., description="smoking_caught / smoking_events (0-1)")
    precision: float = Field(..., description="real alerts / all alerts (0-1)")
    macro_f1: float = Field(..., description="mean F1 of 'smoke' and 'no smoke' (0-1)")
    threshold: float


class EvaluateResponse(BaseModel):
    test_slots: int = Field(..., description="slots after day 1 that were scored")
    smoking_events: int
    cold_start: Metrics
    personalised: Optional[Metrics] = Field(None, description="None until /adapt has been run")
    personal_model_updated_at: Optional[str] = None
    checkpoint_epoch: int


def _load_all_sensor_rows(user_id: str):
    """Profile + every sensor_data row of the user (ordered like /adapt), paged past PostgREST's 1000-row limit."""
    if SUPABASE is None:
        raise HTTPException(503, "Supabase is not configured on this server")
    try:
        prof = (SUPABASE.table("users").select("AGEP_A, SEX_A, CIGNOW_A, EMP_STAT")
                .eq("id", user_id).limit(1).execute()).data
        rows, page = [], 1000
        while True:
            batch = (SUPABASE.table(SENSOR_TABLE).select("*").eq("user_id", user_id)
                     .order("recorded_at").order("id").range(len(rows), len(rows) + page - 1).execute()).data
            rows += batch
            if len(batch) < page or len(rows) >= 10_000:
                break
    except Exception:
        log.exception("Reading sensor data from Supabase failed for user %s", user_id)
        raise HTTPException(502, "Could not read the user's data from Supabase")
    if not prof:
        raise HTTPException(404, "No public.users row for this user")
    p = prof[0]
    if any(p.get(k) is None for k in ("AGEP_A", "SEX_A", "CIGNOW_A", "EMP_STAT")):
        raise HTTPException(422, "Questionnaire not complete for this user")
    return p, rows


def _metrics(y: np.ndarray, prob: np.ndarray, threshold: float) -> Metrics:
    pred = prob >= threshold
    tp = int(np.sum(pred & (y == 1)))
    fp = int(np.sum(pred & (y == 0)))
    fn = int(np.sum(~pred & (y == 1)))
    tn = int(np.sum(~pred & (y == 0)))
    f1_smoke = 2 * tp / (2 * tp + fp + fn) if (tp + fp + fn) else 1.0
    f1_none = 2 * tn / (2 * tn + fn + fp) if (tn + fn + fp) else 1.0
    return Metrics(
        smoking_caught=tp, smoking_events=tp + fn, false_alarms=fp,
        recall=round(tp / (tp + fn), 4) if (tp + fn) else 0.0,
        precision=round(tp / (tp + fp), 4) if (tp + fp) else 0.0,
        macro_f1=round((f1_smoke + f1_none) / 2, 4),
        threshold=threshold,
    )


@app.get("/evaluate", response_model=EvaluateResponse)
def evaluate(
    x_api_key: Optional[str] = Header(default=None),
    authorization: Optional[str] = Header(default=None),
):
    """Scores the signed-in user's own data after day 1 (day 1 is what /adapt learns from):
    cold-start vs their saved personal model. Recall, precision and macro F1 at the server thresholds.
    Nothing is saved."""
    _check_api_key(x_api_key)
    user_id = _user_id_from_token(authorization)
    p, rows = _load_all_sensor_rows(user_id)
    k = MAML_CONFIG["k_shot"]
    if len(rows) <= k + 1:
        raise HTTPException(422, f"Need more than one day of sensor_data to evaluate (have {len(rows)} rows, day 1 = {k})")

    nxt = [int(r.get(SENSOR_NEXT_SMOKE_COL) or 0) for r in rows]
    slots = [Slot(
        time=float(r["time"]), age=float(p["AGEP_A"]), sex=int(p["SEX_A"]), cigs_per_day=float(p["CIGNOW_A"]),
        emp_stat=int(p["EMP_STAT"]), is_sleeping=int(r["is_sleeping"]), location=str(r["Location"]),
        tsls=float(r["TSLS"]), hr=float(r["HR"]),
    ) for r in rows]
    _validate(slots)

    # Resting HR from day 1, exactly as /adapt works it out, then used for every test slot.
    _, _, hr_day1 = _features(_to_raw_df(slots[:k + 1], None))
    offset = float(slots[0].hr - hr_day1[0])
    resting = offset if abs(offset) > 1e-6 else None

    x_q, _, _ = _features(_to_raw_df(slots[k:], resting))
    y_q = np.asarray(nxt[k:], dtype=np.int64)  # sensor_data already stores "smoked in the next 10 min"

    saved = _load_personal_model(user_id)
    with LOCK, torch.no_grad():
        p_cold = torch.softmax(MAML.prediction(x=x_q, adapted_hyper_net=MODEL["hyper_net"], model=MODEL), dim=1)[:, 1]
        p_pers = (torch.softmax(MODEL["f_base_net"].forward(x_q, params=saved[0]), dim=1)[:, 1]
                  if saved is not None else None)

    return EvaluateResponse(
        test_slots=len(y_q),
        smoking_events=int(y_q.sum()),
        cold_start=_metrics(y_q, p_cold.numpy(), COLD_THRESHOLD),
        personalised=_metrics(y_q, p_pers.numpy(), THRESHOLD) if p_pers is not None else None,
        personal_model_updated_at=saved[2] if saved is not None else None,
        checkpoint_epoch=CHECKPOINT_EPOCH,
    )


@app.post("/predict_coldstart", response_model=PredictResponse)
def predict_coldstart(req: PredictRequest, x_api_key: Optional[str] = Header(default=None)):
    """Predict with the meta-learned (not personalized) model. No token needed."""
    _check_api_key(x_api_key)
    return _predict_current(req, None)
