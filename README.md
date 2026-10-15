# NOVARA Android Application

Native Android client for NOVARA — Personalized Language & Country-Immersion Learning App.

## Architecture & Tech Stack

- **UI Framework**: Jetpack Compose with Material 3 Design System
- **Asynchronous Architecture**: Kotlin Coroutines & `StateFlow`
- **Networking & Serialization**: Retrofit 2 + OkHttp 4 + `kotlinx.serialization`
- **Local Persistence**: Android Jetpack DataStore Preferences
- **Target SDK**: Android 15 (SDK 35) / Min SDK 24 (Android 7.0+)

---

## App Features & Modules

1. **Intake & Profile Setup (`ProfileScreen`, `ProfileViewModel`)**:
   - Identity generation (`learner_id`), CEFR level selection (`A1`, `A2`, `B1`, `B2`), region configuration, learning purpose (`trip`, `casual`), topics of interest, and target weak areas.
   - Dynamic API key (`fXTFEdbxbsOE9fuU8E-xbYiwOEyRzgZBzJcwA_DaJ9c`) & JWT Bearer token authentication.
   - Profile switcher dialog supporting profile creation, switching, and removal stored locally in DataStore.

2. **Adaptive Situational Scenarios (`ScenarioScreen`, `ScenarioViewModel`)**:
   - Fetches personalized scenarios from `GET /scenario?learner_id={id}`.
   - Displays setting details, situation tags, adaptive selection scores, interest/weak area overlaps, and opening line.
   - Direct launch into live AI conversation partner.

3. **AI Conversation Partner & Real-Time Repair (`ChatScreen`, `ChatViewModel`)**:
   - Turn-by-turn Spanish conversation UI with message start/end timing measurement (`response_time_ms`).
   - Live AI response rendering with adaptive pace change banners (`PaceChangeBanner`) and inline error repair cards (`RepairFeedbackCard`) displaying error type (`lexical`, `grammar`, `register`, `comprehension`), strategy (`clarify`, `rephrase`, `hint`), and repair text.

4. **Spanish Learning Hub & Vocabulary Modules (`TopicsScreen`, `TopicsViewModel`)**:
   - Module list loaded from `GET /topics?learner_id={id}` and `GET /topics/{topic_id}`.
   - Curated vocabulary flashcards with cultural notes.
   - Interactive mini-quizzes (`POST /topics/{topic_id}/submit`) with instant score and pace feedback.

5. **Readiness Competency Dashboard (`ReadinessScreen`, `ReadinessViewModel`)**:
   - Fetches live readiness score breakdown from `GET /readiness?learner_id={id}`.
   - Displays aggregate score gauge, competency breakdown bars, purpose weights, and recommendations.

6. **Standalone Repair Practice Lab (`RepairPracticeScreen`, `RepairViewModel`)**:
   - Interactive testbed connecting to `POST /repair` for testing custom learner utterances against expected patterns.

---

## API Interceptor & Call Sequence

The app implements `ApiHeaderInterceptor` to automatically attach required headers:
```
X-API-Key: fXTFEdbxbsOE9fuU8E-xbYiwOEyRzgZBzJcwA_DaJ9c
Authorization: Bearer <token>
```

**Backend Call Order**:
```
/auth/login or /auth/signup  -->  /profile  -->  /scenario  -->  /conversation  -->  /readiness
```

---

## How to Build & Run

1. Open `/android` in Android Studio (Ladybug 2024.2.1+).
2. Sync Gradle.
3. Run `./gradlew assembleDebug` or launch on emulator / physical device.
