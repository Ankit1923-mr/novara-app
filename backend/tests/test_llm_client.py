"""
Tests for the llm_client fallback path (no live API key required).
Real-call correctness is covered indirectly via
test_conversation_engine.py's injected fake client.
"""

import httpx
import openai
import app.llm_client as llm_client
from app.llm_client import call_llm, _model_chain, FALLBACK_MODELS


def _fake_response(status_code):
    request = httpx.Request("POST", "https://openrouter.ai/api/v1/chat/completions")
    return httpx.Response(status_code=status_code, request=request)


class ScriptedClient:
    """Fake client whose chat.completions.create() follows a per-model
    script of outcomes: "rate_limit", "not_found", or a reply string.
    Each call to a model consumes the next scripted outcome for that model."""

    def __init__(self, script: dict[str, list]):
        self.script = {k: list(v) for k, v in script.items()}
        self.call_log: list[str] = []

    class _Message:
        def __init__(self, content):
            self.content = content

    class _Choice:
        def __init__(self, content):
            self.message = ScriptedClient._Message(content)

    class _Response:
        def __init__(self, content):
            self.choices = [ScriptedClient._Choice(content)]

    class _Completions:
        def __init__(self, outer):
            self.outer = outer

        def create(self, model, max_tokens, messages):
            self.outer.call_log.append(model)
            outcomes = self.outer.script.get(model, [])
            if not outcomes:
                raise openai.NotFoundError("no script left", response=_fake_response(404), body=None)
            outcome = outcomes.pop(0)
            if outcome == "rate_limit":
                raise openai.RateLimitError("rate limited", response=_fake_response(429), body=None)
            if outcome == "not_found":
                raise openai.NotFoundError("model retired", response=_fake_response(404), body=None)
            return ScriptedClient._Response(outcome)

    class _Chat:
        def __init__(self, outer):
            self.completions = ScriptedClient._Completions(outer)

    @property
    def chat(self):
        return ScriptedClient._Chat(self)


def test_call_llm_falls_back_when_no_client_and_no_api_key(monkeypatch):
    # _client is module-level and lazily cached - if another test (or the
    # real endpoint test) already built a real client in this process,
    # deleting the env var alone won't undo that. Reset it explicitly so
    # this test is independent of run order.
    monkeypatch.delenv("OPENROUTER_API_KEY", raising=False)
    monkeypatch.setattr(llm_client, "_client", None)
    reply = call_llm("system prompt", [{"role": "user", "content": "hola"}])
    assert reply.startswith("[offline]")


def test_call_llm_uses_injected_client_over_env_lookup(monkeypatch):
    monkeypatch.delenv("OPENROUTER_MODEL", raising=False)
    model = FALLBACK_MODELS[0]
    fake_client = ScriptedClient({model: ["respuesta de prueba"]})
    reply = call_llm("system prompt", [{"role": "user", "content": "hola"}], client=fake_client)
    assert reply == "respuesta de prueba"


def test_model_chain_tries_env_var_model_first(monkeypatch):
    monkeypatch.setenv("OPENROUTER_MODEL", "custom/preferred-model:free")
    chain = _model_chain()
    assert chain[0] == "custom/preferred-model:free"
    assert chain[1:] == FALLBACK_MODELS


def test_model_chain_deduplicates_when_env_var_matches_a_fallback(monkeypatch):
    monkeypatch.setenv("OPENROUTER_MODEL", FALLBACK_MODELS[2])
    chain = _model_chain()
    assert chain.count(FALLBACK_MODELS[2]) == 1
    assert chain[0] == FALLBACK_MODELS[2]


def test_retries_once_on_rate_limit_before_succeeding_on_same_model(monkeypatch):
    monkeypatch.delenv("OPENROUTER_MODEL", raising=False)
    monkeypatch.setattr(llm_client, "BACKOFF_SECONDS", 0)
    model = FALLBACK_MODELS[0]
    fake_client = ScriptedClient({model: ["rate_limit", "claro que sí"]})
    reply = call_llm("system", [{"role": "user", "content": "hola"}], client=fake_client)
    assert reply == "claro que sí"
    assert fake_client.call_log == [model, model]


def test_falls_back_to_next_model_after_exhausting_retries(monkeypatch):
    monkeypatch.delenv("OPENROUTER_MODEL", raising=False)
    monkeypatch.setattr(llm_client, "BACKOFF_SECONDS", 0)
    first, second = FALLBACK_MODELS[0], FALLBACK_MODELS[1]
    fake_client = ScriptedClient({
        first: ["rate_limit", "rate_limit"],  # exhausts RETRIES_PER_MODEL=1 (2 attempts)
        second: ["¡hola!"],
    })
    reply = call_llm("system", [{"role": "user", "content": "hola"}], client=fake_client)
    assert reply == "¡hola!"
    assert fake_client.call_log == [first, first, second]


def test_not_found_skips_immediately_without_retry(monkeypatch):
    monkeypatch.delenv("OPENROUTER_MODEL", raising=False)
    first, second = FALLBACK_MODELS[0], FALLBACK_MODELS[1]
    fake_client = ScriptedClient({
        first: ["not_found"],
        second: ["buenos días"],
    })
    reply = call_llm("system", [{"role": "user", "content": "hola"}], client=fake_client)
    assert reply == "buenos días"
    # only ONE call to the retired model - not_found doesn't consume a retry
    assert fake_client.call_log == [first, second]


def test_returns_offline_message_when_every_model_in_chain_fails(monkeypatch):
    monkeypatch.delenv("OPENROUTER_MODEL", raising=False)
    monkeypatch.setattr(llm_client, "BACKOFF_SECONDS", 0)
    script = {model: ["rate_limit", "rate_limit"] for model in FALLBACK_MODELS}
    fake_client = ScriptedClient(script)
    reply = call_llm("system", [{"role": "user", "content": "hola"}], client=fake_client)
    assert reply.startswith("[offline]")
    # every model was tried (with its retry) before giving up
    assert len(fake_client.call_log) == len(FALLBACK_MODELS) * 2
