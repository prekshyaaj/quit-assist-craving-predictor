"""
Home screen endpoints.

The phone never talks to Supabase directly: it calls these endpoints with the user's
sign-in token, and the server reads/writes public.users and public.sensor_data.

sensor_data holds one row per 10-minute slot. The column
"Did the user smoke in next 10 mins?" = 1 marks a cigarette in that slot,
so "cigarettes on a day" = number of flagged slots on that (local) day.
"""
from collections import Counter
from datetime import date, datetime, time, timedelta, timezone

from fastapi import APIRouter, Depends, HTTPException, Query

from auth import get_user_id
from config import CIGARETTE_PRICE, LIFE_MINUTES_PER_CIGARETTE, logger, supabase
from models import DayCount, HomeSummary, ProgressStats

router = APIRouter(prefix="/home")

SLOT = timedelta(minutes=10)
SLOTS_PER_DAY = 144                                  # 24 h of 10-minute slots
PAGE_SIZE = 1000                                     # Supabase returns at most 1000 rows per call
SMOKED_FIELD = "Did the user smoke in next 10 mins?"  # name used in JSON bodies
SMOKED_COL = f'"{SMOKED_FIELD}"'                     # quoted name for filters / select


# ---------------------------------------------------------------- time helpers

def get_tz(tz_offset: int = Query(0, ge=-720, le=840, description="Phone's UTC offset in minutes")) -> timezone:
    """The phone sends its UTC offset so days start at the user's local midnight."""
    return timezone(timedelta(minutes=tz_offset))


def _parse_ts(value: str) -> datetime:
    return datetime.fromisoformat(value.replace("Z", "+00:00"))


def _floor_slot(moment: datetime) -> datetime:
    moment = moment.astimezone(timezone.utc)
    return moment.replace(minute=moment.minute - moment.minute % 10, second=0, microsecond=0)


def _day_bounds(day: date, tz: timezone) -> tuple[datetime, datetime]:
    start = datetime.combine(day, time(0), tz).astimezone(timezone.utc)
    return start, start + timedelta(days=1)


def _iso(moment: datetime) -> str:
    return moment.astimezone(timezone.utc).isoformat()


# ---------------------------------------------------------------- Supabase reads

def _fetch_all(build_query) -> list[dict]:
    """Page through a query, since one request returns at most PAGE_SIZE rows."""
    rows, offset = [], 0
    while True:
        page = build_query().range(offset, offset + PAGE_SIZE - 1).execute().data or []
        rows.extend(page)
        if len(page) < PAGE_SIZE:
            return rows
        offset += PAGE_SIZE


def _smoke_times(user_id: str, until: datetime) -> list[datetime]:
    rows = _fetch_all(lambda: (
        supabase.table("sensor_data")
        .select("recorded_at")
        .eq("user_id", user_id)
        .eq(SMOKED_COL, 1)
        .lte("recorded_at", _iso(until))
        .order("recorded_at")
    ))
    return [_parse_ts(r["recorded_at"]) for r in rows if r.get("recorded_at")]


def _edge_row(user_id: str, until: datetime, newest: bool) -> dict | None:
    res = (
        supabase.table("sensor_data")
        .select("recorded_at, TSLS")
        .eq("user_id", user_id)
        .lte("recorded_at", _iso(until))
        .order("recorded_at", desc=newest)
        .limit(1)
        .execute()
    )
    return res.data[0] if res.data else None


def _load_user(user_id: str) -> dict:
    res = supabase.table("users").select("created_at, CIGNOW_A").eq("id", user_id).maybe_single().execute()
    return (res.data if res else None) or {}


# ---------------------------------------------------------------- summary

