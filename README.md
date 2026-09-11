# NOVARA

Personalized language & country-immersion learning app. B.Tech final year project.

- `/backend` — FastAPI backend: Knowledge Graph, Adaptive Engine, AI Conversation Partner, Repair Engine, Personalization Engine, Readiness Scoring Engine. Owner: Ankit.
- `/android` — Android (Kotlin/Compose) app: intake, scenario, conversation, readiness dashboard. Owner: Sakshi.
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
- All 5 API endpoints (`/profile`, `/scenario`, `/conversation`, `/repair`, `/readiness`) wired to real logic, no mocked responses remaining
- 83 automated tests, all passing
- Deployed and verified live end-to-end: `https://novara-api-dnhc.onrender.com`
- Kept awake via a scheduled GitHub Actions ping (Render free tier sleeps after 15 minutes idle)

**Android: not started.** The `/android` folder exists with a setup README only — no app code yet.

**Documentation:**
- API contract — done, up to date with the live deployment
- Engineering decisions and bugs log (`docs/challenges.md`) — done, updated throughout backend development
- Review feedback log (`docs/review2-feedback.md`) — template only, actual panel comments not yet filled in
- Project report chapters — not started

## What's left

- Android app: project scaffold, intake/purpose-selection screens, local profile storage, scenario screen, conversation/chat UI with inline repair display, readiness dashboard, and the integration pass connecting it to the live backend
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
Open `/android` in Android Studio, sync Gradle, run on emulator/device. Point network calls at the deployed backend URL above, or `http://localhost:8000` for local development.

## Contract

See `docs/api-contract.md` — frozen shape both sides build against, including the live deployed URL. Update it, don't diverge from it silently.
