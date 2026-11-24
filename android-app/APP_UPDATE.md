# App Update: Chatbot and Voice Integration

Branch: `voice-server` · Commits:

- `274db4a` "Add chatbot server with Supabase sign-in"
- `5217ad9` "Add chatbot .env for the team (private repo)"
- `13b5651` "Chatbot And voice Support added"
- `eabd148` "Minor updates"

This update adds a new backend, the **chatbot server** (Python, in `core/chatbot/`). It also connects both backends, the new chatbot server and the existing voice server, to the Android app. After it:

- The **Chatbot** tab in the bottom navigation opens a working coach chat, with saved history.
- **Settings → Craving Voice Detection** opens the voice screen, where the user can turn background listening on and off.
- The header, navigation and back stack now live in one place (`MainActivity`) instead of being copied into each screen.

It also records how the voice craving threshold (`0.47`) was chosen, and how to reproduce that result.

---

## 1. Module layout after this update

```
:app                         shell: MainActivity, NavigationItems, back stack
 ├─ :feature:home:api/impl   HomeNavKey + homeEntry()                 (new nav key/entry)
 ├─ :feature:tools:api/impl  ToolsNavKey + toolsEntry()               (new nav key/entry)
 ├─ :feature:chatbot:api     ChatbotNavKey, ChatbotApi (HTTP client)   NEW
 ├─ :feature:chatbot:impl    chatbotEntry(), ChatbotScreen             NEW
 ├─ :feature:settings:api    SettingsNavKey                            NEW
 ├─ :feature:settings:impl   settingsEntry(), SettingsScreen           NEW
 ├─ :core:voice              VoiceNavKey, voiceEntry(), VoiceScreen,
 │                           VoiceListeningService, VoiceApi, ...      (nav key/entry added)
 └─ :core:designsystem       HeaderTopBar (now with account menu),
                             AppNavigationSuiteScaffold (now takes layoutType)

core/chatbot/                Python chatbot server (FastAPI + Supabase + Gemini)   NEW
core/voice/src/main/python/  Python voice server (FastAPI + VAD + Whisper + MiniLM)
```

The two Python folders are not Gradle modules. They are separate servers that the app reaches over HTTP.

Every screen now follows the same pattern:

1. An `api` module holds a `@Serializable object XxxNavKey : NavKey`.
2. An `impl` module holds an `EntryProviderScope<NavKey>.xxxEntry(...)` extension that maps the key to its screen.
3. `MainActivity` calls every `xxxEntry()` inside a single Navigation 3 `entryProvider { }` and owns the one back stack.

New modules are registered in [settings.gradle.kts](settings.gradle.kts) and added as dependencies in [app/build.gradle.kts](app/build.gradle.kts). The modules that declare nav keys (`home:api`, `chatbot`, `settings`, `core:voice`) now apply the `kotlin.serialization` plugin and depend on `navigation3.runtime`.

### Shell changes in `:app`

- [MainActivity.kt](app/src/main/java/com/example/smokeapp/MainActivity.kt)
  - Builds the `entryProvider` with `homeEntry()`, `toolsEntry()`, `chatbotEntry()`, `settingsEntry(...)` and `voiceEntry(...)`.
  - Shows `HeaderTopBar` once, in the `Scaffold` `topBar`. Home and Tools no longer draw their own header or status-bar spacer.
  - Hides the navigation bar (`NavigationSuiteType.None`) when the keyboard is open, or when the current key is in `DROP_DOWN_NAV_ITEMS`.
  - Calls `VoiceListeningService.resumeIfEnabled(this)` in `onResume()` (see §3.3).
- [NavigationItems.kt](app/src/main/java/com/example/smokeapp/NavigationItems.kt)
  - Adds a `CHATBOT` tab (smart-toy icon) to `NAV_ITEMS`.
  - Adds `DROP_DOWN_NAV_ITEMS`: Profile, Settings, Feedback, About, Help and `VoiceNavKey`. These are full-screen secondary pages without the bottom bar.