def build_summary(user_id: str, tz: timezone) -> HomeSummary:
    now = datetime.now(timezone.utc)
    today = now.astimezone(tz).date()

    user = _load_user(user_id)
    smokes = _smoke_times(user_id, now)          # rows dated in the future are ignored
    first_row = _edge_row(user_id, now, newest=False)
    last_row = _edge_row(user_id, now, newest=True)

    # Tracking starts at whichever is earlier: account creation or the first sensor reading.
    starts = [_parse_ts(v) for v in (user.get("created_at"), first_row and first_row["recorded_at"]) if v]
    tracking_start = min(starts) if starts else now
    start_day = tracking_start.astimezone(tz).date()

    per_day = Counter(t.astimezone(tz).date() for t in smokes)

    week_start = today - timedelta(days=(today.weekday() + 1) % 7)   # Sunday
    week = [
        DayCount(
            date=d.isoformat(),
            cigarettes=per_day[d],
            tracked=start_day <= d <= today,
        )
        for d in (week_start + timedelta(days=i) for i in range(7))
    ]

    streak, d = 0, today
    while d >= start_day and per_day[d] == 0:
        streak += 1
        d -= timedelta(days=1)

    sober_coins = sum(
        1 for i in range((today - start_day).days)
        if per_day[start_day + timedelta(days=i)] == 0
    )

    # Cigarettes avoided = what the questionnaire baseline predicts minus what was logged.
    baseline_per_day = float(user.get("CIGNOW_A") or 0)
    elapsed_days = max((now - tracking_start).total_seconds() / 86400, 0)
    avoided = max(0, round(baseline_per_day * elapsed_days) - len(smokes))

    if smokes:
        since_last = int((now - smokes[-1]).total_seconds() // 60)
    elif last_row and last_row.get("TSLS") is not None:
        # TSLS = minutes since last smoke at that reading
        since_last = int(last_row["TSLS"] + (now - _parse_ts(last_row["recorded_at"])).total_seconds() // 60)
    else:
        since_last = None

    return HomeSummary(
        today=today.isoformat(),
        week=week,
        streak=streak,
        sober_coins=sober_coins,
        progress=ProgressStats(
            money_saved=round(avoided * CIGARETTE_PRICE, 2),
            cigarettes_avoided=avoided,
            life_regained_minutes=avoided * LIFE_MINUTES_PER_CIGARETTE,
            minutes_since_last_cigarette=since_last,
        ),
    )


# ---------------------------------------------------------------- smoke log writes

def _day_rows(user_id: str, day: date, tz: timezone) -> list[dict]:
    start, end = _day_bounds(day, tz)
    res = (
        supabase.table("sensor_data")
        .select(f"id, recorded_at, smoked:{SMOKED_COL}")
        .eq("user_id", user_id)
        .gte("recorded_at", _iso(start))
        .lt("recorded_at", _iso(end))
        .order("recorded_at")
        .limit(SLOTS_PER_DAY * 2)
        .execute()
    )
    return res.data or []


def _log_cigarette(user_id: str, tz: timezone, now: datetime) -> None:
    """Flag the current 10-minute slot, or the closest free slot before it on the same day."""
    today = now.astimezone(tz).date()
    day_start, _ = _day_bounds(today, tz)
    by_slot = {_floor_slot(_parse_ts(r["recorded_at"])): r for r in _day_rows(user_id, today, tz)}

    slot = _floor_slot(now)
    while slot >= day_start and by_slot.get(slot, {}).get("smoked"):
        slot -= SLOT
    if slot < day_start:
        raise HTTPException(status_code=400, detail="No free 10-minute slots left today")

    row = by_slot.get(slot)
    if row is not None:
        supabase.table("sensor_data").update({SMOKED_FIELD: 1}).eq("id", row["id"]).execute()
        return

    # sensor_data.user_id references users.id, so make sure the row exists.
    supabase.table("users").upsert({"id": user_id}, on_conflict="id").execute()
    local = slot.astimezone(tz)
    supabase.table("sensor_data").insert({
        "user_id": user_id,
        "recorded_at": _iso(slot),
        "time": round(local.hour + local.minute / 60, 4),
        "is_sleeping": 0,        # the user is awake to log it
        SMOKED_FIELD: 1,
    }).execute()


# ---------------------------------------------------------------- endpoints

@router.get("/summary", response_model=HomeSummary)
def get_summary(user_id: str = Depends(get_user_id), tz: timezone = Depends(get_tz)):
    try:
        return build_summary(user_id, tz)
    except Exception:
        logger.exception("Error building home summary")
        raise HTTPException(status_code=500, detail="Could not load home data")


@router.post("/smoke-log", response_model=HomeSummary)
def log_cigarette(user_id: str = Depends(get_user_id), tz: timezone = Depends(get_tz)):
    """'I smoked': marks the current 10-minute slot (or the closest free one before it)."""
    now = datetime.now(timezone.utc)
    try:
        _log_cigarette(user_id, tz, now)
        return build_summary(user_id, tz)
    except HTTPException:
        raise
    except Exception:
        logger.exception("Error logging cigarette")
        raise HTTPException(status_code=500, detail="Could not log cigarette")
