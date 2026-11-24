# Project Summary

## 1. Overview

This project is a mobile companion that helps people quit smoking and stay smoke-free. It combines everyday self-help tools, progress tracking, a conversational coach, and an optional hands-free feature that notices when the user may be struggling and offers timely encouragement.

The system has three parts:

- **A mobile application**, which is everything the user sees and touches.
- **A conversational coaching service**, which powers the in-app coach.
- **A voice support service**, which powers the optional craving-awareness feature.

All three are built around one goal: get the right kind of support to the user at the moment they need it, while respecting their privacy and their autonomy.

---

## 2. Product Goals

| Goal | How the product addresses it |
|---|---|
| Keep the user motivated day to day | Small daily challenges, visible progress, and positive reinforcement |
| Help in the moment a craving hits | One-tap "I feel like smoking" access, coping tools, and an always-available coach |
| Make support personal | The coach gradually learns what matters to each user and adapts its guidance |
| Reach the user even when the app is closed | Optional background voice awareness with gentle, supportive nudges |
| Respect privacy | Opt-in features, clear on-screen disclosure, and minimal data leaving the device |
| Stay honest and safe | The coach stays on topic, avoids medical claims, and points users to professionals when appropriate |

---

## 3. User Experience

### 3.1 Navigation

The app has four main destinations, reachable from a navigation bar that adapts to the device (bottom bar on phones, side rail on larger screens):

1. **Home**: the daily dashboard
2. **Tools**: self-help activities
3. **Coach**: the conversational assistant
4. **Explore**: a space for discovery content

A personalised header greets the user by name and gives access to a notifications button and an account menu with **Profile**, **Settings**, **Send Feedback**, **About**, and **Help**. Secondary screens open as focused, full-screen views without the main navigation bar, and the navigation bar also hides while the keyboard is open so the screen stays uncluttered.

### 3.2 Home

- **Quick self-report actions.** Two prominent buttons let the user say "I feel like smoking" or "I smoked." The idea is to make honest check-ins quick and free of judgement.
- **Challenges.** A scrollable set of small, healthy daily goals, such as hydration, breathing practice, a short walk, reading, journaling, and affirmations. The user ticks challenges off as they complete them and can add their own.
- **My Progress.** At-a-glance cards for money saved, cigarettes avoided, life regained, and time since quitting.

### 3.3 Tools

A curated list of coping activities, each with a short description:

- **Diary**: journal thoughts and feelings
- **Games**: short distractions to ride out an urge
- **Breathing Exercises**: guided, mindful breathing to release stress
- **Why I Quit?**: a personal list of reasons and affirmations
- **Talk to Someone**: a reminder that the user doesn't have to do this alone, with routes to human support

### 3.4 Coach

A chat-style assistant focused only on quitting smoking.

- Users can talk about cravings, triggers, habits, setbacks, and motivation.
- Conversations are saved. The user can reopen past conversations, start a fresh one, or permanently delete one.
- The coach's replies become more personal over time because it remembers lasting, relevant facts the user shares, such as what usually triggers them or which strategies have helped.
- While a reply is being prepared, a typing indicator appears. If the coach can't be reached, a clear error banner explains what happened.

### 3.5 Voice Craving Support (optional)

An opt-in feature, managed from **Settings → Craving Voice Detection**.

- When the user turns it on, the app listens in the background, including while the app is closed, for spoken language that suggests a craving.
- When it detects a likely craving, the user receives a short, supportive notification (for example, a suggestion to breathe slowly or to revisit their reasons for quitting). Tapping it opens the app.
- Nudges are rate-limited so the user is never overwhelmed.
- A transparency panel shows the most recent result, whether a craving was detected, and whether a notification was sent, so the user can see the feature working.
- A persistent system notification is shown whenever listening is active, and the user can stop listening at any time from that notification.
- If the user has turned notifications off, the app explains the impact and offers a shortcut to the system settings.

### 3.6 Settings

A simple list of entries: Craving Voice Detection, Account & Profile, Notifications (opens the system notification settings), Privacy & Security, and Help & Support.

---

## 4. System Architecture (Conceptual)

