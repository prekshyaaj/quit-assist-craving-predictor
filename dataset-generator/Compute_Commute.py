# pyrefly: ignore [missing-import]
import numpy as np
import pandas as pd

from Time_Gen import STEP_MINUTES

class CommuteTimeGenerator:
    """
    Samples commute durations (in minutes) between location pairs using a
    Normal(mean, std) per pair. Times are one-way; A->B and B->A are treated
    as identical, so the dictionary only needs one direction per pair.
    """

    # (location_a, location_b) -> (mean_minutes, std_minutes)
    COMMUTE_STATS = {
        ("Home", "Office"): (35.0, 12.0),
        ("Home", "Construction Site"): (40.0, 15.0),
        ("Home", "Factory"): (35.0, 12.0),
        ("Home", "School"): (20.0, 8.0),
        ("Home", "Gym"): (12.0, 5.0),
        ("Home", "Restaurant"): (15.0, 6.0),
        ("Home", "Public Place"): (15.0, 7.0),
        ("Home", "Hospital"): (20.0, 8.0),
        ("Home", "Market"): (12.0, 5.0),

        ("Office", "Construction Site"): (30.0, 10.0),
        ("Office", "Factory"): (25.0, 10.0),
        ("Office", "School"): (25.0, 10.0),
        ("Office", "Gym"): (15.0, 6.0),
        ("Office", "Restaurant"): (10.0, 4.0),
        ("Office", "Public Place"): (15.0, 6.0),
        ("Office", "Hospital"): (20.0, 8.0),
        ("Office", "Market"): (15.0, 6.0),

        ("Construction Site", "Factory"): (25.0, 10.0),
        ("Construction Site", "School"): (30.0, 10.0),
        ("Construction Site", "Gym"): (20.0, 8.0),
        ("Construction Site", "Restaurant"): (15.0, 6.0),
        ("Construction Site", "Public Place"): (20.0, 8.0),
        ("Construction Site", "Hospital"): (25.0, 10.0),
        ("Construction Site", "Market"): (18.0, 7.0),

        ("Factory", "School"): (25.0, 10.0),
        ("Factory", "Gym"): (15.0, 6.0),
        ("Factory", "Restaurant"): (12.0, 5.0),
        ("Factory", "Public Place"): (15.0, 6.0),
        ("Factory", "Hospital"): (20.0, 8.0),
        ("Factory", "Market"): (15.0, 6.0),

        ("School", "Gym"): (12.0, 5.0),
        ("School", "Restaurant"): (10.0, 4.0),
        ("School", "Public Place"): (12.0, 5.0),
        ("School", "Hospital"): (18.0, 7.0),
        ("School", "Market"): (12.0, 5.0),

        ("Gym", "Restaurant"): (8.0, 3.0),
        ("Gym", "Public Place"): (10.0, 4.0),
        ("Gym", "Hospital"): (15.0, 6.0),
        ("Gym", "Market"): (10.0, 4.0),

        ("Restaurant", "Public Place"): (8.0, 3.0),
        ("Restaurant", "Hospital"): (12.0, 5.0),
        ("Restaurant", "Market"): (8.0, 3.0),

        ("Public Place", "Hospital"): (12.0, 5.0),
        ("Public Place", "Market"): (8.0, 3.0),

        ("Hospital", "Market"): (12.0, 5.0),

        # "Entertainment" unstructured activity
        ("Home", "Entertainment"): (20.0, 8.0),
        ("Office", "Entertainment"): (15.0, 6.0),
        ("Construction Site", "Entertainment"): (20.0, 8.0),
        ("Factory", "Entertainment"): (18.0, 7.0),
        ("School", "Entertainment"): (15.0, 6.0),
        ("Gym", "Entertainment"): (12.0, 5.0),
        ("Restaurant", "Entertainment"): (10.0, 4.0),
        ("Public Place", "Entertainment"): (12.0, 5.0),
        ("Hospital", "Entertainment"): (15.0, 6.0),
        ("Market", "Entertainment"): (10.0, 4.0),
    }

    MIN_COMMUTE_MINUTES = 5.0   # every commute is at least 1 step (see apply_commute_transitions)

    def __init__(self, seed: int = None):
        self.rng = np.random.default_rng(seed)

    def _lookup_stats(self, loc_a: str, loc_b: str) -> tuple:
        if loc_a == loc_b:
            return (0.0, 0.0)
        stats = self.COMMUTE_STATS.get(
            (loc_a, loc_b), self.COMMUTE_STATS.get((loc_b, loc_a)))
        if stats is None:
            raise KeyError(
                f"No commute stats for {loc_a!r} <-> {loc_b!r}; add the pair to COMMUTE_STATS")
        return stats

    def sample_commute_minutes(self, loc_a: str, loc_b: str) -> float:
        mean, std = self._lookup_stats(loc_a, loc_b)
        if mean == 0.0 and std == 0.0:
            return 0.0
        return float(max(self.MIN_COMMUTE_MINUTES, self.rng.normal(mean, std)))

    def apply_commute_transitions(
        self,
        locations: pd.Series,
        step_minutes: float = STEP_MINUTES,
    ) -> pd.Series:
        """
        Call AFTER finalize_locations (no NaNs). Run-length encodes the
        location series; for every block-to-block transition where the
        location changes, samples a commute duration for that pair and marks
        that many steps as "Public Place": half (rounded up) at the end of
        the previous block, the rest at the start of the next block. Each
        half is capped at its block's length. Sleep is not skipped: slots of
        the trip that fall in sleep are later marked awake by the caller
        (nobody sleeps away from Home).
        """
        loc_values = locations.to_numpy(dtype=object)
        n = len(loc_values)

        boundaries = [0] + [i for i in range(1, n)
                            if loc_values[i] != loc_values[i - 1]] + [n]
        blocks = [(boundaries[i], boundaries[i + 1], loc_values[boundaries[i]])
                  for i in range(len(boundaries) - 1)]

        result = loc_values.copy()

        for (prev_start, prev_end, prev_loc), (next_start, next_end, next_loc) in zip(blocks, blocks[1:]):
            if next_loc == prev_loc:
                continue

            commute_minutes = self.sample_commute_minutes(prev_loc, next_loc)
            # Round half up (Python's round() rounds .5 to even, e.g. round(2.5) == 2),
            # and make every real commute at least 1 step
            raw_steps = int(commute_minutes / step_minutes + 0.5)
            if commute_minutes > 0 and raw_steps == 0:
                raw_steps = 1
                
            # Split the trip: ceil(half) leaves from the end of the previous
            # location, the rest arrives at the start of the next one
            prev_steps = min((raw_steps + 1) // 2, prev_end - prev_start)
            next_steps = min(raw_steps - (raw_steps + 1) // 2, next_end - next_start)
            result[prev_end - prev_steps:prev_end] = "Public Place"
            result[next_start:next_start + next_steps] = "Public Place"

        return pd.Series(result, index=locations.index)
