import pandas as pd
import json
import concurrent.futures
from pathlib import Path

from Time_Gen import expand_dataset
from Sleeping_Profiles import SleepProfileGenerator, add_wake_up_buffer
from Smoke_Simulation import simulate_smoking
from Structured_Activities_Location_Profiles import LocationProfileGenerator_Structured
from UnStructured_Activities_Location_Profiles import LocationProfileGenerator_Unstructured
from Compute_Commute import CommuteTimeGenerator
from Location_Smoke_Risk import (
    get_home_smoker_profile, get_office_smoker_profile, get_construction_site_smoker_profile,
    get_restaurant_smoker_profile, get_hospital_smoker_profile, get_public_place_smoker_profile,
    get_school_smoker_profile, get_gym_smoker_profile, get_market_smoker_profile, get_entertainment_smoker_profile
)
from Time_Smoke_Risk import TIME_RISK_PROFILES
from TSLS_Calculator import calculate_tsls
from HR import gym_hr_profile, sleep_hr_profile, smoke_hr_profile, sensor_noise_profile

from probability_rules import calculate_user_probabilities
from Seed_Config import load_seeds, make_task_rngs

# Input/output files live next to this script, whatever the working directory
BASE_DIR = Path(__file__).resolve().parent
PERSONAS_PATH = BASE_DIR / "personas_general.json"   # settings for the full run


def load_personas(filepath=PERSONAS_PATH):
    with open(filepath, "r") as f:
        personas = json.load(f)
    personas.pop("seeds", None)  # seed settings live in the same file
    personas.pop("time_risk_probabilities", None)
    personas.pop("generation", None)
    return personas


def load_time_risk_probabilities(filepath=PERSONAS_PATH):
    with open(filepath, "r") as f:
        probs = json.load(f).get("time_risk_probabilities", {})
    invalid = [
        name for name in TIME_RISK_PROFILES
        if isinstance(probs.get(name), bool)
        or not isinstance(probs.get(name), (int, float))
        or not 0.0 <= probs[name] <= 1.0
    ]
    if invalid:
        raise ValueError(
            f"Set a probability between 0 and 1 for {invalid} in "
            f"'time_risk_probabilities' in {filepath}")
    return probs


def load_generation(filepath=PERSONAS_PATH):
    """
    The "generation" block of personas.json: run settings, sleep profiles,
    unstructured activities, location smoke risk, commute and wake-up buffer.
    """
    with open(filepath, "r") as f:
        return json.load(f)["generation"]


def activity_settings(generation, name, sleep_profile_type):
    """Keyword arguments for one unstructured apply_* method, from the JSON."""
    activity = generation["activities"][name]
    return {
        "visit_probability": activity["visit_probability"],
        "duration_mean": activity["duration_mean"],
        "duration_std": activity["duration_std"],
        "max_visits_per_day": activity["max_visits_per_day"],
        "cooldown_hours": activity["cooldown_hours"],
        "preferred_windows": activity["preferred_windows"][sleep_profile_type],
    }


def smoke_risk_settings(generation, persona_config):
    """
    Location, time and TSLS risk levels for one persona: the defaults in
    generation["smoke_risk"], with any values the persona's own "smoke_risk"
    block overrides (e.g. {"tsls": {"max_minutes": 150}}).
    """
    overrides = persona_config.get("smoke_risk", {})
    return {
        part: {**defaults, **overrides.get(part, {})}
        for part, defaults in generation["smoke_risk"].items()
    }


def assign_persona(age, emp_stat, personas, rng):
    """
    Assigns a persona based on basic heuristics and EMP_STAT codes.
    """
    # Offset mappings (101.0 - 108.0)
    reason_map_offset = {
        101.0: "unemployed",
        102.0: "seasonal",
        103.0: "retired",
        104.0: "disabled",
        105.0: "family_care",
        106.0: "student",
        107.0: "unpaid_family",
        108.0: "other"
    }

    if emp_stat in reason_map_offset:
        persona_name = reason_map_offset[emp_stat]
        # Use the persona if personas.json has it; otherwise the home-based
        # "retired" configuration (no structured location)
        return persona_name if persona_name in personas else "retired"

    # EMP_STAT Mappings based on Occupation_Category (10 - 14)
    if emp_stat in [10, 12]:
        return "office_worker"
    elif emp_stat == 11:
        return "service_worker"
    elif emp_stat == 13:
        return "construction_worker"
    elif emp_stat == 14:
        # 14 covers both Production (Factory) and Transportation
        options = ["factory_worker", "transportation_worker"]
        return options[rng.integers(len(options))]
    else:
        # Fallback
        if age < 25:
            return "student"
        else:
            return "retired"


