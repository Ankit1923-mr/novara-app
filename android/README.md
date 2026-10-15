# NOVARA

Personalized language & country-immersion learning app. B.Tech final year project.

## Overview

NOVARA is an AI-powered, adaptive Spanish language immersion and conversational practice platform built natively for Android using **Jetpack Compose (Material 3)**, **Kotlin Coroutines & Flow**, **Retrofit with Kotlinx Serialization**, and **Android Jetpack DataStore**.

---

## Repository Structure

- **`/app`** — Native Android (Kotlin/Jetpack Compose) Application
  - **Intake & Profile Setup**: Learner identity, CEFR proficiency levels (`A1`, `A2`, `B1`, `B2`), region selection (e.g. Madrid, Mexico City), MVP learning purpose (`trip`, `casual`), topics of interest, and target weak areas.
  - **Adaptive Scenarios**: Real-life situational immersion generated dynamically by the backend Personalization Engine based on learner goals and selection scores.
  - **AI Conversation Partner**: Real-time turn-by-turn Spanish dialogue with automatic AI pace adjustments and inline repair feedback cards.
  - **Spanish Learning Hub & Topics**: Interactive vocabulary flashcards with cultural tips and knowledge-check mini-quizzes.
  - **Readiness Competency Dashboard**: Real-time visualization of aggregate readiness and breakdown scores across language accuracy, repair success rate, register appropriateness, and transfer success.
  - **Standalone Repair Lab**: Interactive tool for sentence pattern analysis and grammar/lexical repair strategy experimentation.
  - **Local Persistence & DataStore**: Multi-profile management, offline fallback caching, session persistence, and dynamic `X-API-Key` & JWT Bearer token authentication.
- **`/docs`** — API contract, architectural specs, and documentation.

---

## MVP Scope

- **Language**: Spanish
- **Learner Purposes**: `trip` (Hotels, dining, transport) and `casual` (Smalltalk, culture, hobbies)
- **CEFR Levels**: `A1`, `A2`, `B1`, `B2`

---

## Completed Android Features

1. **Intake & Multi-Profile Management (`ProfileScreen`, `ProfileViewModel`)**:
   - Interactive profile creation with identity generation, region selector, CEFR level chips, learning purpose selector (`trip`/`casual`), interests, and focus weak areas.
   - Dual-header authentication integration (`X-API-Key` and JWT Bearer token via `/auth/signup` and `/auth/login`).
   - Profile switcher dialog supporting profile creation, switching, and removal stored locally in DataStore.

2. **Adaptive Situational Scenarios (`ScenarioScreen`, `ScenarioViewModel`)**:
   - Fetches personalized scenarios from `GET /scenario?learner_id={id}`.
   - Displays setting details, situation tags, adaptive selection scores, interest/weak area overlaps, and opening line.
   - Instant launch into live AI conversation partner.

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

7. **Networking & Authentication Architecture**:
   - Implemented `ApiHeaderInterceptor` for automatically attaching `X-API-Key` (`fXTFEdbxbsOE9fuU8E-xbYiwOEyRzgZBzJcwA_DaJ9c`) and JWT Bearer token headers.
   - Full offline fallback caching and robust error handling for cold starts and network glitches.

---

## Live Deployed Backend

- **Base URL**: `https://novara-api-dnhc.onrender.com`
- **Header**: `X-API-Key: fXTFEdbxbsOE9fuU8E-xbYiwOEyRzgZBzJcwA_DaJ9c`
```
/auth/login or /auth/signup  -->  /profile  -->  /scenario  -->  /conversation  -->  /readiness
```

---

## How to Run the Android App

1. Open the project root in **Android Studio**.
2. Ensure Android SDK 35 is installed.
3. Sync Gradle and build the project (`./gradlew assembleDebug`).
4. Run on an Android Emulator or connected device (Android 7.0 / API 24+).
