"""Cold-start vs personalised MAML-FcNet on the two persona test cases.

Same model and settings as the deployed server and eval_maml_fcnet.py:
checkpoint = last Epoch_*.pt in tabular/outputs/logs_maml_fcnet, scaler from
tabular/data/processed/dataset_prepro_routine_generated_meta.pt, adaptation and
threshold from tabular/config.json ("fast_adaptation_eval" / "cold_start_eval").
Support = day 1 (144 slots), query = days 2-4 (432 slots).

Nothing is written or changed. Run from anywhere:
    python run_persona_eval.py --repo /path/to/few_shot_meta_learning
"""
import argparse
import glob
import json
import sys
from pathlib import Path

import numpy as np
import pandas as pd
import torch
from sklearn.metrics import classification_report, confusion_matrix, f1_score

HERE = Path(__file__).resolve().parent
CASES = [
    ("Person A: office worker, 69 M, heavy smoker (30/day)", HERE / "personA_office_heavy_routine.csv"),
    ("Person B: student, 30 M, light smoker (5/day)", HERE / "personB_student_light_routine.csv"),
]
STEPS = 144

ap = argparse.ArgumentParser()
ap.add_argument("--repo", required=True, help="path to few_shot_meta_learning")
args = ap.parse_args()
REPO = Path(args.repo).resolve()
sys.path.insert(0, str(REPO))
sys.path.insert(0, str(REPO / "tabular" / "scripts"))

from Preprocessing import CONTINUOUS_COLS, FEATURE_COLS, TARGET_COL, engineer_features  # noqa: E402
from Maml import Maml  # noqa: E402
from _utils import FocalLoss  # noqa: E402

cfg = json.loads((REPO / "tabular" / "config.json").read_text())
ev, cold_cfg, fc = cfg["fast_adaptation_eval"], cfg["cold_start_eval"], cfg["fcnet"]
log_dir = REPO / "tabular" / "outputs" / "logs_maml_fcnet"
epoch = max(int(Path(p).stem.split("_")[1]) for p in glob.glob(str(log_dir / "Epoch_*.pt")))
meta = torch.load(REPO / "tabular" / "data" / "processed" / "dataset_prepro_routine_generated_meta.pt",
                  weights_only=False)
mean = np.asarray(meta["scaler_mean"], dtype=np.float32)
std = np.asarray(meta["scaler_std"], dtype=np.float32)
loss = ev.get("focal_loss", {"alpha": 0.25, "gamma": 2.0})

maml = Maml(config={
    "num_ways": 2, "k_shot": STEPS * int(ev.get("support_days", 1)), "device": torch.device("cpu"),
    "network_architecture": "FcNet", "num_hidden_units": fc.get("num_hidden_units", (40, 40)),
    "activation": fc.get("activation", "relu"), "dropout_rate": fc.get("dropout_rate", 0.0),
    "use_layernorm": fc.get("use_layernorm", False),
    "train_val_split_function": lambda eps_data, **k: {"x_t": eps_data["x_s"].squeeze(0), "y_t": eps_data["y_s"].squeeze(0)},
    "num_inner_updates": ev.get("num_inner_updates", 5), "inner_lr": ev.get("inner_lr", 0.01),
    "meta_lr": 1e-3, "KL_weight": ev.get("KL_weight", 1e-6), "num_models": ev.get("num_models", 4),
    "first_order": ev.get("first_order", True), "train_flag": False, "resume_epoch": epoch,
    "logdir": str(log_dir), "loss_function": FocalLoss(alpha=loss["alpha"], gamma=loss["gamma"]),
})
dummy = [{"x_s": torch.zeros(1, 2, len(FEATURE_COLS)), "y_s": torch.zeros(1, 2, dtype=torch.int64)}]
model = maml.load_model(resume_epoch=epoch, hyper_net_class=maml.hyper_net_class, eps_dataloader=dummy)
thr_pers = float(ev.get("classification_threshold", 0.5))
thr_cold = float(cold_cfg.get("classification_threshold", 0.5))
print(f"Checkpoint Epoch_{epoch} | adaptation lr {ev.get('inner_lr')} x {ev.get('num_inner_updates')} steps "
      f"| thresholds: personalised {thr_pers}, cold {thr_cold}")


def report(name, y, p, thr):
    yp = (p >= thr).astype(int)
    cm = confusion_matrix(y, yp, labels=[0, 1])
    (tn, fp), (fn, tp) = cm
    print(f"\n--- {name} ---")
    print(f"Smoking caught {tp}/{tp + fn} | False alarms {fp} | Macro F1 {100 * f1_score(y, yp, average='macro'):.2f}")
    print("Confusion matrix [[TN, FP], [FN, TP]]:", cm.tolist())
    print(classification_report(y, yp, target_names=["Not Smoking (0)", "Smoking (1)"], zero_division=0))


for title, path in CASES:
    df = engineer_features(pd.read_csv(path), tsls_cap=float(meta["tsls_cap"]))
    df[CONTINUOUS_COLS] = (df[CONTINUOUS_COLS].values.astype(np.float32) - mean) / std
    x = torch.tensor(df[FEATURE_COLS].values.astype(np.float32))
    y = torch.tensor(df[TARGET_COL].values.astype(np.int64))
    x_s, y_s, x_q, y_q = x[:STEPS], y[:STEPS], x[STEPS:], y[STEPS:]
    with torch.no_grad():
        p_cold = torch.softmax(maml.prediction(x=x_q, adapted_hyper_net=model["hyper_net"], model=model), 1)[:, 1]
    adapted = maml.adaptation(x=x_s, y=y_s, model=model)
    with torch.no_grad():
        p_pers = torch.softmax(maml.prediction(x=x_q, adapted_hyper_net=adapted, model=model), 1)[:, 1]
    print("\n" + "=" * 70 + f"\n{title}\nSupport {list(x_s.shape)} | Query {list(x_q.shape)}\n" + "=" * 70)
    report("Cold-start", y_q.numpy(), p_cold.numpy(), thr_cold)
    report("Personalised (adapted on day 1)", y_q.numpy(), p_pers.numpy(), thr_pers)
