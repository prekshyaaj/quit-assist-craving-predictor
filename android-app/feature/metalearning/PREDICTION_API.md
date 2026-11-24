# Meta Learning demo page: how it talks to the prediction server

Server: `https://predict-server.proudcliff-0eaff5e4.centralindia.azurecontainerapps.io` (docs at `/docs`).
Code: `few_shot_meta_learning` repo, branch `MyEdits3`, `deploy/server.py` (push there → auto-deploys in ~5 min).
App config: `impl/src/main/java/.../PredictionConfig.kt`.

| Button | Endpoint | Login token | Body |
|---|---|---|---|
| Cold-start → Predict | `POST /predict_coldstart` | not needed | `{"current": {...}, "resting_hr": 65.2}` |
| Personalised → Adapt | `POST /adapt` | required | none: the server reads the user's day 1 from Supabase `sensor_data` |
| Personalised → Delete personal model | `DELETE /personal_model` | required | none: clears `users.personal_model` so Adapt can be shown again |
| Personalised → Predict | `POST /predict_adapted` | required | `{"current": {...}}` (+ `resting_hr` if known) |

`current` = `{time (hour 0-24), age, sex, cigs_per_day, emp_stat, is_sleeping, location, tsls, hr (bpm)}`.
Profile values come from the questionnaire; the rest from the "simulated sensors" card.
No watch → `hr` = resting HR. Asleep → `hr` = resting HR − 15.

Errors shown in the app: 401 → logs the user out (token expires after ~1 h); 404 on Predict → "Tap Adapt first";
422 → server's message.

## Setting up a demo account (once per account)
1. Sign up in the app and fill in the questionnaire (saved in `public.users`).
2. Put that user's rows in Supabase `public.sensor_data` (`user_id` = the account's id), at least 144 rows
   (one day, 10 minutes apart), ordered by `recorded_at`. Day 1 = the first 144 rows.
3. Settings → Meta Learning → tap **Adapt** once. The server reads day 1 from `sensor_data`, adapts,
   saves the model in `users.personal_model` and returns the user's resting HR.
4. Personalised **Predict** now uses the saved model (it stays saved across app restarts).