- [AndroidManifest.xml](app/src/main/AndroidManifest.xml)
  - Adds `INTERNET`, `usesCleartextTraffic="true"`, `networkSecurityConfig="@xml/voice_network_security_config"` and `windowSoftInputMode="adjustResize"`, so the chat input stays above the keyboard.
- [HeaderTopBar.kt](core/designsystem/src/main/java/com/example/smokeapp/core/designsystem/HeaderTopBar.kt)
  - The profile icon now opens a dropdown with Profile, Settings, Send Feedback, About and Help. Each item has its own callback.

---

## 2. Chatbot integration

### 2.1 Android side

**`:feature:chatbot:api`**: [ChatbotApi.kt](feature/chatbot/api/src/main/java/com/example/feature/chatbot/api/ChatbotApi.kt)

A small `HttpURLConnection` client. Every call is a `suspend` function that runs on `Dispatchers.IO`.

| Function | Server endpoint |
|---|---|
| `health()` | `GET /health` |
| `sendMessage(token, message, sessionId?)` | `POST /chat` |
| `getSessions(token)` | `GET /sessions` |
| `getMessages(token, sessionId)` | `GET /sessions/{id}/messages` |
| `endSession(token, sessionId)` | `POST /sessions/{id}/end` |
| `deleteSession(token, sessionId)` | `DELETE /sessions/{id}` |

- Each request sends `Authorization: Bearer <token>`.
- Any non-2xx response is turned into a `ChatbotApiException(statusCode, detail)`. `detail` is read from FastAPI's `{"detail": ...}` body, so the UI can show the server's own message.
- `getBaseUrl()` checks whether the app is running on an emulator:
  - emulator → `http://10.0.2.2:8050`
  - real phone → `http://192.168.1.3:8050`

**`:feature:chatbot:impl`**: [ChatbotEntry.kt](feature/chatbot/impl/src/main/java/com/example/feature/chatbot/impl/ChatbotEntry.kt), [ChatbotScreen.kt](feature/chatbot/impl/src/main/java/com/example/feature/chatbot/impl/ChatbotScreen.kt)

- When the screen opens, it loads the user's sessions and reopens the active one, or the newest one if none is active, along with its message history.
- Top bar:
  - **History** opens a `ModalBottomSheet` that lists past sessions. Each can be opened or deleted.
  - **New chat** ends the current session and clears the screen.
- Shows "Coach is typing..." while a reply is pending, plus an error banner for network or API failures.
- The list scrolls to the newest message automatically.
- `currentSessionId` and the input text are kept in `rememberSaveable`, so a rotation doesn't lose the conversation.
- `chatbotEntry(token = "dev-token")`: the app has no sign-in yet, so it sends a fixed development token (see §2.2).

### 2.2 New chatbot server ([core/chatbot/](core/chatbot/))

This is a new Python backend. It started as a terminal chatbot and was turned into a FastAPI server in commit `274db4a`.

**Files**

| File | Purpose |
|---|---|
| [main.py](core/chatbot/main.py) | The whole server: sign-in check, chat endpoint, memory, summaries, session endpoints |
| [requirements.txt](core/chatbot/requirements.txt) | `fastapi`, `uvicorn`, `langchain-core`, `langchain-google-genai`, `supabase`, `pydantic`, `python-dotenv` |
| [Dockerfile](core/chatbot/Dockerfile) | `python:3.12-slim` image that runs `uvicorn main:app` on `$PORT` (default 8000) |
| [.dockerignore](core/chatbot/.dockerignore) | Keeps `.env`, the local `chatbot_env/` virtualenv and `__pycache__` out of the image |
| `.env` | `GOOGLE_API_KEY`, `SUPABASE_URL`, `SUPABASE_KEY`, loaded by `python-dotenv` (committed in `5217ad9` for the team) |
| [start_chatbot_server.cmd](core/chatbot/start_chatbot_server.cmd) | Windows launcher on port 8050 (added in `13b5651`) |

