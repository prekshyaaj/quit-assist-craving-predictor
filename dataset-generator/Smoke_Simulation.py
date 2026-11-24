import pandas as pd
import numpy as np

from Sleeping_Profiles import hours_since_wake, hours_until_sleep
from Time_Gen import STEP_MINUTES, STEPS_PER_DAY


def tsls_risk(tsls_minutes, low=0.015, high=0.35, max_minutes=120.0):
    """
    Smoking risk from the time since the last cigarette: `low` right after
    one, rising slowly at first and reaching `high` at `max_minutes`, flat
    after that. With the defaults: 0.015, 0.052 at 40 min, 0.35 from 120 min.
    """
    t = min(tsls_minutes, max_minutes)
    return low + (high - low) * (t / max_minutes) ** 2


def smoke_walk(base_risk, is_sleeping, draws, start, scale, tsls_params=None):
    """
    Goes once round the user's slots, starting with a cigarette at `start`
    and wrapping from the last slot back to the first. At every other slot
    TSLS grows by one step; the risk is base_risk OR tsls_risk, times
    `scale` (capped at 1), and a cigarette resets TSLS. No smoking while
    asleep. `draws` holds one fixed random number per slot. `tsls_params`
    optionally overrides tsls_risk's low / high / max_minutes.
    """
    tsls_params = tsls_params or {}
    n = len(base_risk)
    smoked = np.zeros(n, dtype=int)
    smoked[start] = 1
    tsls = 0.0
    for k in range(1, n):
        i = (start + k) % n
        tsls += STEP_MINUTES
        if is_sleeping[i] == 1:
            continue
        risk = 1 - (1 - base_risk[i]) * (1 - tsls_risk(tsls, **tsls_params))
        if draws[i] < min(1.0, scale * risk):
            smoked[i] = 1
            tsls = 0.0
    return smoked


def smoke_user(base_risk, is_sleeping, cigs_per_day, rng, tsls_params=None):
    """
    Smoking for one user. The first cigarette is placed at a random awake
    slot, weighted by location + time risk; the rest come from smoke_walk.
    The scale is chosen (by halving the search range) so the user smokes
    CIGNOW_A cigarettes per day over the whole series.
    """
    if base_risk.sum() == 0:
        return np.zeros(len(base_risk), dtype=int)

    start = rng.choice(len(base_risk), p=base_risk / base_risk.sum())
    draws = rng.random(len(base_risk))
    target = round(cigs_per_day * len(base_risk) / STEPS_PER_DAY)

    # At scale `high` every awake slot is certain to be a cigarette
    low, high = 0.0, 1.0 / base_risk[base_risk > 0].min()
    for _ in range(30):
        mid = (low + high) / 2
        if smoke_walk(base_risk, is_sleeping, draws, start, mid, tsls_params).sum() < target:
            low = mid
        else:
            high = mid
    return smoke_walk(base_risk, is_sleeping, draws, start, high, tsls_params)


def simulate_smoking(df: pd.DataFrame, time_profiles: list, rng, is_main_sleep=None,
                     tsls_params=None) -> pd.DataFrame:
    # is_main_sleep: main sleep session per row (SleepProfileGenerator.is_main_sleep),
    # in the same row order as df; decides when the user wakes up and goes to
    # bed. If None, every sleep counts as main sleep.
    # tsls_params: optional low / high / max_minutes for tsls_risk.
    # 5. Location Smoke Risk is already calculated per-user in generate_routine_general.py
    # df["location_smoke_risk"] = ...
    df = df.copy()  # don't modify the caller's DataFrame

    # Per task (by position, not df.index): hours since waking and until
    # bedtime, measured from the main sleep
    main_sleep_values = np.asarray(
        df["is_sleeping"] if is_main_sleep is None else is_main_sleep)
    hours_awake = np.full(len(df), np.nan)
    hours_to_bed = np.full(len(df), np.nan)
    for positions in df.groupby("task_id").indices.values():
        hours_awake[positions] = hours_since_wake(main_sleep_values[positions])
        hours_to_bed[positions] = hours_until_sleep(main_sleep_values[positions])
    # No time window applies during any sleep, naps included
    hours_to_bed[df["is_sleeping"].to_numpy() == 1] = np.nan

    # 6. Apply Time Smoke Risk using multiple functions; the profiles are
    # relative to the user's routine (hours since waking, hours until bedtime)
    df["time_smoke_risk"] = [
        max(prof(awake, to_bed) for prof in time_profiles)
        for awake, to_bed in zip(hours_awake, hours_to_bed)
    ]

    # 7. Generate "Did The User Smoke?" column
    # Location + time risk (probabilistic OR); no smoking while asleep
    location = df["location_smoke_risk"].to_numpy()
    time = df["time_smoke_risk"].to_numpy()
    sleeping = df["is_sleeping"].to_numpy()
    base_risk = np.where(sleeping == 1, 0.0, location + time - location * time)

    smoked = np.zeros(len(df), dtype=int)
    for positions in df.groupby("task_id").indices.values():
        cigs_per_day = df["CIGNOW_A"].to_numpy()[positions[0]]
        smoked[positions] = smoke_user(
            base_risk[positions], sleeping[positions], cigs_per_day, rng, tsls_params)
    df["Did The User Smoke?"] = smoked

    return df