def process_user(args):
    task_df, personas, seeds, time_risk_probabilities, generation = args
    task_df = task_df.copy()
    task_id = task_df["task_id"].iloc[0]

    # Initialize generators, each with its own independent random stream
    rngs = make_task_rngs(seeds, task_id)
    sleep_gen = SleepProfileGenerator(seed=rngs["sleep"])
    struct_gen = LocationProfileGenerator_Structured(seed=rngs["structured"])
    unstruct_gen = LocationProfileGenerator_Unstructured(seed=rngs["unstructured"])
    commute_gen = CommuteTimeGenerator(seed=rngs["commute"])
    commute_gen.MIN_COMMUTE_MINUTES = generation["commute"]["min_minutes"]

    # Extract demographic info
    age = task_df["AGEP_A"].iloc[0]
    sex = task_df["SEX_A"].iloc[0]
    emp_stat = task_df["EMP_STAT"].iloc[0]

    persona_key = assign_persona(age, emp_stat, personas, rngs["persona"])
    persona_config = personas[persona_key]

    hours = task_df["time"].copy()
    locations = task_df["Location"].copy()

    # Calc probabilities
    user_probs = calculate_user_probabilities(age, sex)

    # 1. Sleeping profile: main sleep plus optional extra sessions (naps),
    #    all from generation["sleep_profiles"]
    is_day_sleeper = rngs["sleep"].random() < persona_config["day_sleeper_prob"]

    if is_day_sleeper:
        sleep_profile_type = "day_sleeper"
        sleep_profile = sleep_gen.day_sleeper_profile
    else:
        sleep_profile_type = "night_sleeper"
        sleep_profile = sleep_gen.night_sleeper_profile

    sleep_settings = generation["sleep_profiles"][sleep_profile_type]
    is_sleeping = sleep_profile(
        hours,
        start_std=sleep_settings["start_std"],
        end_std=sleep_settings["end_std"],
        template=sleep_settings["template"],
        non_main_assignment_probability=sleep_settings["non_main_assignment_probability"],
        non_main_visit_probability=sleep_settings["non_main_visit_probability"],
        min_non_main_minutes=sleep_settings["min_non_main_minutes"],
    )

    task_df["is_sleeping"] = is_sleeping
    # Main sleep session (first in the sleep template) decides where each day starts
    is_main_sleep = sleep_gen.is_main_sleep
    unstruct_gen.is_main_sleep = is_main_sleep

    # 2. Structured activities, with templates per sleep type from the persona.
    #    Work/school only gives way to the main sleep: a nap that falls in a
    #    shift is dropped in step 4.1 (nobody sleeps away from Home)
    struct_method_name = persona_config["apply_method"]
    if struct_method_name:
        struct_method = getattr(struct_gen, struct_method_name)
        structured = persona_config["structured"]
        locations = struct_method(
            locations, hours, profile_type=sleep_profile_type,
            is_sleeping=is_main_sleep,
            visit_probability=persona_config["visit_prob"],
            start_std=structured["start_std"],
            end_std=structured["end_std"],
            template=structured["templates"][sleep_profile_type],
        )

    # 3. Unstructured activities (settings from generation["activities"],
    #    who does them from calculate_user_probabilities)
    locations = unstruct_gen.apply_gym(
        locations, hours, is_sleeping, profile_type=sleep_profile_type,
        function_assignment_probability=user_probs["gym"],
        **activity_settings(generation, "gym", sleep_profile_type)
    )
    locations = unstruct_gen.apply_restaurant(
        locations, hours, is_sleeping, profile_type=sleep_profile_type,
        function_assignment_probability=user_probs["restaurant"],
        **activity_settings(generation, "restaurant", sleep_profile_type)
    )
    locations = unstruct_gen.apply_market(
        locations, hours, is_sleeping, profile_type=sleep_profile_type,
        function_assignment_probability=user_probs["market"],
        **activity_settings(generation, "market", sleep_profile_type)
    )
    locations = unstruct_gen.apply_entertainment(
        locations, hours, is_sleeping, profile_type=sleep_profile_type,
        function_assignment_probability=user_probs["entertainment"],
        **activity_settings(generation, "entertainment", sleep_profile_type)
    )
    locations = unstruct_gen.apply_public_place(
        locations, hours, is_sleeping, profile_type=sleep_profile_type,
        function_assignment_probability=user_probs["public_place"],
        **activity_settings(generation, "public_place", sleep_profile_type)
    )
    locations = unstruct_gen.apply_hospital(
        locations, hours, is_sleeping, profile_type=sleep_profile_type,
        function_assignment_probability=user_probs["hospital"],
        **activity_settings(generation, "hospital", sleep_profile_type)
    )

    # Any location still unset is Home (must happen before the commute step)
    locations = struct_gen.finalize_locations(locations)
    task_df["Location"] = locations

    # 4. Compute Commute transitions
    locations = commute_gen.apply_commute_transitions(locations)
    task_df["Location"] = locations

    # 4.1 Nobody sleeps away from Home: clear sleep (and main sleep) there
    away_from_home = locations != "Home"
    task_df.loc[away_from_home, "is_sleeping"] = 0
    is_main_sleep = is_main_sleep.mask(away_from_home, 0)

    # 4.2 Nobody goes straight from sleep to somewhere other than Home: wake
    # them a few slots earlier so they spend that time at home first
    wake_up_buffer = generation["wake_up_buffer"]
    is_sleeping, is_main_sleep = add_wake_up_buffer(
        locations, task_df["is_sleeping"], is_main_sleep, rngs["wake_buffer"],
        min_slots=wake_up_buffer["min_slots"], max_slots=wake_up_buffer["max_slots"])
    task_df["is_sleeping"] = is_sleeping

    # 4.5 Location Smoke Risk (who is a smoker at each location, from the JSON;
    #     risk levels from generation["smoke_risk"] + this persona's overrides)
    location_risk = generation["location_risk_probabilities"]
    risk_levels = smoke_risk_settings(generation, persona_config)
    location_profiles = [
        get_home_smoker_profile(location_risk["home"], rng=rngs["location_risk"], **risk_levels["location"]),
        get_office_smoker_profile(location_risk["office"], rng=rngs["location_risk"], **risk_levels["location"]),
        get_restaurant_smoker_profile(location_risk["restaurant"], rng=rngs["location_risk"], **risk_levels["location"]),
        get_construction_site_smoker_profile(location_risk["construction_site"], rng=rngs["location_risk"], **risk_levels["location"]),
        get_hospital_smoker_profile(location_risk["hospital"], rng=rngs["location_risk"], **risk_levels["location"]),
        get_public_place_smoker_profile(location_risk["public_place"], rng=rngs["location_risk"], **risk_levels["location"]),
        get_school_smoker_profile(location_risk["school"], rng=rngs["location_risk"], **risk_levels["location"]),
        get_gym_smoker_profile(location_risk["gym"], rng=rngs["location_risk"], **risk_levels["location"]),
        get_market_smoker_profile(location_risk["market"], rng=rngs["location_risk"], **risk_levels["location"]),
        get_entertainment_smoker_profile(location_risk["entertainment"], rng=rngs["location_risk"], **risk_levels["location"])
    ]

    risk_series_list = [prof(locations) for prof in location_profiles]
    task_df["location_smoke_risk"] = pd.concat(risk_series_list, axis=1).max(axis=1)

    # 5. Time Smoke Risk & Simulate Smoking
    time_profiles = [
        get_profile(time_risk_probabilities[name], rng=rngs["time_risk"], **risk_levels["time"])
        for name, get_profile in TIME_RISK_PROFILES.items()
    ]

    task_df = simulate_smoking(task_df, time_profiles, rng=rngs["smoking"],
                               is_main_sleep=is_main_sleep,
                               tsls_params=risk_levels["tsls"])

    # 8. Calculate Time Since Last Smoked (TSLS) in minutes
    smoke_series = task_df["Did The User Smoke?"]
    task_df["TSLS"] = calculate_tsls(smoke_series)

    # 9. Calculate Heart Rate (HR)
    tsls_series = task_df["TSLS"]
    location_series = task_df["Location"]
    sleep_series = task_df["is_sleeping"]

    base_hr_delta = smoke_hr_profile(tsls_series, smoke_series)
    gym_delta = location_series.apply(gym_hr_profile)
    sleep_delta = sleep_series.apply(sleep_hr_profile)

    task_df["HR"] = base_hr_delta + gym_delta + sleep_delta

    # 10. Apply Sensor Noise
    task_df["HR"] += sensor_noise_profile(task_df["Location"], task_df["is_sleeping"], rng=rngs["hr_noise"])

    return task_df

