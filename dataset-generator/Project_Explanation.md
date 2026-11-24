# Synthetic Generation Pipeline - Project Explanation

## Overall Idea
The `SyntheticGeneration` project is a sophisticated data generation pipeline that produces realistic, synthetic time-series datasets simulating individuals' daily routines over several days. The goal of the simulation is to model a person's behavior, sleep schedule, commuting habits, and discretionary activities, and ultimately simulate **smoking events** and corresponding **Heart Rate (HR)** measurements.

This allows for the creation of synthetic wearable sensor data (like smartwatch PPG sensors) alongside lifestyle markers for different demographics, which can be used to train or test machine learning models related to smoking detection, health monitoring, or behavioral analysis.

## How It Works
The pipeline operates in a chronological sequence of modular steps applied to each user (or "task") in the dataset:
1. **Time Expansion**: A base dataset containing individual user demographics is duplicated across time intervals (10-minute steps, 144 steps/day) to form a continuous timeline for several days.
2. **Persona Assignment**: Based on age and employment status (`EMP_STAT`), each user is assigned a persona (e.g., `office_worker`, `student`, `factory_worker`).
3. **Sleep Profiling**: Simulates sleep periods (night vs. day sleepers) using Gaussian distributions around expected sleep/wake times to avoid robotic, overly rigid schedules.
4. **Structured Activities**: Schedules primary, rigid activities (like work or school) into the day, respecting the generated sleep schedule.
5. **Unstructured Activities**: Probabilistically inserts discretionary activities (gym, restaurant, market, etc.) into any remaining free time slots ("Home") based on age, sex, and preferred time windows.
6. **Commute Calculation**: Automatically detects transitions between different locations and inserts realistic travel times (as "Public Place") between them using normal distributions.
7. **Smoking Simulation**: Computes a dynamic smoking risk based on the current location and time of day, and stochastically samples smoking events tailored to the user's reported daily cigarette intake.
8. **Physiological Simulation**: Calculates the "Time Since Last Smoked" (TSLS) and overlays various physiological effects to compute a simulated Heart Rate (HR). HR is dynamically affected by sleep, exercise, smoking cravings, and nicotine recovery, and it includes simulated smartwatch sensor noise based on activity levels.

---

## File Breakdown

