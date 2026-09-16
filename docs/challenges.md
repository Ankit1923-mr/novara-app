# Challenges & Technical Refinement Log

Log every real problem you hit and what you did about it — this is what rubric line 5 ("problem-solving and technical refinement") is graded on. Add an entry as it happens, not retroactively.

## Format
```
### [Date] Short title
**Problem**: what broke or underperformed
**Cause**: root cause if known
**Fix**: what you changed
**Result**: metric before → after, if applicable
```

## Log

### [2026-09-11] Knowledge Graph representation: flat list vs. graph library

**Decision (not a bug)**: Implemented the Language–Culture–Life Knowledge Graph as a flat list of tagged node dicts, queried by purpose/situation_tag/region, instead of a graph library (NetworkX/Neo4j).

**Why it came up**: The module is named "Knowledge Graph" in the architecture, which creates pressure to use an actual graph library/data structure to look technically substantial.

**Reasoning**: At MVP scale (35 nodes, Trip+Casual only), filtering is O(n) regardless of representation — a graph library adds a dependency and query-language overhead with no functional benefit yet. Edges are still present, just implicit: two nodes sharing a `situation_tags` value are connected by that situation, and `get_subgraph`/`list_situations` traverse exactly that relationship.

**Result**: Kept the flat-list implementation. Interface (`get_subgraph`, `list_situations`, `get_node`) is graph-library-agnostic, so swapping to NetworkX or Neo4j later — if scenario complexity grows past MVP — is a drop-in change, not a rewrite.

### [2026-09-11] LLM provider: Anthropic → OpenRouter (budget constraint)

**Problem**: The AI Conversation Partner (task 3) was built against the Anthropic API, which has no persistent free tier — real testing needed a funded API key (~$5 minimum), which wasn't viable on a student budget.

**Cause**: Provider choice, not a code bug. Anthropic requires billing for any real usage.

**Fix**: Rewrote `llm_client.py` to call OpenRouter's OpenAI-compatible API instead, using a free-tier model. Two issues surfaced during the swap:
1. First-choice free model (`meta-llama/llama-3.1-8b-instruct:free`) was retired — OpenRouter returned 404 with a message pointing to the paid slug.
2. Second choice (`google/gemma-4-31b-it:free`) returned 429 — free-tier models sit behind a shared rate-limited pool and can be temporarily unavailable under load.

Tested several free models directly against the API before picking one; landed on `nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free`, which held up under a real in-character Spanish conversation test (stayed in scenario, correct register, short natural reply).

**Result**: `/conversation` now runs on a genuinely free model. Documented risk: free-tier models can be retired or rate-limited without notice — `OPENROUTER_MODEL` is a swappable env var for exactly this reason, and the fallback response (`[offline] ...`) means the app degrades gracefully instead of crashing if the model goes down mid-demo. Also fixed a test-isolation bug this swap surfaced: the module-level `_client` cache in `llm_client.py` persisted a real client across tests in the same pytest run, making the "no API key" fallback test order-dependent — fixed by resetting `_client` to `None` in the relevant test setup.

### [2026-09-11] Free-tier rate limiting: retry + model fallback chain

**Problem**: OpenRouter's free models run on a shared capacity pool donated by the underlying provider (Google, NVIDIA, etc.), not a per-key allocation. Demand from *other* OpenRouter users can 429 our requests regardless of our own usage — unpredictable, and a real risk of failing mid-demo.

**Cause**: Structural to free-tier hosting, not something we can fix by using our key "correctly."

**Fix**: `call_llm` now retries once (short backoff) on 429 before giving up on a model, then falls through an ordered chain of 9 free chat-capable models (excluded embedding/rerank/content-safety-classifier models from OpenRouter's free list — wrong tool for conversation generation). Only returns the offline placeholder if every model in the chain fails.

**Result**: 8 new tests using a scripted fake client (rate-limit-then-succeed, exhaust-retries-then-fallback, not-found-skips-without-retry, all-models-fail) — 40/40 passing. Demo-day risk reduced from "one model 429s → app breaks" to "all 9 models 429 simultaneously → app breaks," which is a much smaller probability.

### [2026-09-11] Backup API key + hard never-pay-for-a-model guard

**Problem**: A single OpenRouter key can itself die (exhausted account, revoked) independent of any individual model's rate limit — the model-fallback chain alone doesn't cover that. Separately, a retired free model's 404 error message from OpenRouter points at the *paid* version of that model as the "fix" — an unguarded fallback could silently start sending requests to a paid slug.

**Fix**:
1. Added a second (backup) OpenRouter key. `call_llm` now tries the full model-fallback chain under the primary key; only if every model fails under it does it retry the same chain under the backup key. An auth/permission error on a key short-circuits immediately to the next key rather than wasting retries on a dead key.
2. Added `assert_free_model()` — every entry in `FALLBACK_MODELS` is checked to end in `":free"` at **import time** (fails loudly if violated), and `_model_chain()` silently drops any `OPENROUTER_MODEL` override that isn't a free slug rather than ever sending it. This is enforced in code, not just by not-configuring a paid model — a mistake (or OpenRouter's own error message pointing at a paid slug) can't cause a real charge.

