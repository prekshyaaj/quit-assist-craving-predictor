# pyrefly: ignore [missing-import]
import numpy as np
import pandas as pd

from Time_Gen import STEP_MINUTES


class SleepProfileGenerator:
    def __init__(self, seed: int = None):
        self.rng = np.random.default_rng(seed)
        # Set by every *_profile call: 1 where the user is in the main sleep
        # session (the first session in the template), 0 elsewhere
        self.is_main_sleep = None

    def _apply_sleep_sessions(
        self,
        hours: pd.Series,
        template: list,
        start_std: float,
        end_std: float,
        non_main_assignment_probability=None,
        non_main_visit_probability=None,
        min_non_main_minutes: float = 20.0,
    ) -> pd.Series:

        hour_values = hours.to_numpy()

        # Convert repeated daily hours into absolute hours
        is_day_start = (np.arange(len(hour_values))
                        == 0) | (hour_values == 0.0)
        abs_hours = (np.cumsum(is_day_start) - 1) * 24 + hour_values

        is_sleeping = pd.Series(0, index=hours.index, dtype=int)
        is_main_sleep = pd.Series(0, index=hours.index, dtype=int)

        # Handle defaults and broadcasting for probabilities
        num_non_main = max(0, len(template) - 1)

        if non_main_assignment_probability is None:
            assign_probs = [1.0] * num_non_main
        elif isinstance(non_main_assignment_probability, list):
            assign_probs = non_main_assignment_probability
        else:
            assign_probs = [non_main_assignment_probability] * num_non_main

        if non_main_visit_probability is None:
            visit_probs = [1.0] * num_non_main
        elif isinstance(non_main_visit_probability, list):
            visit_probs = non_main_visit_probability
        else:
            visit_probs = [non_main_visit_probability] * num_non_main

        for name, probs in (("non_main_assignment_probability", assign_probs),
                            ("non_main_visit_probability", visit_probs)):
            if len(probs) != num_non_main:
                raise ValueError(
                    f"{name} has {len(probs)} values but the template has "
                    f"{num_non_main} non-main session(s)")

        takes_non_main = [
            self.rng.random() < p
            for p in assign_probs
        ]

        def sample_schedule(day):
            sessions = []

            for i, (start_mean, end_mean) in enumerate(template):
                if i > 0:
                    idx = i - 1
                    if not takes_non_main[idx]:
                        continue
                    if not self.rng.random() < visit_probs[idx]:
                        continue

                start = day * 24 + self.rng.normal(start_mean, start_std)
                end = day * 24 + self.rng.normal(end_mean, end_std)

                # Session crosses midnight (decided by the template, not by the
                # noisy draws, so a short nap can't turn into a 24-hour sleep)
                if end_mean < start_mean:
                    end += 24
                # A non-main session (nap) that is happening lasts at least
                # min_non_main_minutes, even if noise made it end too early or
                # before it starts
                if i > 0:
                    end = max(end, start + min_non_main_minutes / 60.0)
                # Noise made the main session end before it starts: skip it
                if end <= start:
                    continue

                sessions.append((start, end, i == 0))  # first session = main sleep

            return sessions

        # Generate schedules from the previous day through the final day
        num_days = int(abs_hours[-1] // 24) + 1
        all_sessions = []

        for day in range(-1, num_days):
            all_sessions.extend(sample_schedule(day))

        # Mark sleeping hours (and which of them belong to the main sleep)
        for idx, abs_hour in enumerate(abs_hours):
            for start, end, is_main in all_sessions:
                if start <= abs_hour < end:
                    is_sleeping.iloc[idx] = 1
                    if is_main:
                        is_main_sleep.iloc[idx] = 1
                        break

        self.is_main_sleep = is_main_sleep
        return is_sleeping

    def night_sleeper_profile(
        self,
        hours: pd.Series,
        start_std: float = 0.3,
        end_std: float = 0.3,
        template=None,
        non_main_assignment_probability=None,
        non_main_visit_probability=None,
        min_non_main_minutes: float = 20.0,
    ) -> pd.Series:

        template = template or [(23.0, 7.0)]

        return self._apply_sleep_sessions(
            hours,
            template,
            start_std,
            end_std,
            non_main_assignment_probability,
            non_main_visit_probability,
            min_non_main_minutes,
        )

    def day_sleeper_profile(
        self,
        hours: pd.Series,
        start_std: float = 0.3,
        end_std: float = 0.3,
        template=None,
        non_main_assignment_probability=None,
        non_main_visit_probability=None,
        min_non_main_minutes: float = 20.0,
    ) -> pd.Series:

        template = template or [(8.0, 16.0)]

        return self._apply_sleep_sessions(
            hours,
            template,
            start_std,
            end_std,
            non_main_assignment_probability,
            non_main_visit_probability,
            min_non_main_minutes,
        )


def personal_days(is_main_sleep, slot_minutes: float = STEP_MINUTES):
    """
    Splits a series into the user's own days instead of calendar days.

    ``is_main_sleep`` marks the main sleep session (the first session in the
    sleep template, see ``SleepProfileGenerator.is_main_sleep``). A new day
    starts in the middle of each main sleep, so each day holds one full
    waking period even when it crosses midnight (day sleepers); other sleep
    sessions (naps) never start a new day. A main sleep touching the
    start/end of the series doesn't start a new day, since that would only
    create an all-asleep day. With no main sleep inside the series, falls
    back to calendar days.

    Returns
    -------
    day_ids : np.ndarray (one int per slot, 0, 1, 2, ...)
    day_weights : np.ndarray (one float per day)
        1.0 for a complete day. A day cut off by the start/end of the series
        while the user is awake (e.g. a day sleeper already awake at 00:00)
        gets its time outside main sleep / average time outside main sleep
        per 24 h, capped at 1, so per-day quotas and visit chances aren't
        applied in full to a partial day.
    """
    sleep = np.asarray(is_main_sleep) == 1
    n = len(sleep)
    steps_per_day = int(round(24 * 60 / slot_minutes))

    edges = np.flatnonzero(np.diff(np.r_[0, sleep.astype(int), 0]))
    boundaries = [
        (start + end) // 2
        for start, end in zip(edges[::2], edges[1::2])
        if start > 0 and end < n
    ]

    if not boundaries:
        day_ids = np.arange(n) // steps_per_day
        return day_ids, np.ones(day_ids[-1] + 1 if n else 0)

    starts_new_day = np.zeros(n, dtype=int)
    starts_new_day[boundaries] = 1
    day_ids = np.cumsum(starts_new_day)
    day_weights = np.ones(day_ids[-1] + 1)

    awake_per_24h = (~sleep).sum() / (n / steps_per_day)
    awake_per_day = np.bincount(day_ids, weights=(~sleep).astype(float))
    if awake_per_24h > 0:
        if not sleep[0]:
            day_weights[0] = min(1.0, awake_per_day[0] / awake_per_24h)
        if not sleep[-1]:
            day_weights[-1] = min(1.0, awake_per_day[-1] / awake_per_24h)

    return day_ids, day_weights


def hours_since_wake(is_main_sleep, slot_minutes: float = STEP_MINUTES):
    """
    Hours since the user last woke up from their main sleep, per slot.

    Counting starts at the first wake-up found in the series (0.0 in the
    first awake slot after a main sleep) and keeps running until the next
    wake-up, including through any other sleep sessions. Slots before the
    first wake-up are NaN, so routine-relative profiles don't apply there.
    """
    sleep = np.asarray(is_main_sleep) == 1
    n = len(sleep)

    # First awake slot after each main sleep
    wake_indices = np.flatnonzero(sleep[:-1] & ~sleep[1:]) + 1
    last_wake = np.full(n, -1)
    last_wake[wake_indices] = wake_indices
    last_wake = np.maximum.accumulate(last_wake) if n else last_wake

    hours = (np.arange(n) - last_wake) * slot_minutes / 60.0
    hours[last_wake < 0] = np.nan
    return hours


def hours_until_sleep(is_main_sleep, slot_minutes: float = STEP_MINUTES):
    """
    Hours until the user's next main sleep starts, per slot (the last awake
    slot before bedtime gives one slot's worth, e.g. 1/6 h). NaN while in
    main sleep; inf for awake slots whose next bedtime is past the end of
    the series (bedtime unknown).
    """
    sleep = np.asarray(is_main_sleep) == 1
    n = len(sleep)

    # First main-sleep slot of each sleep (bedtime)
    onset_indices = np.flatnonzero(~sleep[:-1] & sleep[1:]) + 1
    next_onset = np.full(n, n)
    next_onset[onset_indices] = onset_indices
    next_onset = np.minimum.accumulate(next_onset[::-1])[::-1] if n else next_onset

    hours = (next_onset - np.arange(n)) * slot_minutes / 60.0
    hours[next_onset >= n] = np.inf
    hours[sleep] = np.nan
    return hours


def add_wake_up_buffer(locations, is_sleeping, is_main_sleep, rng,
                       min_slots: int = 3, max_slots: int = 4):
    """
    Wherever the user goes straight from sleep (previous row) to a location
    other than Home (current row), wakes them up earlier: a random
    min_slots - max_slots rows before that row become awake (at Home), so
    they spend that time at home before leaving. is_main_sleep is cleared on
    the same rows so the wake-up time stays consistent.

    Returns the updated (is_sleeping, is_main_sleep) Series.
    """
    loc_values = locations.to_numpy(dtype=object)
    sleep = is_sleeping.to_numpy().copy()
    main_sleep = np.asarray(is_main_sleep).copy()

    for i in range(1, len(loc_values)):
        if sleep[i - 1] == 1 and loc_values[i] != "Home":
            start = max(0, i - int(rng.integers(min_slots, max_slots + 1)))
            sleep[start:i] = 0
            main_sleep[start:i] = 0

    return (pd.Series(sleep, index=is_sleeping.index),
            pd.Series(main_sleep, index=is_sleeping.index))
