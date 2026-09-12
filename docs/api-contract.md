# NOVARA API Contract

**FROZEN as of 2026-09-12.** This is the real, verified-against-production shape of all 5 endpoints — not an aspirational draft. Sakshi should build Android against exactly what's documented here, no assumptions beyond it.

Scope: Spanish, Trip + Casual purposes only. Backend is fully implemented, deployed, tested (97 tests), and hardened (auth, rate limiting, input validation) — it will not be casually changed from here on.

**If a field genuinely must change after this point**: Ankit updates this file, bumps the note below with the date and what changed, and tells Sakshi directly before touching the deployed API — never a silent shape change. A breaking change to something Sakshi has already built against is the one thing that should stop her mid-task, so it needs an explicit heads-up, not a git commit she happens to notice.

*No changes since freeze.*

---

## 1. POST /profile

Create/update a learner profile.

**Request**
```json
{
  "learner_id": "string",
  "language": "spanish",
  "level": "A1 | A2 | B1 | B2",
  "region": "string (e.g. Madrid)",
  "purpose": "trip | casual",
  "interests": ["food", "travel"],
  "weak_areas": ["listening"]
}
```

**Response — 200**
```json
{
  "learner_id": "string",
  "profile_created": true,
  "weakness_vector": {"listening": 1.0, "grammar": 0.0},
  "pace_score": 0.5,
  "confidence_score": 0.5
}
```

---

## 2. GET /scenario?learner_id={id}

Returns the next situational scenario for this learner.

**Response — 200**
```json
{
  "scenario_id": "string",
  "purpose": "trip | casual",
  "title": "Ordering coffee",
  "setting": "A café in Madrid",
  "situation_tags": ["food", "ordering"],
  "opening_line": "Hola, ¿qué le pongo?"
}
```

---

## 3. POST /conversation

Send a learner message, get the AI partner's reply + repair flag if triggered.

**Request**
```json
{
  "learner_id": "string",
  "scenario_id": "string",
  "message": "Quiero un café.",
  "turn_number": 3,
  "response_time_ms": 2400
}
```
`response_time_ms` is **optional** (added after the initial contract freeze, backward-compatible — omit it and the request still works). If sent, it's how long the learner took to type/speak this turn, in milliseconds; feeds the Personalization Engine's pace score. Not required for a working demo, but send it if the UI can measure it (e.g. time between scenario/reply render and message send).

**Response — 200**
```json
{
  "reply": "¿Para aquí o para llevar?",
  "repair_triggered": false,
  "repair": null
}
```

**Response when repair triggers**
```json
{
  "reply": "¿Puede repetir, por favor?",
  "repair_triggered": true,
  "repair": {
    "error_type": "lexical | grammar | register | comprehension",
    "strategy": "clarify | rephrase | hint",
    "repair_text": "Se dice 'para llevar', no 'para llevo'."
  }
}
```

---

## 4. POST /repair

(Called internally by backend, but exposed for Android's own testing/demo purposes.)

**Request**
```json
{
  "learner_utterance": "string",
  "expected_pattern": "string"
}
```

**Response — 200**
```json
{
  "error_type": "lexical | grammar | register | comprehension",
  "strategy": "clarify | rephrase | hint",
  "repair_text": "string"
}
```

---

## 5. GET /readiness?learner_id={id}

**Response — 200**
```json
{
  "learner_id": "string",
  "aggregate_score": 0.72,
  "breakdown": {
    "language_accuracy": 0.8,
    "repair_success_rate": 0.65,
    "register_appropriateness": 0.7,
    "transfer_success": 0.7
  },
  "purpose": "trip | casual",
  "weights_used": {
    "language_accuracy": 0.3,
    "repair_success_rate": 0.3,
    "register_appropriateness": 0.2,
    "transfer_success": 0.2
  }
}
```

---

## Error format

The shape differs by status code — check `error`/`detail` presence, don't assume one uniform format across all of them:

**401 (missing/invalid API key) and 404 (not found)**
```json
{ "detail": "human-readable message" }
```

**422 (validation failed — empty/too-long field, wrong type, missing required field)**
```json
{
  "detail": [
    { "type": "string_too_short", "loc": ["body", "learner_id"], "msg": "...", "input": "", "ctx": {...} }
  ]
}
```
`detail` is a list — a request can fail multiple field validations at once. For a simple UI error message, `detail[0].msg` is usually enough; don't try to show the raw `loc`/`ctx` to the learner.

**429 (rate limit exceeded)**
```json
{ "error": "Rate limit exceeded: 20 per 1 minute" }
```

**500 (unhandled server error)**
```json
{ "error": "An unexpected error occurred.", "code": "INTERNAL_ERROR" }
```

Practical takeaway for Android: check the HTTP status code first, then read `detail` for 401/404/422 or `error` for 429/500 — don't parse assuming both fields always exist.

## Authentication

Every endpoint except `GET /` requires an `X-API-Key` header matching the shared key (distributed separately, never committed — see backend/.env). A missing or wrong key returns 401.

## Rate limits

`POST /conversation`: 20 requests/minute (protects the free-tier LLM quota). All other endpoints: 100 requests/minute (shared default). Exceeding either returns 429.

## Field limits (validation, 422 if violated)

- `learner_id`, `scenario_id`: 1–100 characters
- `message`, `learner_utterance`, `expected_pattern`: 1–1000 characters
- `region`: 1–200 characters
- `interests`, `weak_areas`: max 20 items each
- `turn_number`: must be > 0
- `response_time_ms`: must be ≥ 0 if provided

---

## Base URL

- Local dev: `http://localhost:8000`
- Deployed: `https://novara-api-dnhc.onrender.com` — verified live end-to-end (health, profile, scenario, conversation, readiness) on 2026-09-11.

Note: free-tier Render spins down after 15 min of inactivity — the first request after idle takes ~30-50s to wake up. Not a bug, expected on the free plan.

Android should read base URL from a build config value, not hardcode it, so switching local→deployed is a one-line change.

---

## Quick reference for Android integration

**Every request** (except `GET /`) needs this header:
```
X-API-Key: <shared key, sent to you separately — never commit it into the repo>
```

**Typical session flow** (call in this order):
1. `POST /profile` once per learner (or when they change purpose/level/interests)
2. `GET /scenario?learner_id=...` to get the next situation
3. `POST /conversation` for each learner message in that scenario (loop this)
4. `GET /readiness?learner_id=...` whenever you want to show their current score (dashboard, end of session, etc.)

**Things you will genuinely see in testing — not bugs:**
- A `/conversation` reply starting with `"[offline] ..."` — means the backend's LLM provider (OpenRouter, free tier) is temporarily rate-limited or misconfigured server-side. Render it as the AI's message like any other reply; don't treat it as an error state in the UI. If you see it constantly (not occasionally), tell Ankit.
- The **first** request after the backend has been idle can take 30-50 seconds (Render free tier cold start). Show a loading state, don't assume it's hung.
- `repair` in the `/conversation` response is `null` most of the time — that's correct. It only populates when `repair_triggered` is `true`.

**Test with your own `learner_id` values** (e.g. `"sakshi_test_1"`), not `"u1"` — that one has accumulated test history from backend development and its readiness numbers won't mean anything clean for you to demo against.