**Result**: 12 new tests (free-model-guard unit tests, backup-key-on-exhaustion, backup-key-on-auth-failure, both-keys-exhausted) — 48/48 passing. Both API keys are stored only in `backend/.env` (gitignored), never committed — confirmed via `git diff | grep` before every commit in this session.

### [2026-09-11] Repair Engine v1: classifier accuracy and a marker-priority conflict

**Problem**: First pass at the rule-based error classifier (`classify_error`) scored 65% on a 20-utterance hand-labeled test set — below the 70% Review 3 baseline. Two root causes:

1. **Lexical-gap detection was too coarse.** Raw word-overlap ratio (all words, including articles/prepositions) made "Quiero un té" vs. "Quiero un café" look 80% similar, because 4 of 5 words are shared function words — so a clear vocabulary error was misclassified as `grammar`.
2. **Register detection relied only on slang/formality markers** ("usted", "guay"), missing the more common case of a plain tú-form verb where an usted-form was expected (e.g. "Tienes una mesa para dos?" vs. expected "¿Tiene una mesa para dos?") — no slang word to catch, so it fell through to `grammar`.

**Fix**: (1) Switched lexical detection to a stopword-filtered *content-word* overlap ratio — articles/prepositions/"por favor" etc. no longer count toward the similarity score, so a swapped noun/verb shows up clearly. (2) Added a small tú→usted verb-conjugation pair table (tienes/tiene, puedes/puede, eres/es, etc.) checked directly against the expected pattern, independent of slang markers.

A third issue surfaced after that: a comprehension marker ("repetir") legitimately appears inside a well-formed but wrong-register request ("Puedes repetir por favor"), and the comprehension check ran first, misclassifying it. **Fix**: reordered the checks so the more specific, structural register signal (verb-pair match) runs before the generic keyword-based comprehension check.

**Result**: 100% on the 20-sample set after tuning — reported conservatively as "meets the ≥70% baseline," not as a generalization claim, since the set was iterated against while tuning (documented directly in `tests/test_repair_engine.py`'s docstring to avoid overstating this in the report). Real learner input (typos, mixed errors, code-switching) will be messier than these clean single-error examples.

### [2026-09-11] Personalization Engine: contract change for response timing

**Problem**: The Personalization Engine's pace score needs how long a learner took to respond, but the frozen `/conversation` request contract (`docs/api-contract.md`) has no timing field — it wasn't anticipated when the contract was written on Day 0.

**Decision**: Added `response_time_ms` as an **optional** field on `ConversationRequest` (defaults to `None`) rather than reopening the contract as a breaking change. Android can omit it entirely and everything still works — `update_scores()` explicitly leaves `pace_score` unchanged when `response_time_ms` is `None`, rather than nudging it toward a meaningless default. Confidence score doesn't depend on timing at all (driven by whether repair was triggered), so it updates every turn regardless.

**Why this matters for the report**: it's a real example of a contract evolving after Day 0 without breaking the other side's already-built code — handled by making the new field optional and documenting the same-turn fallback behavior, not by requiring Sakshi to change anything on the Android side before this could ship.

**Result**: 10 new personalization_engine tests including the specified baseline check (10 simulated interactions with varying response time/correctness, verifying scores move in the expected direction after each one) + 3 new endpoint tests. 71/71 passing.

### [2026-09-11] Readiness Scoring Engine: defining "transfer" without a dedicated transfer-test flow

**Problem**: The architecture defines `transfer_success` as one of four readiness dimensions ("performs in unfamiliar situations"), but there's no dedicated transfer-test flow built yet (learn-in-one-scenario, test-in-a-new-one) — that's future work beyond Review 3 scope. Needed a real, honestly-labeled proxy from data we actually have, not a hardcoded placeholder.

**Decision**: Defined `transfer_success` as situation **breadth** — the fraction of a purpose's known situations (café, hotel, transport, etc., from the Knowledge Graph's `list_situations()`) the learner has actually practiced (`recently_seen`, already tracked by the Adaptive Engine), rather than repeating one scenario. Reasoning: genuine transfer can't be measured without a real transfer-test flow, but breadth across situations is a legitimate, defensible precursor to it — a learner who's only ever done the café scenario clearly hasn't demonstrated transfer, whether or not we can test it directly yet.

