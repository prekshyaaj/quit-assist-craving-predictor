# Dataset Information (Derived from Source Code)

## Location Names
By analyzing the core generation logic (`Structured_Activities_Location_Profiles.py` and `UnStructured_Activities_Location_Profiles.py`), the simulation actively assigns the following locations:

**Structured Activities (Primary Locations):**
- Home (Default/Baseline location)
- Office
- Factory
- Construction Site
- School

**Unstructured Activities (Discretionary/Gap-filling):**
- Gym
- Restaurant
- Market
- Public Place (Also used as the transitional location during commuting)
- Entertainment
- Hospital

*(Note: `Compute_Commute.py` contains routing statistics for `Shop` and `Shopping Mall`, but they are not currently assigned as active destinations by the primary generators).*

## Column Names and Valid Value Types
| Column Name | Data Type | Description / Valid Values |
| :--- | :--- | :--- |
| `task_id` | Integer | Unique identifier for each simulated task/user (starts at 1). Generated in `Time_Gen.py`. |
| `time` | Float | Represents the time of day in hours. Generated in `Time_Gen.py` (See detailed definition below). |
| `AGEP_A` | Integer / Float | Age of the individual, sourced from the base input data. |
| `SEX_A` | Integer / Float | Categorical variable representing sex, sourced from the base input data. |
| `CIGNOW_A` | Float | Number of cigarettes smoked per day. Used to scale probabilities in `Smoke_Simulation.py`. |
| `EMP_STAT` | Integer / Float | Employment status code. Used in `assign_persona()` to determine user behaviors (e.g., Office Worker, Student). |
| `is_sleeping` | Integer (Binary) | Indicates if the user is sleeping (`0` = Awake, `1` = Sleeping), computed by `SleepProfileGenerator`. |
| `Location` | String | Categorical variable for the current location (e.g., `Home`, `Office`, `Public Place`). |
| `location_smoke_risk` | Float | Risk factor associated with the current location (e.g., `0.500` or `0.05`). Calculated by taking the maximum risk across all location profiles. |
| `time_smoke_risk` | Float | Risk factor associated with the current time of day, calculated from time-based smoker profiles. |
| `Did The User Smoke?` | Integer (Binary) | Indicates if a smoking event occurred (`0` = No, `1` = Yes). Sampled via binomial distribution in `Smoke_Simulation.py`. |
| `TSLS` | Float | Time Since Last Smoked in minutes. |
| `HR` | Float | Heart Rate variable computed by adding base delta, gym delta, sleep delta, and sensor noise. |

## Time Column Definition
According to `Time_Gen.py`, the `time` column is a **floating-point number representing the time of day in hours**, computed as `(i * 10 / 60) % 24.0` where `i` is the simulation step.
- **Interval/Step Size:** Each step represents exactly **10 minutes** ($10/60$ hours, which is `0.166666...`).
- **Range:** The values start at `0.0` (Midnight / 00:00) and increase to `23.83333333333333` (11:50 PM / 23:50).
- **Cycle:** The modulo operator (`% 24.0`) ensures that after exactly 144 steps (24 hours), the time resets to `0.0` for the next simulated day.