**Stack**

- **FastAPI** handles HTTP.
- **Supabase** provides sign-in checks and the Postgres database. The server uses the secret key, so it can read every user's rows. That is why every query also filters by the signed-in user.
- **Gemini** (`gemini-3.5-flash-lite` through LangChain) writes the replies. It uses structured output, so each reply also includes a memory decision (see below).

**Endpoints**

| Method | Path | What it does |
|---|---|---|
| `GET` | `/health` | Returns `{"status": "ok"}` |
| `POST` | `/chat` | Body `{message, session_id?}`. Returns `{session_id, reply}`. Leave out `session_id` to start a new chat. |
| `GET` | `/sessions` | The user's chats, newest first |
| `GET` | `/sessions/{id}/messages` | All messages in one chat, oldest first |
| `POST` | `/sessions/{id}/end` | Marks the chat `ended`. It stays in the list but rejects new messages (409). |
| `DELETE` | `/sessions/{id}` | Deletes the chat and its messages (204) |

Every endpoint except `/health` requires `Authorization: Bearer <Supabase access token>`:

- `get_user_id()` asks Supabase who the token belongs to, so the caller can't fake a user id.
- `load_own_session()` returns 404 unless the session exists **and** belongs to that user, so one user can't open another user's chat.

**Supabase tables used**

| Table | Columns the server uses |
|---|---|
| `users` | `id`. Upserted before a user's first session, because `sessions.user_id` points to it. |
| `sessions` | `id`, `user_id`, `status` (`active`/`ended`), `started_at`, `ended_at`, `session_summary` |
| `messages` | `id`, `session_id`, `user_id`, `role` (`user`/`assistant`), `content`, `created_at`, `folded_into_summary` |
| `global_profile` | `user_id`, `profile_text` (a bulleted list of long-term facts about the user) |

**What happens on `POST /chat`**

1. **Find the session.**
   - With a `session_id`: check it belongs to the user and isn't ended.
   - Without one: create a new session.
2. **Load the user's global profile.** This is long-term memory that carries across all chats.
3. **Load only the messages not yet folded into the session summary.** That means the latest 6 (`RECENT_MESSAGES`), plus any older ones waiting for the next summary batch. This keeps the query and the prompt the same size however long the chat gets.
4. **Ask Gemini.** The prompt has a system prompt and four labelled blocks: global profile, session summary, recent conversation, and current message.
   - The system prompt limits the bot to smoking cessation, tells it to steer off-topic questions back, and forbids diagnosing or acting as a doctor.
   - The structured reply contains `assistant_response`, `memory_update_needed`, `memory_reason` and `memory_information`.
   - Gemini errors, or an empty structured reply, return a 502 with a friendly "try again" message.
5. **Save the user message and the reply in a single insert.** A user message can never be saved without its reply. If saving fails, the user still gets the reply; it just won't appear in history.
6. **Return the reply, then do the housekeeping** as FastAPI `BackgroundTasks`, so the user never waits for it:
   - **`update_global_profile()`:** if Gemini flagged new long-term information (for example "I always crave after coffee"), add it as a new `- ` bullet. `is_duplicate_memory()` skips the bullet if it's already contained in an existing one, or is at least 80% similar to one (`SequenceMatcher`).
   - **`update_session_summary()`:** once at least 8 (`SUMMARY_BATCH`) messages older than the latest 6 have built up, merge them into `sessions.session_summary` with one Gemini call and mark them `folded_into_summary`. Summarising in batches, rather than before every message, roughly halves Gemini calls. If summarising fails, the messages stay unmarked and are retried next time.

### 2.3 Server changes made for the app

Commit `13b5651` makes two changes to the server so the app can use it:

1. **Development token.** `get_user_id()` maps the bearer token `dev-token` to the fixed user `00000000-0000-0000-0000-000000000000`, so the app can chat before in-app Supabase sign-in exists.
2. **Reply cleanup.** `clean_message_content()` now un-escapes literal `\n`, `\t` and `\"` in Gemini output. Without this, replies showed raw backslash sequences in the chat bubbles.

