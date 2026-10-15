# NOVARA

Personalized language & country-immersion learning app. B.Tech final year project.

- `/backend` — FastAPI backend: Knowledge Graph, Adaptive Engine, AI Conversation Partner, Repair Engine, Personalization Engine, Readiness Scoring Engine. Owner: Ankit.
- `/android` — Native Android (Kotlin/Jetpack Compose) app: intake, profile setup, scenario selection, AI conversation partner, readiness dashboard, repair practice lab, learning hub. Owner: Sakshi.
- `/web` — React web frontend dashboard.
- `/docs` — API contract, feedback log, challenges log.

## Scope

MVP covers Spanish, for the Trip and Casual learner purposes only. Exam and Relocation purposes, and an ML-trained (rather than rule-based) repair selector, are not built.

## Current status

**Backend: complete and deployed.**

- Knowledge Graph — 35 hand-authored Spanish phrase nodes across Trip (café, transport, hotel, restaurant, airport, directions) and Casual (smalltalk, food, hobbies, culture) situations
- Adaptive Learning Engine — rule-based scenario selection by purpose, learner interests, and novelty (avoids repeating recently-seen situations)
- AI Conversation Partner — live LLM integration via OpenRouter, free-tier only, with a 9-model fallback chain, retry-with-backoff on rate limits, and a primary/backup API key pair
- Repair Engine — rule-based error classifier (comprehension / lexical / register / grammar) and strategy selector (clarify / rephrase / hint)
- Personalization Engine — rolling per-learner pace and confidence scores, updated after every conversation turn
- Readiness Scoring Engine — purpose-weighted aggregate score across four competency dimensions, computed from real interaction history
- All API endpoints (`/auth/login`, `/auth/signup`, `/profile`, `/scenario`, `/conversation`, `/repair`, `/readiness`, `/me`, `/topics`) wired to real logic
- 83 automated tests, all passing
- Deployed and verified live end-to-end: `https://novara-api-dnhc.onrender.com`
- Kept awake via a scheduled GitHub Actions ping (Render free tier sleeps after 15 minutes idle)

**Android: complete and fully integrated.**

- Native Android app built with **Jetpack Compose (Material 3)**, **Kotlin Coroutines & Flow**, **Retrofit**, and **Jetpack DataStore**.
- **Intake & Profile Setup**: Identity generation, CEFR level selection (`A1`–`B2`), target region (e.g. Madrid, Mexico City), learning purpose (`trip`/`casual`), interests, weak areas, and multi-profile switcher.
- **Dual-Header Authentication**: Automated `X-API-Key` (`fXTFEdbxbsOE9fuU8E-xbYiwOEyRzgZBzJcwA_DaJ9c`) + JWT Bearer token authentication via `/auth/signup` and `/auth/login`.
- **Adaptive Scenarios**: Real-time personalized scenario generation via `GET /scenario`.
- **AI Conversation Partner**: Turn-by-turn Spanish chat UI with `response_time_ms` measurement, adaptive pace change banners (`PaceChangeBanner`), and inline error repair cards (`RepairFeedbackCard`).
- **Spanish Learning Hub**: Interactive vocabulary flashcards with cultural tips and mini-quizzes (`POST /topics/{topic_id}/submit`).
- **Readiness Competency Dashboard**: Live aggregate score gauge and 4-metric breakdown visualization (`GET /readiness`).
- **Standalone Repair Practice Lab**: Interactive grammar/lexical repair strategy testbed (`POST /repair`).
- **Offline Caching & DataStore**: Multi-profile storage, session caching, and full fallback offline support.

**Documentation:**
- API contract — done, up to date with live deployment
- Engineering decisions and bugs log (`docs/challenges.md`) — done, updated throughout development
- Review feedback log (`docs/review2-feedback.md`) — template only, actual panel comments not yet filled in
- Project report chapters — in progress

## What's left

- Fill in actual review panel feedback and track action taken on each comment
- Write up methodology, algorithms, and results for the project report
- Data persistence: the backend currently holds all learner data in memory only — it resets on every server restart or redeploy. A real database is not yet in scope
- Exam and Relocation learner purposes (out of current MVP scope)
- ML-trained repair strategy selection (current version is rule-based)

## Setup

**Backend**
```
cd backend
pip install -r requirements.txt
```
Create `backend/.env` (gitignored, never commit it) with:
```
OPENROUTER_API_KEY=your-key-here
OPENROUTER_API_KEY_BACKUP=your-backup-key-here
OPENROUTER_MODEL=nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free
```
Get a free key at openrouter.ai. Without it, `/conversation` still works but returns a `[offline] ...` placeholder reply instead of a real one.
```
uvicorn app.main:app --reload
```

**Android**
Open `/android` in Android Studio, sync Gradle, run on emulator/device. Point network calls at the deployed backend URL above (`https://novara-api-dnhc.onrender.com`), or `http://localhost:8000` for local development.

## Contract

See `docs/api-contract.md` — frozen shape both sides build against, including the live deployed URL.
