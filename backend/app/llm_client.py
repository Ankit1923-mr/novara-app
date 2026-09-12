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

HARD RULE: this module must never call a paid model. Every model in
FALLBACK_MODELS is asserted to be a ":free" slug at import time, and
any OPENROUTER_MODEL override that doesn't end in ":free" is dropped
(never sent) rather than silently allowed through - see
assert_free_model() and its use in _model_chain(). This is enforced in
code, not just by convention, because a retired free model can 404 and
OpenRouter's error message points at the *paid* slug as the fix - that
paid slug must never be auto-substituted.

Free-tier models sit behind a shared, unpredictable rate-limited pool
(not a per-key limit - see docs/challenges.md). To survive that:
retry each model once with backoff on 429, then fall through to the
next model in FALLBACK_MODELS. If OPENROUTER_API_KEY_BACKUP is set,
the whole model chain is retried under the backup key if it fails
entirely under the primary key (covers a dead/exhausted primary key,
not just a rate-limited model).
"""

import os
import time
from typing import Optional
from dotenv import load_dotenv

load_dotenv()

# Ordered by fit for in-character conversational Spanish + general capability.
# First verified live against a real scenario prompt; rest are untested
# fallbacks - if one underperforms in practice, reorder or drop it.
# Every entry MUST end in ":free" - enforced by assert_free_model() below.
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

_clients: Optional[list[tuple[str, object]]] = None


def assert_free_model(model: str) -> bool:
    """Returns True if `model` is safe to call under the never-pay rule.
    A model string that doesn't end in ':free' is a paid (or ambiguous)
    slug and must never be sent to the API from this module."""
    return model.endswith(":free")


# Fail loudly at import time if anyone edits FALLBACK_MODELS to include
# a non-free slug - catches the mistake before it can ever place a call.
for _m in FALLBACK_MODELS:
    assert assert_free_model(_m), f"FALLBACK_MODELS contains a non-free model: {_m!r}"


def _build_client(api_key: str):
    import openai
    return openai.OpenAI(base_url="https://openrouter.ai/api/v1", api_key=api_key)


def _get_clients() -> list[tuple[str, object]]:
    """Lazily construct (label, client) pairs for the primary and, if
    set, backup OpenRouter keys. Lazy + cached so importing this module
    never requires a key, and so tests can reset it via monkeypatch."""
    global _clients
    if _clients is not None:
        return _clients

    clients: list[tuple[str, object]] = []
    primary_key = os.environ.get("OPENROUTER_API_KEY")
    if primary_key:
        clients.append(("primary", _build_client(primary_key)))
    backup_key = os.environ.get("OPENROUTER_API_KEY_BACKUP")
    if backup_key:
        clients.append(("backup", _build_client(backup_key)))

    _clients = clients
    return clients


def _model_chain() -> list[str]:
    """OPENROUTER_MODEL (if set AND free) is tried first, then the rest
    of FALLBACK_MODELS, de-duplicated, preserving order. A non-free
    OPENROUTER_MODEL override is dropped, never sent."""
    preferred = os.environ.get("OPENROUTER_MODEL")
    candidates = ([preferred] if preferred else []) + FALLBACK_MODELS

    seen = set()
    ordered = []
    for m in candidates:
        if m in seen:
            continue
        if not assert_free_model(m):
            continue  # silently drop - never call a paid model, even if configured
        seen.add(m)
        ordered.append(m)
    return ordered


def _usable_reply(response) -> Optional[str]:
    """Validates a provider response actually has something worth
    returning. A malformed response (empty choices, null/empty/
    non-string content) must be treated as a failure and fall through
    to the next model/retry, not returned as-is or allowed to crash
    with an IndexError/AttributeError."""
    if not response.choices:
        return None
    content = response.choices[0].message.content
    if not isinstance(content, str) or not content.strip():
        return None
    return content


def _try_model_chain(client, chat_messages: list[dict]) -> tuple[Optional[str], Optional[Exception]]:
    """Runs the full model chain (with per-model retry) against one
    client. Returns (reply, None) on success or (None, last_error) if
    every model failed under this client."""
    import openai

    # Transient failures - the provider is temporarily unreachable/overloaded/
    # rate-limited, not permanently wrong. Worth a retry, then move on to
    # the next model in the chain rather than propagating and turning into
    # our own 500 - a provider hiccup should degrade to the offline
    # fallback, never an application error.
    TRANSIENT_ERRORS = (
        openai.RateLimitError,
        openai.APITimeoutError,
        openai.APIConnectionError,
        openai.InternalServerError,
    )

    last_error: Optional[Exception] = None
    for model in _model_chain():
        for attempt in range(RETRIES_PER_MODEL + 1):
            try:
                response = client.chat.completions.create(
                    model=model,
                    max_tokens=MAX_TOKENS,
                    messages=chat_messages,
                )
                reply = _usable_reply(response)
                if reply is not None:
                    return reply, None
                # malformed content from an otherwise-successful call - treat
                # like a transient failure rather than crashing or echoing junk
                last_error = ValueError(f"unusable response content from model {model}")
                break
            except TRANSIENT_ERRORS as e:
                last_error = e
                if attempt < RETRIES_PER_MODEL:
                    time.sleep(BACKOFF_SECONDS)
            except openai.NotFoundError as e:
                # model retired/unavailable - no point retrying, move to next model
                last_error = e
                break
            except (openai.AuthenticationError, openai.PermissionDeniedError) as e:
                # this key itself is bad/exhausted - stop trying models under
                # it, let the caller move to the next key
                return None, e
    return None, last_error


def call_llm(system_prompt: str, messages: list[dict], client: Optional[object] = None) -> str:
    """
    messages: [{"role": "user" | "assistant", "content": "..."}]
    client: injected for tests; defaults to the lazily-built OpenRouter client(s).

    Tries the full model fallback chain under the primary key; if that
    key fails entirely (auth/exhausted, or every model rate-limited),
    retries the same chain under the backup key if one is configured.
    Returns the offline placeholder only if everything fails.
    """
    if client is not None:
        reply, _ = _try_model_chain(client, [{"role": "system", "content": system_prompt}] + messages)
        if reply is not None:
            return reply
        return "[offline] injected client failed for every model in the chain."

    clients = _get_clients()
    if not clients:
        return "[offline] OPENROUTER_API_KEY not set — set it in backend/.env to get real replies."

    chat_messages = [{"role": "system", "content": system_prompt}] + messages

    for label, active_client in clients:
        reply, _error = _try_model_chain(active_client, chat_messages)
        if reply is not None:
            return reply

    # Deliberately generic: never interpolate the raw provider exception
    # into a client-visible message - it can carry the provider's own
    # error text (which itself might echo back request details) straight
    # through to whoever is looking at the app. The real error is still
    # available server-side to whoever calls call_llm with logging.
    return "[offline] the AI assistant is temporarily unavailable — please try again shortly."
