# Time-of-day smoking profiles, relative to the user's own routine.
# Each profile gets two values per slot (see Sleeping_Profiles):
#   hours_awake  - hours since the user woke from their main sleep
#                  (NaN before the first wake-up in the series)
#   hours_to_bed - hours until the user's next main sleep starts
#                  (NaN while in main sleep; inf if the next bedtime is past
#                  the end of the series)
# Wake-anchored windows end where before_bed begins, so no two windows
# overlap, and no profile applies during main sleep or before the first
# wake-up (NaN comparisons are False). The same windows work for night and
# day sleepers.
#
# Each factory rolls once per user (like Location_Smoke_Risk): if the profile
# applies, its window gets `high` risk (default 0.25); otherwise it returns `low`
# (default 0.015) at all times.
# Example times in the docstrings are for someone who sleeps 23:00 - 07:00.

BEFORE_BED_HOURS = 3.0


def get_after_waking_smoker_profile(function_assignment_probability, rng, high=0.25, low=0.015):
    """
    Elevates smoking risk during the first hours after waking up
    (0 - 6 h after waking; 07:00 - 13:00).
    """
    rand_val = rng.random()
    is_applicable = rand_val < function_assignment_probability
    def apply_profile(hours_awake, hours_to_bed):
        if is_applicable and 0.0 <= hours_awake < 6.0 and hours_to_bed > BEFORE_BED_HOURS:
            return high
        return low
    return apply_profile


def get_midday_break_smoker_profile(function_assignment_probability, rng, high=0.25, low=0.015):
    """
    Elevates smoking risk during the midday break
    (6 - 7 h after waking; 13:00 - 14:00).
    """
    rand_val = rng.random()
    is_applicable = rand_val < function_assignment_probability
    def apply_profile(hours_awake, hours_to_bed):
        if is_applicable and 6.0 <= hours_awake < 7.0 and hours_to_bed > BEFORE_BED_HOURS:
            return high
        return low
    return apply_profile


def get_post_break_smoker_profile(function_assignment_probability, rng, high=0.25, low=0.015):
    """
    Elevates smoking risk after the midday break
    (7 - 10 h after waking; 14:00 - 17:00).
    """
    rand_val = rng.random()
    is_applicable = rand_val < function_assignment_probability
    def apply_profile(hours_awake, hours_to_bed):
        if is_applicable and 7.0 <= hours_awake < 10.0 and hours_to_bed > BEFORE_BED_HOURS:
            return high
        return low
    return apply_profile


def get_late_day_smoker_profile(function_assignment_probability, rng, high=0.25, low=0.015):
    """
    Elevates smoking risk late in the waking day
    (10 h after waking until before_bed starts; 17:00 - 20:00).
    """
    rand_val = rng.random()
    is_applicable = rand_val < function_assignment_probability
    def apply_profile(hours_awake, hours_to_bed):
        if is_applicable and 10.0 <= hours_awake and hours_to_bed > BEFORE_BED_HOURS:
            return high
        return low
    return apply_profile


def get_before_bed_smoker_profile(function_assignment_probability, rng, high=0.25, low=0.015):
    """
    Elevates smoking risk in the hours before the main sleep starts
    (last 3 h before bedtime; 20:00 - 23:00).
    """
    rand_val = rng.random()
    is_applicable = rand_val < function_assignment_probability
    def apply_profile(hours_awake, hours_to_bed):
        if is_applicable and hours_awake >= 0.0 and 0.0 < hours_to_bed <= BEFORE_BED_HOURS:
            return high
        return low
    return apply_profile


# Name (as used in personas.json "time_risk_probabilities") -> factory.
# Order matters: it's the order the per-user assignment rolls are drawn in.
TIME_RISK_PROFILES = {
    "after_waking": get_after_waking_smoker_profile,
    "midday_break": get_midday_break_smoker_profile,
    "post_break": get_post_break_smoker_profile,
    "late_day": get_late_day_smoker_profile,
    "before_bed": get_before_bed_smoker_profile,
}