It also adds [start_chatbot_server.cmd](core/chatbot/start_chatbot_server.cmd), which runs `uvicorn main:app --host 0.0.0.0 --port 8050`. Binding to `0.0.0.0` lets a phone on the same Wi-Fi reach it. Commit `eabd148` then switches the model from `gemini-3.6-flash` to `gemini-3.5-flash-lite`.

---

## 3. Voice integration and the Settings entry point

### 3.1 How the user reaches it

```
Header account icon ─► dropdown "Settings" ─► SettingsScreen
                                                 └─ "Craving Voice Detection" ─► VoiceScreen
```

- **`:feature:settings:impl`** ([SettingsScreen.kt](feature/settings/impl/src/main/java/com/example/feature/settings/impl/SettingsScreen.kt)) is a card with five rows: Craving Voice Detection, Account & Profile, Notifications, Privacy & Security, Help & Support. Each row is a `SettingsRowItem` (icon, title, chevron).
- **The Settings module does not depend on `:core:voice`.** `settingsEntry()` only takes callbacks ([SettingsEntry.kt](feature/settings/impl/src/main/java/com/example/feature/settings/impl/SettingsEntry.kt)), and the app shell connects them:

  ```kotlin
  settingsEntry(
      onBackClick = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
      onVoiceDetectionClick = { backStack.add(VoiceNavKey) },
      onAccountClick = { backStack.add(ProfileNavKey) },
      onHelpClick = { backStack.add(HelpNavKey) },
      onNotificationsClick = { /* opens Settings.ACTION_APP_NOTIFICATION_SETTINGS */ },
  )
  ```

  Settings and Voice stay independent. Only `:app` knows that tapping the row should open the voice screen.
- **`:core:voice`** gains [VoiceNavKey.kt](core/voice/src/main/java/com/example/core/voice/VoiceNavKey.kt) and [VoiceEntry.kt](core/voice/src/main/java/com/example/core/voice/VoiceEntry.kt).
- [VoiceScreen.kt](core/voice/src/main/java/com/example/core/voice/VoiceScreen.kt) changes:
  - It gains a back arrow and an `onBackClick` parameter.
  - It no longer adds its own `statusBarsPadding()`, because the shell's `Scaffold` handles insets now.
- `VoiceNavKey` is in `DROP_DOWN_NAV_ITEMS`, so the voice screen opens full-screen without the bottom bar, the same as the other Settings pages.
- "Privacy & Security" has no destination yet. Its callback defaults to a no-op.

### 3.2 What the voice screen does

- A **Background listening** switch:
  - If microphone permission is missing, it asks for it.
  - On Android 13+ it also asks for notification permission.
  - It then calls `VoiceListeningService.start()`.
- If notifications are blocked, it shows a warning card with an "Open notification settings" button. The check runs again when the user comes back to the screen.
- A **Last heard** card shows:
  - the transcript,
  - whether a craving was detected,
  - the similarity score and the closest reference phrase,
  - whether a notification was sent or skipped (cooldown, or notifications off).
- Server errors appear in red, for example "Can't reach the voice server (…)".

### 3.3 Pipeline (unchanged in this update, listed for context)

```
Mic (16 kHz mono PCM)
  → SpeechSegmenter (WebRTC VAD on the phone: 240 ms to start, 2.5 s silence to end, 30 s max)
  → Channel(capacity 5, DROP_OLDEST)
  → VoiceApi.analyze()  POST /analyze  (3 attempts, 3 s apart)
  → server: vad.py → whisper.py (whisper-server, tiny.en) → similarity.py
  → [{text, is_craving, score, match}, ...]
  → CravingNotifier (supportive message, cooldown)  +  VoiceScreen "Last heard"
```

Changes this update made to make the pipeline work inside the app:

