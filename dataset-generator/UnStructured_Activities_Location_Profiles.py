import math

# pyrefly: ignore [missing-import]
import numpy as np
import pandas as pd

from Sleeping_Profiles import personal_days


class LocationProfileGenerator_Unstructured:
    """
    Schedules unstructured (discretionary) activities into time slots
    that are NOT already occupied by a structured activity or sleep.

    Workflow
    --------
    1. The caller passes in the ``locations`` Series (already populated by
       ``LocationProfileGenerator_Structured``) and the ``is_sleeping``
       Series (from ``SleepProfileGenerator``).
    2. This class identifies free gaps (locations still set to the
       ``free_label``, default ``"Home"``, AND ``is_sleeping == 0``).
    3. For each activity it probabilistically inserts sessions into those
       gaps, respecting per-activity visit probability, preferred time
       windows, duration bounds, and a per-day visit cap. A "day" is the
       user's own day (it changes in the middle of each main sleep), so a
       waking period that crosses midnight stays in one day.

    Intervals are represented as plain ``(start_idx, end_idx)`` tuples
    (end exclusive) rather than dicts — nothing downstream needs absolute
    hour boundaries, only slot indices.
    """

    def __init__(self, seed: int = None, is_main_sleep: pd.Series = None):
        self.rng = np.random.default_rng(seed)
        # Main sleep session (SleepProfileGenerator.is_main_sleep); decides
        # where each day starts. If None, every sleep counts as main sleep.
        self.is_main_sleep = is_main_sleep

    @staticmethod
    def _normalize_profile(profile_type: str) -> str:
        return profile_type.lower().replace("_profile", "")

    # ------------------------------------------------------------------
    # Interval helpers
    # ------------------------------------------------------------------
    @staticmethod
    def _find_free_intervals(loc_values, sleep_values, start_idx, end_idx, free_label):
        """
        Contiguous free (start, end) index ranges within [start_idx, end_idx).
        A slot is free when loc_values[i] == free_label AND sleep_values[i] == 0.

        Only scans the given day's slice, so total cost across all days for
        one activity is O(total_slots), not O(num_days * total_slots).
        """
        intervals = []
        run_start = None
        for i in range(start_idx, end_idx):
            is_free = loc_values[i] == free_label and sleep_values[i] == 0
            if is_free:
                run_start = i if run_start is None else run_start
            elif run_start is not None:
                intervals.append((run_start, i))
                run_start = None
        if run_start is not None:
            intervals.append((run_start, end_idx))
        return intervals

    @staticmethod
    def _is_in_preferred_window(hour_of_day, preferred_windows):
        """Return True if *hour_of_day* falls inside any preferred window (handles wraparound)."""
        for centre, half_w in preferred_windows:
            low, high = centre - half_w, centre + half_w
            if low < 0:
                if hour_of_day >= low % 24 or hour_of_day <= high:
                    return True
            elif high > 24:
                if hour_of_day >= low or hour_of_day <= high % 24:
                    return True
            elif low <= hour_of_day <= high:
                return True
        return False

    def _score_interval(self, hour_values, interval, preferred_windows, time_step, base_weight=0.1):
        """
        score = preference_score * sqrt(duration_in_hours), where
        preference_score = base_weight + (1 - base_weight) * overlap_fraction.

        Long intervals that only partially overlap a preferred window still
        score well — they offer more placement flexibility, so they aren't
        penalised the way a naive overlap-only score would penalise them.
        """
        start_idx, end_idx = interval
        n_slots = end_idx - start_idx
        if n_slots == 0:
            return 0.0

        overlap = sum(
            self._is_in_preferred_window(hour_values[i], preferred_windows)
            for i in range(start_idx, end_idx)
        )
        preference_score = base_weight + \
            (1.0 - base_weight) * (overlap / n_slots)
        return preference_score * np.sqrt(n_slots * time_step)

    def _pick_start_in_interval(self, hour_values, interval, duration_slots, preferred_windows, base_weight=0.15):
        """
        Choose a start index inside *interval* biased toward preferred hours.
        Valid starts are those where the full activity still fits; each is
        weighted 1.0 inside a preferred window, base_weight otherwise.
        """
        start_idx, end_idx = interval
        valid_starts = list(range(start_idx, end_idx - duration_slots + 1))
        if not valid_starts:
            return start_idx  # caller pre-filters by size, so this shouldn't happen

        weights = [
            1.0 if self._is_in_preferred_window(
                hour_values[i], preferred_windows) else base_weight
            for i in valid_starts
        ]
        probs = np.array(weights) / sum(weights)
        return self.rng.choice(valid_starts, p=probs)

    @staticmethod
    def _remove_from_intervals(intervals, start, end):
        """Free intervals with [start, end) cut out; the pieces either side are kept."""
        remaining = []
        for iv_start, iv_end in intervals:
            if iv_start < min(iv_end, start):
                remaining.append((iv_start, min(iv_end, start)))
            if max(iv_start, end) < iv_end:
                remaining.append((max(iv_start, end), iv_end))
        return remaining

    # ------------------------------------------------------------------
    # Core gap-filling engine
    # ------------------------------------------------------------------
    def _apply_gap_activity(
        self,
        locations: pd.Series,
        hours: pd.Series,
        is_sleeping: pd.Series,
        location_name: str,
        preferred_windows: list,
        visit_probability: float,
        duration_mean: float,
        duration_std: float,
        max_visits_per_day: int,
        free_label: str = "Home",
        function_assignment_probability: float = 1.0,
        cooldown_hours: float = 2.0,
    ) -> pd.Series:
        if self.rng.random() > function_assignment_probability:
            return locations

        hour_values = hours.to_numpy()
        loc_values = locations.to_numpy(dtype=object).copy()
        sleep_values = is_sleeping.to_numpy()

        n_slots = len(hour_values)
        time_step = float(
            hour_values[1] - hour_values[0]) if n_slots > 1 else 1 / 6
        cooldown_slots = int(round(cooldown_hours / time_step))

        # A "day" here is the user's own day, which changes in the middle of
        # each main sleep (see personal_days). It can cross midnight, so free
        # time such as 23:00-01:00 stays one gap instead of being cut at
        # 24:00, and visit_probability / max_visits_per_day apply once per
        # waking day. Days cut off by the start/end of the series while the
        # user is awake get a proportionally smaller visit chance.
        main_sleep_values = (sleep_values if self.is_main_sleep is None
                             else self.is_main_sleep.to_numpy())
        day_ids, day_weights = personal_days(
            main_sleep_values, slot_minutes=time_step * 60)
        day_start_indices = np.flatnonzero(np.r_[True, np.diff(day_ids) != 0])
        day_end_indices = np.append(day_start_indices[1:], n_slots)

        for day, (start_idx, end_idx) in enumerate(zip(day_start_indices, day_end_indices)):
            if self.rng.random() > visit_probability * day_weights[day]:
                continue

            free_intervals = self._find_free_intervals(
                loc_values, sleep_values, start_idx, end_idx, free_label)
            visits_today = 0

            for _ in range(self.rng.integers(1, max_visits_per_day + 1)):
                if visits_today >= max_visits_per_day:
                    break

                dur = max(time_step, self.rng.normal(
                    duration_mean, duration_std))
                duration_slots = max(1, math.ceil(dur / time_step))

                candidates = [
                    iv for iv in free_intervals
                    if (iv[1] - iv[0]) >= duration_slots
                ]
                if not candidates:
                    break  # no interval fits — done for this day

                weights = [
                    self._score_interval(
                        hour_values, iv, preferred_windows, time_step)
                    for iv in candidates
                ]
                probs = np.array(weights) / sum(weights)
                chosen_local = self.rng.choice(len(candidates), p=probs)
                chosen_interval = candidates[chosen_local]

                start_i = self._pick_start_in_interval(
                    hour_values, chosen_interval, duration_slots, preferred_windows,
                )
                end_i = start_i + duration_slots

                loc_values[start_i:end_i] = location_name

                # The visit plus its cooldown on both sides is no longer free
                # for this activity, so its next visit can't start within
                # cooldown_hours of this one
                free_intervals = self._remove_from_intervals(
                    free_intervals, start_i - cooldown_slots, end_i + cooldown_slots)
                visits_today += 1

        result = locations.copy()  # don't modify the caller's Series
        result[:] = loc_values
        return result

    # ------------------------------------------------------------------
    # Per-activity public methods
    # ------------------------------------------------------------------
    def apply_gym(
        self,
        locations,
        hours,
        is_sleeping,
        profile_type,
        visit_probability=0.40,
        duration_mean=1.5,
        duration_std=0.4,
        max_visits_per_day=1,
        preferred_windows=None,
        free_label="Home",
        function_assignment_probability=1.0,
        cooldown_hours=2.0,
    ):
        profile = self._normalize_profile(profile_type)

        windows = {
            "night_sleeper": [(7.0, 2.0), (17.0, 2.0)],
            "day_sleeper": [(20.0, 3.0)],
        }

        if preferred_windows is None:
            if profile not in windows:
                raise ValueError(
                    f"Unknown profile_type {profile_type!r} for 'Gym'; expected one of {list(windows)}"
                )
            preferred_windows = windows[profile]

        return self._apply_gap_activity(
            locations,
            hours,
            is_sleeping,
            "Gym",
            preferred_windows,
            visit_probability,
            duration_mean,
            duration_std,
            max_visits_per_day,
            free_label,
            function_assignment_probability,
            cooldown_hours,
        )

    def apply_restaurant(
        self,
        locations,
        hours,
        is_sleeping,
        profile_type,
        visit_probability=0.35,
        duration_mean=1.0,
        duration_std=0.3,
        max_visits_per_day=2,
        preferred_windows=None,
        free_label="Home",
        function_assignment_probability=1.0,
        cooldown_hours=2.0,
    ):
        profile = self._normalize_profile(profile_type)

        windows = {
            "night_sleeper": [(12.5, 1.5), (19.5, 1.5)],
            "day_sleeper": [(18.0, 2.0), (23.0, 2.0)],
        }

        if preferred_windows is None:
            if profile not in windows:
                raise ValueError(
                    f"Unknown profile_type {profile_type!r} for 'Restaurant'; expected one of {list(windows)}"
                )
            preferred_windows = windows[profile]

        return self._apply_gap_activity(
            locations,
            hours,
            is_sleeping,
            "Restaurant",
            preferred_windows,
            visit_probability,
            duration_mean,
            duration_std,
            max_visits_per_day,
            free_label,
            function_assignment_probability,
            cooldown_hours,
        )

    def apply_market(
        self,
        locations,
        hours,
        is_sleeping,
        profile_type,
        visit_probability=0.20,
        duration_mean=1.0,
        duration_std=0.3,
        max_visits_per_day=1,
        preferred_windows=None,
        free_label="Home",
        function_assignment_probability=1.0,
        cooldown_hours=2.0,
    ):
        profile = self._normalize_profile(profile_type)

        windows = {
            "night_sleeper": [(8.0, 2.0), (17.0, 2.0)],
            "day_sleeper": [(19.0, 3.0)],
        }

        if preferred_windows is None:
            if profile not in windows:
                raise ValueError(
                    f"Unknown profile_type {profile_type!r} for 'Market'; expected one of {list(windows)}"
                )
            preferred_windows = windows[profile]

        return self._apply_gap_activity(
            locations,
            hours,
            is_sleeping,
            "Market",
            preferred_windows,
            visit_probability,
            duration_mean,
            duration_std,
            max_visits_per_day,
            free_label,
            function_assignment_probability,
            cooldown_hours,
        )

    def apply_public_place(
        self,
        locations,
        hours,
        is_sleeping,
        profile_type,
        visit_probability=0.30,
        duration_mean=1.25,
        duration_std=0.5,
        max_visits_per_day=2,
        preferred_windows=None,
        free_label="Home",
        function_assignment_probability=1.0,
        cooldown_hours=2.0,
    ):
        profile = self._normalize_profile(profile_type)

        windows = {
            "night_sleeper": [(10.0, 3.0), (17.0, 3.0)],
            "day_sleeper": [(18.0, 4.0)],
        }

        if preferred_windows is None:
            if profile not in windows:
                raise ValueError(
                    f"Unknown profile_type {profile_type!r} for 'Public Place'; expected one of {list(windows)}"
                )
            preferred_windows = windows[profile]

        return self._apply_gap_activity(
            locations,
            hours,
            is_sleeping,
            "Public Place",
            preferred_windows,
            visit_probability,
            duration_mean,
            duration_std,
            max_visits_per_day,
            free_label,
            function_assignment_probability,
            cooldown_hours,
        )

    def apply_entertainment(
        self,
        locations,
        hours,
        is_sleeping,
        profile_type,
        visit_probability=0.20,
        duration_mean=2.0,
        duration_std=0.5,
        max_visits_per_day=1,
        preferred_windows=None,
        free_label="Home",
        function_assignment_probability=1.0,
        cooldown_hours=2.0,
    ):
        profile = self._normalize_profile(profile_type)

        windows = {
            "night_sleeper": [(18.0, 3.0)],
            "day_sleeper": [(20.0, 3.0)],
        }

        if preferred_windows is None:
            if profile not in windows:
                raise ValueError(
                    f"Unknown profile_type {profile_type!r} for 'Entertainment'; expected one of {list(windows)}"
                )
            preferred_windows = windows[profile]

        return self._apply_gap_activity(
            locations,
            hours,
            is_sleeping,
            "Entertainment",
            preferred_windows,
            visit_probability,
            duration_mean,
            duration_std,
            max_visits_per_day,
            free_label,
            function_assignment_probability,
            cooldown_hours,
        )

    def apply_hospital(
        self,
        locations,
        hours,
        is_sleeping,
        profile_type,
        visit_probability=0.08,
        duration_mean=1.5,
        duration_std=0.5,
        max_visits_per_day=1,
        preferred_windows=None,
        free_label="Home",
        function_assignment_probability=1.0,
        cooldown_hours=2.0,
    ):
        profile = self._normalize_profile(profile_type)

        windows = {
            "night_sleeper": [(10.0, 4.0), (15.0, 3.0)],
            "day_sleeper": [(18.0, 4.0)],
        }

        if preferred_windows is None:
            if profile not in windows:
                raise ValueError(
                    f"Unknown profile_type {profile_type!r} for 'Hospital'; expected one of {list(windows)}"
                )
            preferred_windows = windows[profile]

        return self._apply_gap_activity(
            locations,
            hours,
            is_sleeping,
            "Hospital",
            preferred_windows,
            visit_probability,
            duration_mean,
            duration_std,
            max_visits_per_day,
            free_label,
            function_assignment_probability,
            cooldown_hours,
        )
