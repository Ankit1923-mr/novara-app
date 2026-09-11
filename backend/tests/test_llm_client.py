"""
Tests for the llm_client fallback path (no live API key required).
Real-call correctness is covered indirectly via
test_conversation_engine.py's injected fake client.
"""

import os
from app.llm_client import call_llm


def test_call_llm_falls_back_when_no_client_and_no_api_key(monkeypatch):
    monkeypatch.delenv("ANTHROPIC_API_KEY", raising=False)
    reply = call_llm("system prompt", [{"role": "user", "content": "hola"}])
    assert reply.startswith("[offline]")


def test_call_llm_uses_injected_client_over_env_lookup():
    class FakeClient:
        class messages:
            @staticmethod
            def create(model, max_tokens, system, messages):
                class R:
                    content = [type("C", (), {"text": "respuesta de prueba"})()]
                return R()

    reply = call_llm("system prompt", [{"role": "user", "content": "hola"}], client=FakeClient())
    assert reply == "respuesta de prueba"