- **Server address.** [VoiceApi.kt](core/voice/src/main/java/com/example/core/voice/VoiceApi.kt) replaces the fixed `http://10.0.2.2:8000` with the same emulator check the chatbot uses:
  - emulator → `http://10.0.2.2:8080`
  - real phone → `http://192.168.1.3:8080`
- **Cleartext HTTP.** [voice_network_security_config.xml](core/voice/src/main/res/xml/voice_network_security_config.xml) changed from allowing cleartext only for `10.0.2.2` and `localhost` to `<base-config cleartextTrafficPermitted="true" />`. A real phone on the LAN can now reach the laptop.
- **Server launcher.** [start_voice_server.cmd](core/voice/src/main/python/start_voice_server.cmd) prints the Whisper binary and model paths, then runs `uvicorn main:app --host 0.0.0.0 --port 8080`.
- **Resume on open.** `MainActivity.onResume()` calls `VoiceListeningService.resumeIfEnabled()`. Android 14+ does not allow a microphone foreground service to start from the background. So if the system killed the service, or the phone rebooted, listening restarts the next time the user opens the app. `ListeningBootReceiver` shows a "tap to resume" notification after a reboot or app update.

### 3.4 Ports used in local development

| Service | Port | Started by |
|---|---|---|
| Voice server (FastAPI) | 8080 | `core/voice/src/main/python/start_voice_server.cmd` |
| whisper-server (internal to the voice server) | 8178, `127.0.0.1` only | launched by `whisper.py` at startup |
| Chatbot server (FastAPI) | 8050 | `core/chatbot/start_chatbot_server.cmd` |

---

## 4. How the voice threshold was set

The server decides whether a sentence is a craving. The phone only reads `is_craving`. The decision is made in [similarity.py](core/voice/src/main/python/similarity.py):

```python
THRESHOLD = 0.47  # best F1 on test_phrases_1000.csv
```

### 4.1 What the score means

For each transcribed segment, `score_text()` works as follows:

1. **Negation filter.** If the text matches one of the `NEGATION_PATTERNS` regexes (for example "don't want", "quit smoking", "won't smoke", "10 days without nicotine", "skipped … cigarette"), it returns `score = 0.0`, not a craving.
2. **Embed.** It encodes the text with `all-MiniLM-L6-v2` (ONNX, 8-bit quantised, 384-dim, mean-pooled, L2-normalised).
3. **Compare.** It takes the dot product (cosine similarity) with the 80 reference embeddings in [reference_embeddings.json](core/voice/src/main/python/reference_embeddings.json): **50 `craving`** phrases ("I need a smoke", "Just one puff", "Work is stressing me out, I need a smoke", …) and **30 `not_craving`** phrases ("I kicked the habit", "I fought off the urge", "I'm not lighting up tonight", …).
4. **Anti-craving override.** If the closest `not_craving` phrase scores at least as high as the closest `craving` phrase, it returns `score = 0.0`.
5. **Threshold.** Otherwise `score` is the best craving similarity, and `is_craving = score >= THRESHOLD`.

Steps 1 and 4 run before the threshold, so the threshold only decides cases that already look more like a craving than a non-craving.

### 4.2 Test data

Two labelled CSVs, each with the columns `text,label,category` (`label` 1 = craving, 0 = not):

| File | Rows | How it was made | Craving categories | Non-craving categories |
|---|---|---|---|---|
| `test_phrases_150.csv` | 75 / 75 | Hand-written, varied wording | direct, slang, stress, habit, urge, tentative, social | hard_negative, negation, everyday |
| `test_phrases_1000.csv` | 500 / 500 | Templates: filler ("Ugh", "Bro", …) × verb ("I'm dying for", "I could go for", …) × object ("a cig", "a vape hit", …) × context ("after dinner", "to calm down", …) | want (200), lets_go (60), ask (50), stress (120), urge (70) | other_want (120), hard_negative (100), negation (130), everyday (150) |

Two categories matter most for the threshold:

- **`hard_negative`** sentences mention smoking without a craving: "My dad smokes too much", "I love smoked salmon", "Holy smokes that was close".
- **`other_want`** sentences use craving grammar for something else: "I'm dying for a nap to calm down", "I so need chocolate tonight".

These are the sentences that push the threshold up.

### 4.3 The grid search (`find_threshold.py`)

1. Run every phrase in the CSV through the similarity scorer. Save `text,label,score` to `threshold_scores_<n>.csv`.
2. Try every threshold from **0.00 to 1.00 in steps of 0.01**. At each one, predict `score >= t` and compute:
   - precision = TP / (TP + FP), i.e. of the alerts, how many were real
   - recall = TP / (TP + FN), i.e. of the cravings, how many were caught
   - **F1** = 2PR / (P + R)
3. Pick the threshold with the **highest F1**. Report the **stable range**: every threshold whose F1 is within 0.02 of the best.
4. Print every false alarm and missed craving at the best threshold.

Usage: `python find_threshold.py test_phrases_1000.csv`

### 4.4 Results

These numbers come from re-running the script on the current `similarity.py`, model and reference set:

| Test set | Best threshold | F1 | Precision | Recall | Stable range (F1 within 0.02 of best) |
|---|---|---|---|---|---|
| `test_phrases_1000.csv` | **0.47** | 0.857 | 0.774 | 0.960 | 0.42 – 0.63 |
| `test_phrases_150.csv` | 0.57 | 0.920 | 0.920 | 0.920 | 0.56 – 0.62 |

How F1 changes with the threshold on the 1000-phrase set:

| Threshold | 0.40 | 0.45 | **0.47** | 0.50 | 0.55 | 0.60 | 0.65 |
|---|---|---|---|---|---|---|---|
| Precision | 0.732 | 0.766 | **0.774** | 0.788 | 0.825 | 0.899 | 0.915 |
| Recall | 0.968 | 0.964 | **0.960** | 0.928 | 0.858 | 0.816 | 0.756 |
| F1 | 0.834 | 0.854 | **0.857** | 0.852 | 0.841 | 0.855 | 0.828 |

**Why 0.47.** It has the best F1 on the larger set, and it sits inside that set's wide stable range (0.42 – 0.63). It favours recall: 96% of cravings are caught. The cost is more false alarms, which the notification cooldown in `CravingNotifier` limits.

On the hand-written 150 set, 0.47 gives F1 0.882 (P 0.826, R 0.947). That is below that set's own best of 0.920, and 0.47 is outside its stable range. In practice the F1 curve is fairly flat from 0.45 to 0.60 on both sets. Moving towards **0.57 – 0.60** would cut false alarms considerably (precision 0.899 at 0.60 on the 1000 set) and lose about 14 points of recall. That is a product decision, not something the data settles.

### 4.5 Where the errors are at 0.47 (1000-phrase set)

| Category | Rows | Wrong | Zeroed by negation or anti-craving | Typical error |
|---|---|---|---|---|
| want | 200 | 0 | 0 | none |
| lets_go | 60 | 3 | 1 | "go vape" scores 0.44–0.47 |
| ask | 50 | 1 | 0 | "Can I bum a vape hit" (0.44) |
| stress | 120 | 6 | 3 | "That exam was brutal, I want a drag" (0.33) |
| urge | 70 | 10 | 10 | every "lighting up" phrase lands closest to the anti-craving reference "I'm not lighting up tonight" and is zeroed |
| other_want | 120 | 45 | 19 | "Seriously I gotta have some fresh air to calm down" (0.64) |
| hard_negative | 100 | 53 | 43 | "Nicotine is really addictive" (0.74), "There's smoke coming from the grill" (0.66) |
| negation | 130 | 38 | 92 | "I'm not having a cig" (0.83): no regex covers "not having" |
| everyday | 150 | 4 | 58 | "I'm so hungry" (0.62) |

Totals: TP 480, FP 140, FN 20, TN 360.

Raising the threshold would not fix most of these errors. Better rules and reference phrases would:

- **Negation regex.** Add `not having`, `don't need`/`don't want` + noun forms, and `said no to`. This alone would remove most of the 38 negation false alarms, which score 0.6 – 0.83.
- **Anti-craving reference.** Reword "I'm not lighting up tonight" (or rely on the regex instead), so it stops absorbing genuine "lighting up" cravings.
- **More `not_craving` references** for third-person and factual statements ("My dad smokes", "Smoking is addictive", "smoke from the grill"), and for "need X to calm down" where X isn't nicotine.

After changing the rules or references, re-run the grid search on both CSVs before changing `THRESHOLD`.

### 4.6 Reproducing the numbers

`find_threshold.py` and the two CSVs are not in the repo yet. Also, the script imports `calculate_similarity`, which was the name in the first version of `similarity.py` (commit `1e95a8b`). The current module exposes `score_text()` instead. To run the script without editing it, add this shim next to it:

```python
# run_threshold.py: lets find_threshold.py use the current similarity.py
import runpy, sys
sys.path.insert(0, "core/voice/src/main/python")
import similarity

similarity.calculate_similarity = lambda text: (
    (r := similarity.score_text(text))["match"], r["score"]
)
runpy.run_path("find_threshold.py", run_name="__main__")
```

Then run `python run_threshold.py test_phrases_1000.csv`.

A better fix is to commit the script and CSVs under `core/voice/src/main/python/eval/` and change the script to call `score_text()` directly.

---

## 5. Running it locally

1. **Voice server.**
   - Build `whisper-server` (whisper.cpp under `core/whisper/.../whisper.cpp`).
   - Place `ggml-tiny.en.bin` in `app/src/main/assets/models/`.
   - Run `core/voice/src/main/python/start_voice_server.cmd`.
2. **Chatbot server.**
   - Make sure `core/chatbot/.env` has `SUPABASE_URL`, `SUPABASE_KEY` and `GOOGLE_API_KEY`.
   - Run `core/chatbot/start_chatbot_server.cmd`.
3. **App.**
   - On an emulator it connects to `10.0.2.2` automatically.
   - On a real phone, the laptop must be at `192.168.1.3` on the same Wi-Fi. Otherwise, edit `getBaseUrl()` in both `VoiceApi.kt` and `ChatbotApi.kt`.

---

## 6. Known gaps and follow-ups

| Item | Where | Note |
|---|---|---|
| `dev-token` accepted by the chatbot server | `core/chatbot/main.py` `get_user_id()` | Anyone who sends this token acts as the shared dev user. It must be removed or put behind an env flag before the server is deployed. |
| Chatbot secrets committed | `core/chatbot/.env` | Includes the Supabase secret key, which bypasses row-level security. Fine while the repo stays private. For deployment, use the hosting provider's app settings (as the Dockerfile comment says), and rotate the keys if the repo is ever shared. |
| Hard-coded LAN IP `192.168.1.3` | `VoiceApi.kt`, `ChatbotApi.kt` | Move to a `BuildConfig` field or `local.properties` value. Duplicated emulator-detection code could live in one core module. |
| Cleartext HTTP allowed for every host | `voice_network_security_config.xml`, app manifest | Fine for LAN testing. Use HTTPS for deployed servers and limit cleartext to debug builds. |
| Craving notification cooldown is 1 min | `CravingNotifier.COOLDOWN_MS` | The comment says to use 20 min for release. The voice screen text also says "1 min cooldown". |
| Header greets a hard-coded "Prekshya" | `MainActivity.kt` | Replace once sign-in exists. |
| Empty stray file | `feature/settings/impl/src/main/java/com/example/impl/SettingsScreen.kt` | Contains only a `package` line. Safe to delete. |
| Threshold tooling not in the repo | see §4.6 | Commit the script and CSVs so the threshold can be re-checked after changes to the rules or references. |
| Negation and anti-craving gaps | `similarity.py`, `reference_embeddings.json` | See §4.5. These would gain more than changing the threshold. |
