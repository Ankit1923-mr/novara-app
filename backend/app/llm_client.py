"""
Thin wrapper around the Anthropic API for the AI Conversation Partner.

Kept separate from conversation_engine.py so the network call is the one
thing mocked in tests — everything else (prompt construction, history,
turn handling) is tested against real logic, not a stubbed LLM.

Requires ANTHROPIC_API_KEY in the environment for real calls. Without
it, falls back to a clearly-labeled canned reply so local dev and CI
don't need a live key to run the rest of the stack.
"""

import os
from typing import Optional

MODEL = "claude-haiku-4-5-20251001"
MAX_TOKENS = 200

_client = None


def _get_client():
    """Lazily construct the Anthropic client so importing this module
    never requires a key (needed for tests that only check the fallback
    path, and so /docs and other endpoints work without one)."""
    global _client
    if _client is not None:
        return _client
    api_key = os.environ.get("ANTHROPIC_API_KEY")
    if not api_key:
        return None
    import anthropic
    _client = anthropic.Anthropic(api_key=api_key)
    return _client


def call_llm(system_prompt: str, messages: list[dict], client: Optional[object] = None) -> str:
    """
    messages: [{"role": "user" | "assistant", "content": "..."}]
    client: injected for tests; defaults to the lazily-built Anthropic client.
    """
    active_client = client if client is not None else _get_client()

    if active_client is None:
        return "[offline] ANTHROPIC_API_KEY not set — set it in the environment to get real replies."

    response = active_client.messages.create(
        model=MODEL,
        max_tokens=MAX_TOKENS,
        system=system_prompt,
        messages=messages,
    )
    return response.content[0].text
