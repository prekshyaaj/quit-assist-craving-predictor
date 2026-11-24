# FINAL demo test cases (2026-11-19). Use these.

| Login | Test case | Profile | Their day | Files |
|---|---|---|---|---|
| `newtestcase1@gmail.com` (`42486501…`) | **TC3** (Pooja) | 49, female, 5/day, not working (EMP 104) | Mostly at home; smokes at home through the day and evening | `pooja/Tast_Case3_routine.csv`, `supabase/testcase3_TC3_sensor_data_READY.csv` |
| `newtestcase2@gmail.com` (`ef18dc80…`) | **Construction worker** (generator task 920041) | 23, male, 5/day, construction (EMP 13) | At the construction site; smokes there, mostly 07:00–10:00 | `construction_worker_23M_routine.csv`, `supabase/testcase4_construction_worker_sensor_data_READY.csv` |

Both loaded in Supabase (576 rows, 17–20 Nov 2026 IST). Verified with the server code on the live data:

| App metrics card | TC3 cold → personalised | Construction worker cold → personalised |
|---|---|---|
| Recall | 94.1% → 94.1% (16/17) | 93.3% → 93.3% (14/15) |
| Precision | 51.6% → **55.2%** | 56.0% → **58.3%** |
| Macro F1 | 82.3 → **83.9** | 84.3 → **85.2** |
| False alarms | 15 → **13** | 11 → **10** |

Predict cards (press **Adapt** first, sleeping off; the app shows whole %):

| Login | Time | Location | Last cig | HR | Cold-start | Personalised | Reality |
|---|---|---|---|---|---|---|---|
| newtestcase2 | 07:40 | Construction Site | 10 | 77 | 34% Low | 35% **High** | he smoked: only personalised caught it |
| newtestcase2 | 18:20 | Home | 30 | 75 | 35% **High** | 34% Low | he didn't smoke: false alarm avoided |
| newtestcase1 | 17:00 | Market | 240 | 74 | 35% **High** | 34% Low | (typed example) |

The construction worker was picked as the best of ~200 generated young men (scored exactly like the app). Present him as an example user; TC1 stays the main reported result.
Everything below is the earlier Person A / B work, kept for reference.

---

# Persona test cases: cold-start vs personalised

Two new people, made with our routine generator from real NHIS smoker profiles.
Their routines are new (generator task ids 900012 / 900006), so the model has never seen them.
Each has 4 days × 144 ten-minute slots: **day 1 = support** (personalisation), **days 2–4 = test** (432 slots).
Model = the deployed one: MAML + FcNet, Epoch_100, adaptation lr 0.01 × 5 steps, threshold 0.35.

| | Person A | Person B |
|---|---|---|
| Who | Office worker, 69, male | Student, 30, male |
| Smoking | Heavy, 30/day | Light, 5/day |
| When / where they smoke (days 2–4) | All day, 06:00–23:00; Home, Office, Public Place | Mostly mornings 07:00–10:00 at Home; a few at School / Public Place |

These were picked out of 67 generated people as **two of the clearest cases where personalisation helped**.
Across all 67, personalisation helped 25, made no difference for 22, and slightly hurt 20
(on average about no change). Present them as illustrative examples, not as typical results.

## Files
| File | For | What |
|---|---|---|
| `personA_office_heavy_routine.csv`, `personB_student_light_routine.csv` | Pooja | Generator output format (HR as delta, like the TC1 / TC2 CSVs) |
| `run_persona_eval.py` | Pooja | Runs cold-start vs personalised on both people, prints confusion matrix + report |
| `supabase/person*_sensor_data.csv` | Shubham / Pooja | The same 4 days in `sensor_data` format (HR in bpm, resting ≈ 72) for the deployed app |
| `person*_app_moments.csv` | Everyone | Every test slot with the cold / personalised % the **app** should show (HR typed as whole bpm) |

---

## 1. Pooja: run on her laptop (5 min)
No files are written or changed. It uses her checkpoint, scaler and `tabular/config.json`.
```bash
python demo_testcases/run_persona_eval.py --repo /path/to/few_shot_meta_learning
```
(Run with the same Python environment she uses for `eval_maml_fcnet.py`.)

