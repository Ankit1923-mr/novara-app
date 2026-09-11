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

Model is free-tier by default (OPENROUTER_MODEL env var, see .env).
Check https://openrouter.ai/models?max_price=0 for current free options
if the default model is retired or rate-limited.
"""

import os
from typing import Optional
from dotenv import load_dotenv

load_dotenv()

DEFAULT_MODEL = "nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free"
MAX_TOKENS = 200

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


def call_llm(system_prompt: str, messages: list[dict], client: Optional[object] = None) -> str:
    """
    messages: [{"role": "user" | "assistant", "content": "..."}]
    client: injected for tests; defaults to the lazily-built OpenRouter client.
    """
    active_client = client if client is not None else _get_client()

    if active_client is None:
        return "[offline] OPENROUTER_API_KEY not set — set it in backend/.env to get real replies."

    model = os.environ.get("OPENROUTER_MODEL", DEFAULT_MODEL)
    chat_messages = [{"role": "system", "content": system_prompt}] + messages

    response = active_client.chat.completions.create(
        model=model,
        max_tokens=MAX_TOKENS,
        messages=chat_messages,
    )
    return response.choices[0].message.content