```
┌──────────────────────────────┐
│        Mobile Application    │
│  • Screens & navigation      │
│  • Shared design system      │
│  • Background voice helper   │
└──────────┬─────────┬─────────┘
           │         │
   chat requests   short speech clips
           │         │
┌──────────▼───┐ ┌───▼────────────────┐
│  Coaching    │ │  Voice Support     │
│  Service     │ │  Service           │
│              │ │                    │
│ • Identity   │ │ • Speech → text    │
│   checks     │ │ • Meaning analysis │
│ • Language   │ │ • Per-utterance    │
│   reasoning  │ │   assessment       │
│ • Memory     │ │                    │
└──────┬───────┘ └────────────────────┘
       │
┌──────▼───────────────────────┐
│  Managed identity & data     │
│  platform (users, chats,     │
│  long-term memory)           │
└──────────────────────────────┘
```

- The **mobile application** is split into independent feature areas that share a common visual design system, so each area can evolve on its own.
- The **coaching service** checks who the user is, gathers that user's context, asks a language model for a reply, and stores the result.
- The **voice support service** turns short speech clips into text and judges whether that text expresses a craving.
- A **managed identity and data platform** stores user accounts, conversation history, and each user's long-term coaching memory.

Both services are packaged for container-based cloud hosting, and both can also be run locally for development.

### 4.1 Architectural Patterns

**Mobile application**

- **Single-activity design.** The whole app runs inside one host screen. Moving between screens is handled by an in-app back stack instead of separate system screens, which keeps transitions fast and state easy to manage.
- **Declarative UI.** Every screen is described as a function of its current state. When the state changes, the interface redraws itself, so there is no manual view bookkeeping.
- **Unidirectional data flow.** State flows down into screens, and user actions flow back up as events. Screens don't change shared state directly.
- **Reactive state streams.** Long-running background work, such as the listening helper, publishes its status as observable streams. Any screen can subscribe and stays in sync automatically.
- **Contract / implementation split.** Each feature exposes a small public contract (how to reach it) separately from its internal implementation. The app shell only depends on the contracts, which keeps features decoupled and swappable.
- **Layered modules.** Modules sit in three layers:
  - the *app shell*, which handles composition and navigation
  - *feature modules*, one per user-facing area
  - *core modules*, which hold the shared design system and platform capabilities such as voice

  Dependencies only point downward, from the shell to features to core.
- **Separation of concerns inside features.** Network access, background processing, notifications and presentation each live in their own clearly bounded units.

**Backend services**

- **Stateless request handling.** Each request carries everything needed to serve it, and durable state lives in the managed data platform. Because of this, the services can be scaled horizontally.
- **Pipeline processing.** Voice analysis runs as a staged pipeline: isolate speech, transcribe it, interpret its meaning, return a result. Each stage has a single responsibility.
- **Deferred background work.** Housekeeping that isn't needed for the immediate reply runs after the response has been sent.

---

## 5. How the Key Capabilities Work (High Level)

### 5.1 Conversational Coaching

1. **Identity first.** Every request is tied to a signed-in user, and the service only reads or changes that user's own data.
2. **Context assembly.** For each message, the coach considers:
   - the user's long-term profile (lasting facts learned over time),
   - a condensed summary of the earlier parts of the current conversation, and
   - the most recent exchanges, word for word.
3. **Guided response.** The model works within clear rules: it stays on smoking cessation, redirects off-topic questions, never presents itself as a medical professional, and encourages users to seek appropriate care when health concerns come up.
4. **Learning what matters.** Alongside each reply, the coach decides whether the user said anything worth remembering long term, such as a recurring trigger or a coping strategy that works, as opposed to a passing, one-off detail. Near-duplicate memories are filtered out so the profile stays clean.
5. **Keeping conversations efficient.** As a conversation grows, older parts are periodically condensed into a summary. This keeps responses fast and consistent however long the chat becomes.
6. **No waiting on housekeeping.** Updating memory and summaries happens after the reply has been sent, so the user never waits for it.
7. **Session lifecycle.** Conversations can be continued, ended (kept for reference but closed to new messages), or deleted permanently along with their messages.