**Also decided**: purpose-specific weights, not a single fixed formula (the rubric's "novel component" requirement) — Trip purpose weights `transfer_success` heaviest (a traveler needs breadth across many unplanned situations, register mistakes are more forgivable), Casual purpose weights `register_appropriateness` heaviest (sounding socially natural with recurring peers matters more than covering many disconnected situations). Documented directly in `readiness_engine.py`'s comments so the reasoning doesn't need to be reconstructed later for the report.

**Result**: 10 new readiness_engine tests including the baseline check (3 synthetic high/mid/low-performing learners rank correctly, tested for both purposes) + 3 new endpoint tests verifying real repair history moves the score. All 6 backend modules are now wired to real logic. 83/83 passing.

### [2026-09-12] Postgres persistence: connection string and test isolation

**Problem**: All learner/scenario data lived in in-memory Python dicts (`LEARNERS`, `SCENARIOS` in `main.py`) — wiped on every server restart or Render redeploy. Needed real persistence via Supabase (Postgres).

Two connection issues before it worked:
1. Supabase's direct-connection hostname (`db.<ref>.supabase.co`) didn't resolve — it requires IPv6, which isn't available on this network (or on Render). Fixed by using Supabase's **transaction-mode pooler** hostname instead (`aws-0-<region>.pooler.supabase.com:6543`), which supports IPv4. Username also changes shape for the pooler: `postgres.<project-ref>` instead of plain `postgres`.
2. The database password contained special characters (`#`, `*`, `&`) that broke the connection URI until percent-encoded (`%23`, `%2A`, `%26`).

**Decision**: Built the DB layer (`db.py`, `db_models.py`) with a lazily-constructed engine (same pattern as `llm_client.py`'s lazy client) so the test suite could override `DATABASE_URL` to an in-memory SQLite database via a `conftest.py` fixture — tests never touch the real Supabase instance or require network access. SQLite's `:memory:` mode needed an explicit `StaticPool` in SQLAlchemy, since without it every new connection (one per request) gets its own blank database and nothing persists across requests within a single test run.

**Result**: All 6 endpoints migrated from dict access to SQLAlchemy ORM queries (`LearnerModel`, `ScenarioModel`). Verified against the real Supabase instance with a direct psycopg2 query proving the row persisted independently of the running process (not just readable within the same request). 83/83 tests still passing on the SQLite fallback, unchanged in behavior. `DATABASE_URL` stored only in `backend/.env` (gitignored) — same handling as the API keys.

### [2026-09-12] Demo-safety hardening: auth, rate limiting, input validation

**Context**: Ran the remaining-work prioritization through an LLM council (5 independent advisor perspectives + peer review + synthesis) rather than deciding solo. All 5 advisors and all 5 peer reviews independently converged on the same conclusion: skip the ML-trained repair engine and expanded purposes (scope creep with no demo-visible payoff), and treat auth + rate limiting + edge-case hardening as non-negotiable, because the live backend was public with zero protection — a real risk of the free-tier LLM quota being burned or the demo breaking mid-review.

**What was built**:
1. **API key auth** (`auth.py`) — every endpoint except the health check requires an `X-API-Key` header matching a shared secret. Deliberately simple (one static key, not per-user auth) — this project doesn't need real user accounts, it needs to stop a stranger who finds the URL from writing junk data or draining the LLM quota.
2. **Rate limiting** (`slowapi`) — 20/minute on `/conversation` specifically (the LLM-costly endpoint), 100/minute default on everything else, keyed per-IP.
3. **Input validation hardening** (`models.py`) — length limits on all string/list fields, positive-only `turn_number`, non-negative `response_time_ms`. Plus a global exception handler so any unhandled bug returns a clean `{error, code}` JSON response instead of a raw Python traceback leaking file paths and internals to a client.

**Bug found while testing rate limiting**: FastAPI's `TestClient` reports the same fake IP ("testclient") for every request, so all tests hitting `/conversation` shared one rate-limit bucket — an early test exhausting the 20/minute quota caused unrelated *later* tests to fail with 429s they had nothing to do with. Fixed with an autouse `conftest.py` fixture that resets the limiter's storage before every test.

**Second bug while testing the exception handler**: `TestClient` re-raises server exceptions by default (`raise_server_exceptions=True`) rather than returning the response a real client would see — useful for catching bugs in most tests, but it meant the first attempt at testing the 500 handler saw the raw exception instead of the clean JSON response. Fixed by using a second `TestClient` instance with `raise_server_exceptions=False` for that specific test.

**Result**: 14 new tests (3 auth, 1 rate-limit, 9 validation, 1 exception-handler) — 97/97 passing. Verified against the real Supabase-backed local server: request without a key correctly returns 401, request with the key returns 200.

### [2026-09-12] Independent test suite (GPT/Codex) found 7 real implementation bugs

Ran an externally-generated pytest suite (663 cases, built from a detailed backend spec handed to another LLM) against the actual backend. It found genuine defects the existing 97-test suite didn't cover — none of these were spec disagreements, all were real behavioral bugs:

1. **Scenario ID collision across purposes** — `scenario_id` was built as `f"scenario-{tag}"`, so Trip and Casual could both generate `scenario-food` and silently overwrite each other's row (it's the DB primary key). Fix: include purpose in the ID (`f"scenario-{purpose}-{tag}"`) — this is a genuine identity bug, not just a naming nitpick, since the scenario a *different* learner is mid-conversation in could be mutated.
2. **Lost updates under concurrent `/conversation` calls** — two simultaneous requests for the same learner could both read `total_turns`/`confidence_score`, compute independently, and the second commit would silently overwrite the first's work instead of both being counted. Root cause: classic read-modify-write race, no isolation. Fixed with a combination of (a) an atomic SQL `UPDATE ... SET col = f(col)` expression for `total_turns`/`pace_score`/`confidence_score` — the database, not Python, performs the read-and-write in one atomic step per row — and (b) SQLAlchemy optimistic locking (`version_id_col`) with a retry loop for `repair_counts` (a JSON merge, not expressible as a single portable SQL arithmetic expression). Took three attempts to get right: a coarse per-learner lock deadlocked against the test's own synchronization barrier; a full bypass of the Personalization Engine's `update_scores()` call broke the test's expectation that this hook still exists and fires. The final design keeps calling `update_scores()` once (preserving the documented architecture and the test's monkeypatch point) but persists via the atomic SQL path rather than trusting that call's return value, which can go stale by write time.
3. **Provider interruptions became application 500s instead of the offline fallback** — `llm_client.py` only caught `RateLimitError`, `NotFoundError`, `AuthenticationError`, `PermissionDeniedError`; a timeout, connection error, or 500/503 from the LLM provider propagated uncaught, turning "the AI is temporarily down" into "our backend is broken." Fixed by widening the caught exception set and adding response-content validation (empty/null/non-string replies now count as a failure and fall through the model chain, instead of being returned as-is or crashing on `response.choices[0]`).
4. **Raw provider error text could reach the client** — the offline fallback message interpolated `str(last_error)` directly, which could echo back internal exception details. Replaced with a fixed generic message.
5. **Profile reset didn't clear conversation history** — calling `/profile` again for an existing `learner_id` reset every SQL field but left old dialogue sitting in the in-memory `HISTORY` dict, which would still be sent to the LLM as prior context on the learner's next turn under their *new* profile. Fixed with `reset_all_history_for_learner()`, called from `/profile`.
6. **A failed request could still pollute in-memory history** — `handle_turn()` appends to `HISTORY` before the request's DB work is attempted; if something after that point failed (DB commit error, a downstream engine bug), the DB rolled back correctly but the in-memory turn stayed, diverging from persisted state. Fixed by recording history length before the turn and truncating back to it in an `except` block around the whole handler.

**Result**: re-ran the external suite's core groups (contract, engine invariants, local integrity, process restart, provider resilience — 542 cases) after fixes: all passing. Our own 97-test suite: still 97/97, unaffected by the changes. Not addressed (deliberately, per the report's own framing): `test_quality.py` (repair classifier precision/recall on adversarial examples — already documented as a known heuristic limitation) and `test_specification_gaps.py` (32 *proposed* behaviors the frozen contract never promised, e.g. rejecting unknown JSON fields, enforcing a language enum — logged as future-work candidates, not regressions).

### [2026-09-12] Postgres round() type mismatch, caught during production deploy verification

**Problem**: The atomic SQL EMA fix above passed a bare float expression to `func.round(...)`, which worked silently on SQLite (used in tests) but threw `ProgrammingError: function round(double precision, integer) does not exist` the moment it hit real Postgres — Postgres's `round()` only accepts `numeric`, not `double precision`.

**Cause**: Trusted the SQLite-passing test suite as sufficient signal, deployed, and only found this by manually re-running the exact same end-to-end smoke test against the live Render URL immediately after deploy — the two database backends' `round()` signatures genuinely differ, and no test in either suite runs against real Postgres.

**Fix**: Explicit cast to `Numeric` before rounding (`func.round(cast(expr, Numeric), 4)`). Also had to manually `ALTER TABLE learners ADD COLUMN version_id ...` on the live Supabase table, since `Base.metadata.create_all()` only creates missing tables, never adds columns to tables that already existed before a schema change.

**Lesson for the report**: this is the concrete argument for "test against the real deployment target, not just the fast local substitute" — logged here specifically because it's a good example for Chapter 4's testing-methodology section.

### [2026-09-12] Second GPT re-verification round: 3 more real bugs, found by testing interactions *between* the first round's fixes

After the first 6 fixes, the same reviewer wrote **new** adversarial tests specifically probing whether the fixes interacted badly with each other — not re-testing the original bugs, but testing the fix boundaries:

1. **History-rollback fix was itself unsafe under concurrency**: the original fix (commit history-length-before, truncate-back-to-that-length on failure) breaks the instant a *second*, concurrent, successful turn for the same learner+scenario appends to that history in between — the failing request's rollback would truncate away the sibling's legitimate turn too, since it only knows a length, not which entries were its own. Fixed by having `handle_turn()` return the exact message objects it appended, and rolling back by **object identity** (`remove_turn_from_history`), not by length. Two dicts with identical content (two different learners both saying "hola") are never confused, since identity (not value equality) decides what gets removed.
2. **A profile reset mid-flight could still corrupt the new profile**: if `/conversation` was slow (e.g. a hung LLM call) and `/profile` reset the same learner while it was still running, the slow request would finish afterward and apply its turn count/repair data onto the *new* profile — a request that started under the old purpose silently mutating the new one. Fixed by capturing the learner's purpose at the start of `/conversation` and re-checking it immediately before each database write; a mismatch means a reset happened mid-flight, so the turn's effect is discarded (the reply is still returned to the client — they got an answer — but nothing is persisted).
3. **Malformed provider responses could still crash**: `_usable_reply()` only guarded against bad `content`, not a `None` entry inside `choices`, a missing `message` key, or the OpenAI SDK itself raising while parsing an unexpected payload shape. Wrapped the entire check in a broad try/except (any failure to extract a usable reply falls through to the next model, exactly like an unusable-content case) and added a catch-all `except Exception` in the retry loop as a last-resort safety net, so a payload shape nobody anticipated still degrades gracefully instead of becoming a 500.

**Also caught along the way**: `handle_turn()` itself needed to become self-cleaning — when `call_llm` is replaced directly in a test (bypassing `llm_client`'s own broad exception handling, which normally never lets an exception escape), the exception happens *inside* `handle_turn`, before it can return the `appended` list to the caller for cleanup. Fixed by wrapping `handle_turn`'s body in its own try/except that rolls back its own partial history on any exception, so the caller's cleanup is a safety net, not the only line of defense.

**Result**: 12 new tests (`test_fix_followups.py`) — all passing. Re-ran the full external core+followup suite: 554/554 passing.

### [2026-09-12] Third re-verification round: the round-2 fixes had their own two root causes

The same reviewer went one level deeper: instead of testing new scenarios, it tested whether round 2's *fixes themselves* were actually correct under different concurrency schedules and edge cases than the ones that first caught the bugs.

1. **Purpose is not a generation.** Round 2's profile-reset guard compared `learner.purpose` before/after — but that misses a same-purpose reset (Trip → Trip) or an A→B→A sequence that lands back on the original purpose, since both leave `purpose` looking unchanged even though a real reset happened in between. Fixed by adding a dedicated `profile_generation` counter on `LearnerModel`, bumped unconditionally on every `/profile` call regardless of whether the new values differ from the old ones — this is what `/conversation`'s stale-write guard now compares, not `purpose`.

2. **The guard needs to be part of the UPDATE's WHERE clause, not a preceding SELECT.** Round 2's check was "SELECT current state, compare, then decide whether to UPDATE" — two separate operations with a race window between them: a reset could still land in that gap. Restructured so `profile_generation` (and `version_id`, replacing the earlier separate ORM-based repair_counts retry) are both baked directly into the same atomic UPDATE statement's WHERE clause, and `total_turns`/scores/`repair_counts`/version bump all happen in that single statement. The database evaluates the WHERE condition atomically as part of executing that one UPDATE — there is no gap between "check" and "write" because they're the same operation. `rowcount == 0` afterward is unambiguous: re-reading `profile_generation` distinguishes "a concurrent conversation turn already committed" (version_id moved, legitimate — retry with fresh values) from "a profile reset happened" (generation moved — discard the turn's effect entirely, still return the reply).

3. **`remove_turn_from_history` was silently dropping a sibling's successful reply.** The round-2 rollback fix (identity-based removal) rebuilt the list via `HISTORY[key] = [m for m in ... if ...]` — a **new** list object. A concurrent sibling call had already captured a reference to the *old* list object (via `HISTORY.setdefault(key, [])` inside its own `handle_turn()`), and continued appending to that reference for the rest of its execution. Once the rollback rebound `HISTORY[key]` to a different list, the sibling's subsequent appends went to a now-detached object nobody else would ever read — its successful reply was silently lost, not just delayed. Fixed by mutating the existing list **in place** (`del history[i]` by index, scanning in reverse, matched by identity) instead of ever reassigning `HISTORY[key]`. Every holder of a reference to that list object — no matter when they obtained it — sees the same object for the object's entire lifetime.

**Root-cause pattern across all three fixes in this round**: the same mistake, in three different shapes — checking or removing state through a reference/read that can go stale (or detached) before the corresponding write actually lands. The fix each time was to make the check part of the same atomic operation as the write (SQL WHERE clause), or to never replace a shared mutable reference that another party might still be holding (in-place list mutation).

**Result**: 3 new tests (`test_judge_interleavings.py`) targeting these exact interleavings, all passing. Full external suite (core + both follow-up rounds): 558/558. Added `profile_generation` column to production Supabase via the same one-off `ALTER TABLE` pattern as `version_id` earlier, verified locally against real Postgres before deploying this time (not just SQLite) — the round-1 Postgres `round()` incident made that the standing rule going forward.

### [2026-09-15] Documented gap: no pedagogical staging between Lesson and Practice

Raised during review prep: a brand-new (A1) learner can go straight from reading 4 vocab cards to a fully open-ended LLM conversation in Practice, with no intermediate step. Real second-language pedagogy stages this — recognition, then guided/constrained production, then free production — and NOVARA currently skips from the first straight to the last. The Lesson and Practice modules are sequenced on the dashboard but not pedagogically linked; nothing scaffolds Practice's difficulty down for a learner who just finished their first topic.

**Not fixed now** — flagged as documented future work, not silently ignored: the fix is a constrained-conversation stage between Lesson and Practice, where the AI partner expects (and only lightly deviates from) the exact phrases just taught, with a "suggested reply" affordance instead of requiring free typing, unlocking full free-form Practice only once that stage is passed for a topic. Requires a new Conversation Partner mode plus dashboard/gating changes — out of scope for the current review window, in scope for the next.

**Planned fix (post-review), scoped so it's ready to pick up directly:**

1. **New Conversation Partner mode** ("guided") — reuse `conversation_engine.py`'s existing `call_llm` plumbing, but constrain `build_system_prompt()`: pass in the topic's known vocab phrases and instruct the LLM to only use those, gently steering the learner back on-script if they wander rather than opening into full free dialogue. Prompt-only change, no new infra.
2. **Suggested-reply chips in the UI** — instead of a blank text input, show 2-3 tappable buttons pulled from `lesson_content.py`'s existing vocab list for that topic; learner can tap one or type their own attempt. Removes the "blank page" problem for an absolute beginner.
3. **Gating** — extend the existing `topics_completed` threshold-lock pattern (already built for the Dashboard's winding path) one level deeper: Practice (free-form) for a topic unlocks only after Guided Practice is passed for that topic. Sequence becomes Vocab → Quiz → Guided Practice → Practice.
4. **No changes needed** to Personalization or Readiness Engines — both already treat "turns" and "repairs" generically regardless of which conversation mode produced them.

Likely needs one new field (`scenario_type: "guided" | "free"`) on the scenario object and a small Dashboard/routing change — everything else is additive to existing modules, not a rewrite.
