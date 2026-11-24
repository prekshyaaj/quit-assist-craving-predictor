import json

import numpy as np

# Every random part of the simulation gets its own independent stream.
# A component's position in this list is part of its seed, so only ever
# APPEND new components - reordering would change every stream.
COMPONENTS = [
    "persona",        # factory vs transportation choice for EMP_STAT 14
    "sleep",          # day/night sleeper choice + sleep session times
    "structured",     # work / school sessions
    "unstructured",   # gym, restaurant, market, entertainment, public place, hospital
    "commute",        # commute durations
    "location_risk",  # which location smoker profiles apply to the user
    "smoking",        # Bernoulli draws for "Did The User Smoke?"
    "hr_noise",       # smartwatch sensor noise on HR
    "time_risk",      # which time-of-day smoker profiles apply to the user
    "wake_buffer",    # how many slots (3-4) a user is woken early before leaving home
]


def load_seeds(filepath):
    """
    Reads the "seeds" block from a personas JSON file: "base" plus an
    optional seed per component (null = use base), and an optional "sample"
    seed for picking the users of a test run.
    """
    with open(filepath, "r") as f:
        seeds = json.load(f)["seeds"]
    unknown = set(seeds) - set(COMPONENTS) - {"base", "sample"}
    if unknown:
        raise ValueError(f"Unknown seed key(s) {sorted(unknown)} in {filepath}")
    return seeds


def make_task_rngs(seeds, task_id):
    """
    One independent np.random.Generator per component for one task. Each
    stream depends only on (component seed, task_id, component position), so
    results don't depend on which worker process runs the task.
    """
    rngs = {}
    for index, name in enumerate(COMPONENTS):
        seed = seeds.get(name)
        if seed is None:
            seed = seeds["base"]
        rngs[name] = np.random.default_rng([seed, int(task_id), index])
    return rngs