### 5.2 Voice Craving Support

1. **On-device filtering.** The phone listens locally and keeps only stretches of actual speech. Silence and background noise are discarded on the device.
2. **Short, purposeful uploads.** Only brief speech clips are sent for analysis. Audio is analysed and not stored.
3. **Understanding meaning, not keywords.** The service transcribes each clip and compares its meaning with a curated set of example phrases that express cravings, as well as examples that don't.
4. **Avoiding false alarms.** Statements that express resistance or progress, such as saying they've quit, are deliberately not treated as cravings.
5. **Device-side decision.** The app receives an assessment for each utterance and decides whether a supportive nudge is appropriate, taking into account the rate limit and the user's notification settings.

---

## 6. Privacy & Safety

- **Opt-in only.** Background listening is off by default and needs the user's explicit permission.
- **Always visible.** A persistent indicator is shown whenever listening is active, with a one-tap stop.
- **Data minimisation.** Non-speech audio never leaves the device, and audio is not kept after analysis.
- **Per-user isolation.** Each user can only access their own conversations and memory.
- **Responsible guidance.** The coach is limited to supportive, general guidance, avoids diagnosis, and refers users to professionals for medical matters and emergencies.
- **User control.** Users can end or permanently delete conversations at any time.

---

## 7. Reliability & Resilience

- **Microphone recovery.** If audio capture is interrupted, the app restarts it automatically. If it keeps failing, it stops cleanly and asks the user to resume.
- **Respecting platform rules.** Modern mobile operating systems limit when background audio can start. When listening can't resume on its own (for example, after a restart or an app update), the user gets a gentle "tap to resume" prompt, and listening also resumes automatically the next time they open the app.
- **Network tolerance.** Uploads are retried when the network is unreliable. If the service stays unreachable, the app shows a clear status message instead of failing silently.
- **Backpressure.** If analysis falls behind, the app favours fresh speech over stale clips so it stays responsive.
- **Graceful degradation in chat.** If saving history fails, the user still gets their reply. If the language service is unavailable, the user sees a friendly retry message.

---

## 8. Engineering Practices

- **Modular structure.** Each feature is isolated behind a lightweight contract, which keeps dependencies clean and lets features be developed and tested independently.
- **Shared design system.** Reusable components (buttons, cards, task items, progress tiles, header, adaptive navigation) give the app one consistent look.
- **Adaptive, themed UI.** The layout adapts across phone and tablet sizes and supports light and dark themes.
- **Deployable services.** The backend services are containerised, and secrets are supplied by the hosting environment rather than built into the containers.
- **Developer convenience.** One-step local launchers exist for each backend service, and the app automatically targets the right backend depending on whether it is running on an emulator or a real device.
- **Test scaffolding.** Every module has unit and on-device test harnesses ready for expansion.

---

## 9. Current Status

| Area | Status |
|---|---|
| Navigation, header, account menu | Implemented |
| Shared design system & theming | Implemented |
| Home dashboard (layout, challenges, progress tiles) | Implemented; content currently illustrative |
| Tools catalogue | Implemented as a menu; individual tools are in progress |
| Coach (chat, history, new/end/delete, long-term memory) | Implemented end to end |
| Voice craving support (background listening, analysis, nudges, recovery) | Implemented end to end |
| Settings hub | Implemented; some destinations are placeholders |
| Explore, Profile, Feedback, About, Help | Placeholder screens |
| Full user sign-in in the app | Planned; the backend already enforces identity |
| In-app notifications centre | Planned |

---

## 10. Roadmap Themes

- Connect the Home dashboard to real, personal progress data.
- Build out each self-help tool (diary, breathing, games, reasons to quit, talk to someone).
- Complete in-app sign-in and profile management.
- Link the "I feel like smoking" action directly to the coach and coping tools.
- Fine-tune nudge timing and content for real-world use.
- Move to a full MVVM structure: move screen state and logic into dedicated view models backed by data repositories, so they survive configuration changes and are easier to test.
- Strengthen transport security and production deployment settings.
- Expand automated test coverage across features.