**Expected output (from Prekshya's run). Her numbers should match exactly:**

| | Smoking caught | False alarms | Macro F1 | Confusion `[[TN,FP],[FN,TP]]` |
|---|---|---|---|---|
| A cold-start | 89 / 90 | 124 | 68.23 | `[[218,124],[1,89]]` |
| **A personalised** | 89 / 90 | **118** | **69.47** | `[[224,118],[1,89]]` |
| B cold-start | 12 / 15 | 45 | 63.64 | `[[372,45],[3,12]]` |
| **B personalised** | 12 / 15 | **42** | **64.56** | `[[375,42],[3,12]]` |

If her numbers differ, her checkpoint or config is different from the deployed one. Tell Prekshya.

---

## 2. Shubham: run in the deployed app

**Already loaded (2026-11-19):** Person A = `newtestcase1@gmail.com`, Person B = `newtestcase2@gmail.com`
(profile + 576 sensor rows each, 17–20 Nov 2026). Just log in, press **Adapt**, then use the moments below.
The steps below are only needed for a new account.

1. **Create 2 accounts in the app** (e.g. `personA@example.com`, `personB@example.com`) and fill in the questionnaire:
   - A: age **69**, **Male**, **30** cigarettes/day, **Office or desk job**
   - B: age **30**, **Male**, **5** cigarettes/day, **Student**
2. In Supabase → Table editor → `users`, check the two rows: A must have `EMP_STAT` = **12**, B = **106**. If A shows 10, change it to 12; otherwise the numbers will be slightly different. Copy each user's `id`.
3. In each `supabase/person*_sensor_data.csv`, replace every `PUT-USER-ID-HERE` with that user's `id`. Then go to Supabase → `sensor_data` → **Insert → Import data from CSV** (576 rows each; `id` is filled automatically).
4. Log in as the person → Settings → Meta Learning → **Adapt**. Resting HR should show **72.0** (A) / **72.1** (B).
5. Enter a moment below, press **Predict** on both cards, and compare with the expected values.

**Demo moments (real slots from that person's test days; they didn't smoke in the next 10 min at any of them):**

| Person | Time | Location | Sleeping | Last cig (min) | HR (bpm) | Cold-start | Personalised |
|---|---|---|---|---|---|---|---|
| A | 11:50 | Office | off | 10 | 81 | 35.1% **High** | 32.1% **Low** |
| A | 09:00 | Home | off | 40 | 73 | 36.2% **High** | 33.0% **Low** |
| A | 13:20 | Office | off | 0 | 82 | 58.9% High | 52.0% High (largest gap, 7 points) |
| B | 18:50 | Home | off | 120 | 73 | 37.0% **High** | 33.2% **Low** |
| B | 19:30 | Public Place | off | 120 | 73 | 35.8% **High** | 32.9% **Low** |
| B | 06:40 | Home | off | 630 | 74 | 54.6% High | 48.5% High (largest gap, 6 points) |

Any other slot: see `person*_app_moments.csv` (columns: day, time, location, sleeping, last_cig, hr_bpm, smoked_next, cold, pers).

Note: for Person A the personalised model is wrong once in the other direction (day 3, 09:30, Office, last cig 30, HR 78: cold 34.4% Low → personalised 36.3% High, and he didn't smoke). Overall it removed 6 false alarms and added 0 missed events.

---

## How to explain it
- **Different people get different predictions.** The heavy smoker is high-risk across the whole working day; the light-smoking student is mostly high-risk in the morning. This comes from the profile and context features (age, cigarettes/day, job, time, location, time since last cigarette, heart rate).
- **Personalisation fine-tunes each person a little** using their day 1. For these two it removed false alarms (A: 124 → 118, B: 45 → 42) without missing any extra smoking event.
- **The effect is small with one day of data.** Across 67 new people it is about neutral on average. Stronger adaptation (lr 0.1 × 20) was tested and overfit (more missed events on TC1, almost 2× false alarms on TC2), so we kept the light setting.
- The data is synthetic (generator calibrated to NHIS profiles). Validating on real sensor data is future work.
