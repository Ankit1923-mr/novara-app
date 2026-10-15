# NOVARA API Contract

**FROZEN as of 2026-09-12.** This is the real, verified-against-production shape of all 5 endpoints — not an aspirational draft. Sakshi should build Android against exactly what's documented here, no assumptions beyond it.

Scope: Spanish, Trip + Casual purposes only. Backend is fully implemented, deployed, tested (97 tests), and hardened (auth, rate limiting, input validation) — it will not be casually changed from here on.

**If a field genuinely must change after this point**: Ankit updates this file, bumps the note below with the date and what changed, and tells Sakshi directly before touching the deployed API — never a silent shape change. A breaking change to something Sakshi has already built against is the one thing that should stop her mid-task, so it needs an explicit heads-up, not a git commit she happens to notice.

*No changes since freeze.*

**BREAKING CHANGE — 2026-10-14, authentication requirement added.** Every endpoint that reads or writes learner-specific data (`/profile`, `/scenario`, `/conversation`, `/readiness`, `/me`, `/topics`, `/topics/{topic_id}/submit`, `/profile/pace`, `/conversation/history`) now requires, **in addition to** the existing `X-API-Key` header, an `Authorization: Bearer <jwt>` header whose token's email matches the request's `learner_id`. This was not true before this date — those endpoints previously accepted any `learner_id` with just the shared API key. See "Authentication" below. The new `/conversation/history` endpoint is documented below. Total test count including Phase 1's changes: 178, all passing.

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

Also present, added after the freeze (additive, same backward-compatible status as `response_time_ms` below): `"selection_scores"` — an array of per-candidate-situation score breakdowns from the Adaptive Engine (`situation_tag`, `interest_overlap`, `weak_area_overlap`, `novelty_bonus`, `score`). Omit it in client code that doesn't need it; it will still be present in every real response.

Also additive: `"vocabulary"` — every Knowledge Graph phrase matching the chosen situation, each as `{"phrase": "...", "meaning": "...", "cultural_note": "..."}` (same shape as the existing `VocabItem` used by `/topics/{topic_id}`). This is the actual teaching material for the scenario — translations and cultural context for the phrases the learner will need, not just the single `opening_line`. Reused Knowledge Graph content only; nothing in it is AI-generated at request time.

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

Also present on every response, added after the freeze (additive, same backward-compatible status as `response_time_ms` above): `"pace_changed"` (bool), `"pace_change_reason"` (string or null), `"pace_preference"` (number or null) — surfaces automatic pace adjustment so a client can show when and why pace moved, instead of only picking it up on the next `/me` poll.

---

## 3a. GET /conversation/history

Additive, not part of the frozen 5 — returns a learner's prior turns for one scenario (ordered oldest-first), backed by real persistence (survives a backend restart; previously nothing was persisted here at all).

**Request**: `GET /conversation/history?learner_id={id}&scenario_id={id}`

**Response — 200**
```json
{
  "learner_id": "string",
  "scenario_id": "string",
  "turns": [
    {"role": "assistant", "content": "Hola, ¿qué le pongo?"},
    {"role": "user", "content": "Quiero un café."},
    {"role": "assistant", "content": "¿Para aquí o para llevar?"}
  ]
}
```

Requires authentication (see below) — `learner_id` must match the bearer token's email. Returns 404 if `learner_id` has no profile yet; returns an empty `turns` list (not 404) if the learner/scenario pair exists but has no conversation yet.

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
  },
  "next_step": "Slow down and re-read the AI partner's replies carefully - several recent turns needed a correction."
}
```

Also present (additive): `"next_step"` — a short, actionable recommendation naming the learner's weakest breakdown dimension in plain language (e.g. the example above points at `repair_success_rate`, the lowest score). With zero turns so far, it instead reads "Start a conversation to get your first readiness reading...". Reuses only the existing breakdown/weights data — no new scoring, no invented claim about real-world transfer.

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

**Added 2026-10-14 — per-learner ownership.** Possession of the shared API key is app-level only; it is never sufficient proof of *which learner* a request is for. Every endpoint that reads or writes learner-specific data additionally requires:

```
Authorization: Bearer <jwt>
```

obtained from `POST /auth/signup` or `POST /auth/login` (`token` field in the response). The token's email must equal the request's `learner_id` (query param or body field, depending on the endpoint) — this is the same convention `has_profile`/`learner_id` in `AuthResponse` already documents (`learner_id == email`).

- Missing or malformed `Authorization` header, or an expired/invalid token → `401` (`{"detail": "Missing bearer token"}` or `{"detail": "Invalid or expired session token"}`).
- Valid token, but its email doesn't match the request's `learner_id` → `403` (`{"detail": "Not authorized for this learner_id"}`). This is the **same response** whether that `learner_id` belongs to someone else or doesn't exist at all — the error deliberately never discloses which.
- Endpoints requiring this: `/profile`, `/scenario`, `/conversation`, `/readiness`, `/me`, `/topics`, `/topics/{topic_id}/submit`, `/profile/pace`, `/conversation/history`.
- Endpoints NOT requiring this (no learner-specific data): `GET /`, `POST /auth/signup`, `POST /auth/login`, `POST /repair`, `GET /topics/{topic_id}`.

**Client impact**: Android and the web frontends must obtain and attach a bearer token for every one of the endpoints listed above — a request with only a valid `X-API-Key` and no bearer token (or a mismatched one) now fails where it previously succeeded. There is no shared-key bypass for this check.

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
