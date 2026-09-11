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
