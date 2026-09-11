# NOVARA API Contract

Frozen for Review 3 scope: **Spanish, Trip + Casual purposes only.**
Owner: Ankit (implements for real in `/backend`). Sakshi mocks these shapes in Android until backend is live, then swaps to real calls (Task 7, both sides).

If a field needs to change: whoever needs it updates this file + pings the other in the group chat before changing code. Never change a shape silently.

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

## Error format (all endpoints)

```json
{
  "error": "string, human-readable",
  "code": "string, e.g. LEARNER_NOT_FOUND"
}
```
HTTP status: 400 (bad input) / 404 (not found) / 500 (server error).

---

## Base URL

- Local dev: `http://localhost:8000`
- Deployed: `https://novara-api-dnhc.onrender.com` — verified live end-to-end (health, profile, scenario, conversation, readiness) on 2026-09-11.

Note: free-tier Render spins down after 15 min of inactivity — the first request after idle takes ~30-50s to wake up. Not a bug, expected on the free plan.

Android should read base URL from a build config value, not hardcode it, so switching local→deployed is a one-line change.
