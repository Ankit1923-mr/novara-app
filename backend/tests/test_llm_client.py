"""
Tests for the llm_client fallback path (no live API key required).
Real-call correctness is covered indirectly via
test_conversation_engine.py's injected fake client.
"""

import app.llm_client as llm_client
from app.llm_client import call_llm


def test_call_llm_falls_back_when_no_client_and_no_api_key(monkeypatch):
    # _client is module-level and lazily cached - if another test (or the
    # real endpoint test) already built a real client in this process,
    # deleting the env var alone won't undo that. Reset it explicitly so
    # this test is independent of run order.
    monkeypatch.delenv("OPENROUTER_API_KEY", raising=False)
    monkeypatch.setattr(llm_client, "_client", None)
    reply = call_llm("system prompt", [{"role": "user", "content": "hola"}])
    assert reply.startswith("[offline]")


def test_call_llm_uses_injected_client_over_env_lookup():
    class FakeMessage:
        content = "respuesta de prueba"

    class FakeChoice:
        message = FakeMessage()

    class FakeResponse:
        choices = [FakeChoice()]

    class FakeCompletions:
        @staticmethod
        def create(model, max_tokens, messages):
            return FakeResponse()

    class FakeChat:
        completions = FakeCompletions()

    class FakeClient:
        chat = FakeChat()

    reply = call_llm("system prompt", [{"role": "user", "content": "hola"}], client=FakeClient())
    assert reply == "respuesta de prueba"
