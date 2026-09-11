# NOVARA

Personalized language & country-immersion learning app. B.Tech final year project.

- `/backend` — FastAPI backend: Knowledge Graph, Adaptive Engine, Repair Engine, Personalization Engine, Readiness Scoring, LLM conversation integration. Owner: Ankit.
- `/android` — Android (Kotlin/Compose) app: intake, scenario, conversation, readiness dashboard. Owner: Sakshi.
- `/docs` — API contract, feedback log, challenges log.

## Scope (Review 3)

MVP: Spanish, Trip + Casual purposes only. Exam/Relocation and ML-trained repair selection are planned for the final sprint.

## Setup

**Backend**
```
cd backend
pip install -r requirements.txt
```
Create `backend/.env` (gitignored, never commit it) with:
```
OPENROUTER_API_KEY=your-key-here
OPENROUTER_MODEL=nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free
```
Get a free key at openrouter.ai. Without it, `/conversation` still works but returns a `[offline] ...` placeholder reply instead of a real one.
```
uvicorn app.main:app --reload
```

**Android**
Open `/android` in Android Studio, sync Gradle, run on emulator/device.

## Contract

See `docs/api-contract.md` — frozen shape both sides build against. Update it, don't diverge from it silently.