def main():
    personas = load_personas()
    seeds = load_seeds(PERSONAS_PATH)
    time_risk_probabilities = load_time_risk_probabilities()
    generation = load_generation()
    run = generation["full_simulation"]
    print(f"Seeds: {seeds}")

    # 1. Expand dataset (returns df with time and task_id): every user in the
    #    input file, for run["days_per_task"] days
    df = expand_dataset(BASE_DIR / run["input_csv"], output_path=None,
                        days_per_task=run["days_per_task"], verbose=run["verbose"])

    # Initialize columns
    df["is_sleeping"] = 0
    df["Location"] = "Home"

    # Split df by task_id
    grouped = df.groupby("task_id")
    tasks = [(group, personas, seeds, time_risk_probabilities, generation)
             for name, group in grouped]

    print(f"Processing {len(tasks)} tasks using multiprocessing...")
    results = []
    
    with concurrent.futures.ProcessPoolExecutor() as executor:
        results = list(executor.map(process_user, tasks))

    # Recombine
    df = pd.concat(results, ignore_index=True)

    # Save the generated routine (time rounded for readability only)
    df["time"] = df["time"].round(4)
    output_path = BASE_DIR / run["output_csv"]
    df.to_csv(output_path, index=False)
    print(f"Generated routine saved to {output_path}")

if __name__ == "__main__":
    main()