### Entry Points
- [`generate_routine_general.py`](file:///c:/Users/kunke/Downloads/SyntheticGeneration2/SyntheticGeneration/generate_routine_general.py): The main execution script. It loads personas, expands the dataset, uses `concurrent.futures.ProcessPoolExecutor` for multiprocessing, and pipes the dataframe through all the simulated generators sequentially. Outputs `dataset_prepro_routine_generated.csv`.
- [`generate_routine_testing.py`](file:///c:/Users/kunke/Downloads/SyntheticGeneration2/SyntheticGeneration/generate_routine_testing.py): A variant of the main script primarily used for testing specific cases (e.g., using `Test_Case1.csv`). It contains minor overrides, like changing `DAYS_PER_TASK` to 4 for testing edge cases.

### Core Simulation Modules
- [`Time_Gen.py`](file:///c:/Users/kunke/Downloads/SyntheticGeneration2/SyntheticGeneration/Time_Gen.py): Bootstraps the time-series structure. It takes the base tabular data and expands it into 10-minute intervals (`time` column in hours) across multiple days, assigning a unique `task_id` to each individual.
- [`Sleeping_Profiles.py`](file:///c:/Users/kunke/Downloads/SyntheticGeneration2/SyntheticGeneration/Sleeping_Profiles.py): Contains the `SleepProfileGenerator`, which creates boolean masks indicating sleep based on night/day templates and normal distribution variances.
- [`Structured_Activities_Location_Profiles.py`](file:///c:/Users/kunke/Downloads/SyntheticGeneration2/SyntheticGeneration/Structured_Activities_Location_Profiles.py): Places structured events (like shifts at a Factory, Office, or School) onto the daily schedule, avoiding hours where the user is already marked as sleeping.
- [`UnStructured_Activities_Location_Profiles.py`](file:///c:/Users/kunke/Downloads/SyntheticGeneration2/SyntheticGeneration/UnStructured_Activities_Location_Profiles.py): Discretionary events (Gym, Market, Restaurant, Hospital, Entertainment, Public Place). Uses an intelligent gap-filling algorithm to find free chunks of time, scores them based on preferred time-of-day windows, and probabilistically inserts activities without overlapping existing schedules.
- [`Compute_Commute.py`](file:///c:/Users/kunke/Downloads/SyntheticGeneration2/SyntheticGeneration/Compute_Commute.py): The `CommuteTimeGenerator` detects location boundaries (e.g., `Home` -> `Office`) and replaces the first few steps of the destination block with a "Public Place" commute transit time, sampled from a normal distribution tailored to the specific origin-destination pair.

### Smoking and Physiology Models
- [`Location_Smoke_Risk.py`](file:///c:/Users/kunke/Downloads/SyntheticGeneration2/SyntheticGeneration/Location_Smoke_Risk.py): Provides probability weights (risk factors) for smoking based on the current location. For instance, being at a bar/restaurant or public place might increase the chance of smoking compared to being at school.
- [`Time_Smoke_Risk.py`](file:///c:/Users/kunke/Downloads/SyntheticGeneration2/SyntheticGeneration/Time_Smoke_Risk.py): Provides probability weights for smoking based on the time of day (e.g., morning routines or late-night cravings).
- [`Smoke_Simulation.py`](file:///c:/Users/kunke/Downloads/SyntheticGeneration2/SyntheticGeneration/Smoke_Simulation.py): Combines location and time risks, normalizes them against the individual's `CIGNOW_A` (reported cigarettes/day), applies a sleep mask (no smoking while asleep), and samples a binomial distribution to flag exact 10-minute intervals where a cigarette was smoked.
- [`TSLS_Calculator.py`](file:///c:/Users/kunke/Downloads/SyntheticGeneration2/SyntheticGeneration/TSLS_Calculator.py): Calculates "Time Since Last Smoked" (TSLS) in minutes by counting steps between smoking flags. Uses array doubling to create a cyclic wraparound, ensuring accurate calculations at the very beginning of the dataset.
- [`HR.py`](file:///c:/Users/kunke/Downloads/SyntheticGeneration2/SyntheticGeneration/HR.py): Heart Rate generation. Applies base deltas: gym (+40 bpm), sleep (-15 bpm), and a non-linear nicotine effect curve based on TSLS (including a pre-smoke craving spike and a post-smoke recovery decay). It also adds `sensor_noise_profile`, simulating the real-world artifacts of PPG smartwatch sensors (heavy noise during gym, minimal noise during sleep).

### Configuration and Rules
- [`personas.json`](file:///c:/Users/kunke/Downloads/SyntheticGeneration2/SyntheticGeneration/personas.json): JSON dictionary mapping abstract personas (`office_worker`, `factory_worker`, `retired`) to concrete Python methods (e.g., `apply_office_goer`), alongside base probabilities of attending work and the likelihood of them being a day-sleeper (working night shifts).
- [`probability_rules.py`](file:///c:/Users/kunke/Downloads/SyntheticGeneration2/SyntheticGeneration/probability_rules.py): Implements demographic modifiers for unstructured activities. Translates base visit probabilities through sex and age multipliers to tailor behavior (e.g., hospital visit likelihood based on age demographics).
- [`ResearchRules.txt`](file:///c:/Users/kunke/Downloads/SyntheticGeneration2/SyntheticGeneration/ResearchRules.txt): Internal documentation containing links to BLS (Bureau of Labor Statistics) and CDC (Centers for Disease Control) reports. It outlines the math and logic used to derive the probabilities in `personas.json` and `probability_rules.py`.
- [`dataset_info.md`](file:///c:/Users/kunke/Downloads/SyntheticGeneration2/SyntheticGeneration/dataset_info.md): A markdown dictionary explaining the valid location strings, categorical mappings, and data types (e.g., `EMP_STAT`, `CIGNOW_A`) expected and generated in the dataset.
