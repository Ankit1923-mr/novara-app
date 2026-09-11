"""
Thin wrapper around OpenRouter (OpenAI-compatible API) for the AI
Conversation Partner.

Kept separate from conversation_engine.py so the network call is the one
thing mocked in tests - everything else (prompt construction, history,
turn handling) is tested against real logic, not a stubbed LLM.

Requires OPENROUTER_API_KEY in the environment (loaded from backend/.env
via python-dotenv - see .env, gitignored, never committed). Without a
key, falls back to a clearly-labeled canned reply so local dev and CI
don't need a live key to run the rest of the stack.

Free-tier models sit behind a shared, unpredictable rate-limited pool
(not a per-key limit - see docs/challenges.md). To survive that:
retry each model once with backoff on 429, then fall through to the
next model in FALLBACK_MODELS. Chain only includes text/chat-capable
models - embedding, rerank, and content-safety-classifier models from
OpenRouter's free list are excluded, they don't generate conversation.
"""

import os
import time
from typing import Optional
from dotenv import load_dotenv

load_dotenv()

# Ordered by fit for in-character conversational Spanish + general capability.
# First verified live against a real scenario prompt; rest are untested
# fallbacks - if one underperforms in practice, reorder or drop it.
FALLBACK_MODELS = [
    "nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free",  # verified: stayed in character, correct register
    "google/gemma-4-31b-it:free",
    "google/gemma-4-26b-a4b-it:free",
    "thinkingmachines/inkling:free",
    "nvidia/nemotron-3-super:free",
    "nvidia/nemotron-3-ultra-550b-a55b:free",
    "cohere/north-mini-code:free",
    "poolside/laguna-s-2.1:free",
    "poolside/laguna-xs-2.1:free",
]

MAX_TOKENS = 200
RETRIES_PER_MODEL = 1  # additional attempts after the first, per model
BACKOFF_SECONDS = 1.5

_client = None


def _get_client():
    """Lazily construct the OpenRouter client so importing this module
    never requires a key (needed for tests that only check the fallback
    path, and so /docs and other endpoints work without one)."""
    global _client
    if _client is not None:
        return _client
    api_key = os.environ.get("OPENROUTER_API_KEY")
    if not api_key:
        return None
    import openai
    _client = openai.OpenAI(base_url="https://openrouter.ai/api/v1", api_key=api_key)
    return _client


def _model_chain() -> list[str]:
    """OPENROUTER_MODEL (if set) is tried first, then the rest of
    FALLBACK_MODELS, de-duplicated, preserving order."""
    preferred = os.environ.get("OPENROUTER_MODEL")
    chain = ([preferred] if preferred else []) + FALLBACK_MODELS
    seen = set()
    ordered = []
    for m in chain:
        if m not in seen:
            seen.add(m)
            ordered.append(m)
    return ordered


def call_llm(system_prompt: str, messages: list[dict], client: Optional[object] = None) -> str:
    """
    messages: [{"role": "user" | "assistant", "content": "..."}]
    client: injected for tests; defaults to the lazily-built OpenRouter client.

    Tries each model in the fallback chain; within a model, retries once
    on 429 with a short backoff before moving to the next model. Returns
    the offline placeholder only if every model in the chain fails.
    """
    active_client = client if client is not None else _get_client()

    if active_client is None:
        return "[offline] OPENROUTER_API_KEY not set — set it in backend/.env to get real replies."

    import openai  # local import: keeps module importable without the package during fallback-only tests

    chat_messages = [{"role": "system", "content": system_prompt}] + messages
    last_error: Optional[Exception] = None

    for model in _model_chain():
        for attempt in range(RETRIES_PER_MODEL + 1):
            try:
                response = active_client.chat.completions.create(
                    model=model,
                    max_tokens=MAX_TOKENS,
                    messages=chat_messages,
                )
                return response.choices[0].message.content
            except openai.RateLimitError as e:
                last_error = e
                if attempt < RETRIES_PER_MODEL:
                    time.sleep(BACKOFF_SECONDS)
                # else: exhausted retries on this model, fall through to next model
            except openai.NotFoundError as e:
                # model retired/unavailable - no point retrying, move to next model
                last_error = e
                break

    return f"[offline] all fallback models unavailable ({last_error}) — try again shortly."
