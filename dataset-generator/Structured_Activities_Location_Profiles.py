# pyrefly: ignore [missing-import]
import numpy as np
import pandas as pd


class LocationProfileGenerator_Structured:
    def __init__(self, seed: int = None):
        self.rng = np.random.default_rng(seed)

    @staticmethod
    def _normalize_profile(profile_type: str) -> str:
        return profile_type.lower().replace("_profile", "")

    def _pick_template(self, profile_type, templates, template, location_name):
        """An explicit template wins; otherwise the profile's template (unknown profiles raise)."""
        if template:
            return template
        profile = self._normalize_profile(profile_type)
        if profile not in templates:
            raise ValueError(
                f"Unknown profile_type {profile_type!r} for {location_name!r}; expected one of {list(templates)}"
            )
        return templates[profile]

    def _apply_location_sessions(
        self,
        locations: pd.Series,
        hours: pd.Series,
        is_sleeping: pd.Series,
        location_name: str,
        template: list,
        visit_probability: float,
        start_std: float,
        end_std: float,
    ) -> pd.Series:

        locations = locations.copy()  # don't modify the caller's Series
        hour_values = hours.to_numpy()

        # Convert repeated daily hours into absolute hours
        is_day_start = (np.arange(len(hour_values))
                        == 0) | (hour_values == 0.0)
        abs_hours = (np.cumsum(is_day_start) - 1) * 24 + hour_values

        def sample_schedule(day):
            sessions = []

            if self.rng.random() > visit_probability:
                return sessions

            for start_mean, end_mean in template:
                start = day * 24 + self.rng.normal(start_mean, start_std)
                end = day * 24 + self.rng.normal(end_mean, end_std)

                # Overnight session (decided by the template, not by the noisy
                # draws, so a short session can't turn into a 24-hour one)
                if end_mean < start_mean:
                    end += 24
                # Noise made this session end before it starts: skip it
                if end <= start:
                    continue

                sessions.append((start, end))

            return sessions

        num_days = int(abs_hours[-1] // 24) + 1
        all_sessions = []

        # Previous day is required for overnight sessions
        for day in range(-1, num_days):
            all_sessions.extend(sample_schedule(day))

        if is_sleeping is not None:
            is_sleeping_arr = is_sleeping.to_numpy()
        else:
            is_sleeping_arr = np.zeros(len(locations))

        for idx, abs_hour in enumerate(abs_hours):
            if is_sleeping_arr[idx] == 1:
                continue
            for start, end in all_sessions:
                if start <= abs_hour < end:
                    locations.iloc[idx] = location_name
                    break

        return locations

    def apply_office_goer(
        self,
        locations,
        hours,
        profile_type,
        is_sleeping=None,
        visit_probability=0.9,
        start_std=0.5,
        end_std=0.5,
        template=None,
    ):
        templates = {
            "night_sleeper": [(9.0, 17.0)],
            "day_sleeper": [(15.0, 23.0)],
        }

        template = self._pick_template(profile_type, templates, template, "Office")

        return self._apply_location_sessions(
            locations,
            hours,
            is_sleeping,
            "Office",
            template,
            visit_probability,
            start_std,
            end_std,
        )

    def apply_construction_site_goer(
        self,
        locations,
        hours,
        profile_type,
        is_sleeping=None,
        visit_probability=0.85,
        start_std=0.6,
        end_std=0.6,
        template=None,
    ):
        templates = {
            "night_sleeper": [(7.0, 16.0)],
            "day_sleeper": [(16.0, 1.0)],
        }

        template = self._pick_template(profile_type, templates, template, "Construction Site")

        return self._apply_location_sessions(
            locations,
            hours,
            is_sleeping,
            "Construction Site",
            template,
            visit_probability,
            start_std,
            end_std,
        )

    def apply_factory_goer(
        self,
        locations,
        hours,
        profile_type,
        is_sleeping=None,
        visit_probability=0.85,
        start_std=0.5,
        end_std=0.5,
        template=None,
    ):
        templates = {
            "night_sleeper": [(9.0, 17.5)],
            "day_sleeper": [(19.0, 3.5)],
        }

        template = self._pick_template(profile_type, templates, template, "Factory")

        return self._apply_location_sessions(
            locations,
            hours,
            is_sleeping,
            "Factory",
            template,
            visit_probability,
            start_std,
            end_std,
        )

    def apply_school_goer(
        self,
        locations,
        hours,
        profile_type,
        is_sleeping=None,
        visit_probability=0.85,
        start_std=0.4,
        end_std=0.4,
        template=None,
    ):
        templates = {
            "night_sleeper": [(10.5, 16.5)],
            "day_sleeper": [(18.5, 0.5)],
        }

        template = self._pick_template(profile_type, templates, template, "School")

        return self._apply_location_sessions(
            locations,
            hours,
            is_sleeping,
            "School",
            template,
            visit_probability,
            start_std,
            end_std,
        )

    def apply_transportation_goer(
        self,
        locations,
        hours,
        profile_type,
        is_sleeping=None,
        visit_probability=0.9,
        start_std=0.5,
        end_std=0.5,
        template=None,
    ):
        templates = {
            "night_sleeper": [(7.5, 15.5)],
            "day_sleeper": [(18.0, 2.0)],
        }

        template = self._pick_template(profile_type, templates, template, "Public Place")

        return self._apply_location_sessions(
            locations,
            hours,
            is_sleeping,
            "Public Place",
            template,
            visit_probability,
            start_std,
            end_std,
        )

    def finalize_locations(self, locations: pd.Series) -> pd.Series:
        return locations.fillna("Home")
