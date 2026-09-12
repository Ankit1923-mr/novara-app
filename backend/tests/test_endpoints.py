from fastapi.testclient import TestClient
from app.main import app
from app.db import get_session_factory
from app.db_models import LearnerModel
from tests.conftest import TEST_API_KEY
import app.llm_client as llm_client

client = TestClient(app, headers={"X-API-Key": TEST_API_KEY})


def _get_learner_row(learner_id: str) -> LearnerModel:
    """Test helper: reads a learner row directly from the DB, since
    pace_score/confidence_score aren't exposed by any endpoint response."""
    db = get_session_factory()()
    try:
        return db.get(LearnerModel, learner_id)
    finally:
        db.close()


def test_health_check_does_not_require_api_key():
    unauthenticated_client = TestClient(app)  # no X-API-Key header at all
    r = unauthenticated_client.get("/")
    assert r.status_code == 200


def test_missing_api_key_is_rejected():
    unauthenticated_client = TestClient(app)
    r = unauthenticated_client.post("/profile", json={
        "learner_id": "should_not_be_created", "level": "A2", "region": "Madrid",
        "purpose": "trip", "interests": [], "weak_areas": []
    })
    assert r.status_code == 401


def test_wrong_api_key_is_rejected():
    wrong_key_client = TestClient(app, headers={"X-API-Key": "definitely-not-the-real-key"})
    r = wrong_key_client.post("/profile", json={
        "learner_id": "should_not_be_created", "level": "A2", "region": "Madrid",
        "purpose": "trip", "interests": [], "weak_areas": []
    })
    assert r.status_code == 401


def test_profile_rejects_empty_learner_id():
    r = client.post("/profile", json={
        "learner_id": "", "level": "A2", "region": "Madrid",
        "purpose": "trip", "interests": [], "weak_areas": []
    })
    assert r.status_code == 422


def test_profile_rejects_oversized_learner_id():
    r = client.post("/profile", json={
        "learner_id": "x" * 101, "level": "A2", "region": "Madrid",
        "purpose": "trip", "interests": [], "weak_areas": []
    })
    assert r.status_code == 422


def test_profile_rejects_too_many_interests():
    r = client.post("/profile", json={
        "learner_id": "u_edge", "level": "A2", "region": "Madrid",
        "purpose": "trip", "interests": ["x"] * 21, "weak_areas": []
    })
    assert r.status_code == 422


def test_profile_rejects_invalid_purpose():
    r = client.post("/profile", json={
        "learner_id": "u_edge", "level": "A2", "region": "Madrid",
        "purpose": "exam", "interests": [], "weak_areas": []
    })
    assert r.status_code == 422


def test_conversation_rejects_empty_message():
    r = client.post("/conversation", json={
        "learner_id": "u1", "scenario_id": "anything", "message": "", "turn_number": 1
    })
    assert r.status_code == 422


def test_conversation_rejects_oversized_message():
    r = client.post("/conversation", json={
        "learner_id": "u1", "scenario_id": "anything", "message": "x" * 1001, "turn_number": 1
    })
    assert r.status_code == 422


def test_conversation_rejects_non_positive_turn_number():
    r = client.post("/conversation", json={
        "learner_id": "u1", "scenario_id": "anything", "message": "hola", "turn_number": 0
    })
    assert r.status_code == 422


def test_scenario_rejects_empty_learner_id_param():
    r = client.get("/scenario", params={"learner_id": ""})
    assert r.status_code == 422


def test_readiness_rejects_empty_learner_id_param():
    r = client.get("/readiness", params={"learner_id": ""})
    assert r.status_code == 422


def test_unhandled_exception_returns_clean_500_not_a_raw_traceback(monkeypatch):
    import app.main as main_module

    def _boom(*args, **kwargs):
        raise RuntimeError("simulated internal failure")

    monkeypatch.setattr(main_module, "build_scenario", _boom)

    # TestClient re-raises server exceptions by default (raise_server_exceptions=True) -
    # useful for catching bugs in most tests, but it hides the actual HTTP response a
    # real deployment would send. Disable it here to observe what a real client gets.
    non_raising_client = TestClient(app, headers={"X-API-Key": TEST_API_KEY}, raise_server_exceptions=False)

    non_raising_client.post("/profile", json={
        "learner_id": "u_crash_test", "level": "A2", "region": "Madrid",
        "purpose": "trip", "interests": [], "weak_areas": []
    })
    r = non_raising_client.get("/scenario", params={"learner_id": "u_crash_test"})

    assert r.status_code == 500
    body = r.json()
    assert body == {"error": "An unexpected error occurred.", "code": "INTERNAL_ERROR"}
    assert "RuntimeError" not in r.text
    assert "Traceback" not in r.text


def test_profile():
    r = client.post("/profile", json={
        "learner_id": "u1", "level": "A2", "region": "Madrid",
        "purpose": "trip", "interests": ["food"], "weak_areas": ["listening"]
    })
    assert r.status_code == 200
    assert r.json()["profile_created"] is True


def test_scenario():
    # depends on test_profile having created learner "u1" first (pytest
    # runs this file top-to-bottom); test_scenario_requires_existing_profile
    # below covers the case independently.
    r = client.get("/scenario", params={"learner_id": "u1"})
    assert r.status_code == 200
    assert r.json()["situation_tags"]
    assert r.json()["purpose"] == "trip"


def test_scenario_requires_existing_profile():
    r = client.get("/scenario", params={"learner_id": "never_created"})
    assert r.status_code == 404


def test_conversation(monkeypatch):
    # Force the offline fallback path so this test is deterministic and
    # network-free in CI, regardless of whether a real OPENROUTER_API_KEY
    # is set in the environment. Real-call correctness is covered by
    # test_conversation_engine.py's injected fake client.
    monkeypatch.delenv("OPENROUTER_API_KEY", raising=False)
    monkeypatch.delenv("OPENROUTER_API_KEY_BACKUP", raising=False)
    monkeypatch.setattr(llm_client, "_clients", None)

    # depends on test_scenario having created and stored a scenario for
    # "u1" first (pytest runs this file top-to-bottom).
    scenario_id = client.get("/scenario", params={"learner_id": "u1"}).json()["scenario_id"]
    r = client.post("/conversation", json={
        "learner_id": "u1", "scenario_id": scenario_id,
        "message": "Quiero un café.", "turn_number": 1
    })
    assert r.status_code == 200
    assert "reply" in r.json()
    assert isinstance(r.json()["reply"], str) and len(r.json()["reply"]) > 0
    assert r.json()["reply"].startswith("[offline]")


def test_conversation_requires_existing_scenario():
    r = client.post("/conversation", json={
        "learner_id": "u1", "scenario_id": "never_created",
        "message": "Hola", "turn_number": 1
    })
    assert r.status_code == 404


def test_conversation_requires_existing_learner():
    r = client.post("/conversation", json={
        "learner_id": "never_created", "scenario_id": "anything",
        "message": "Hola", "turn_number": 1
    })
    assert r.status_code == 404


def test_conversation_updates_personalization_scores(monkeypatch):
    monkeypatch.delenv("OPENROUTER_API_KEY", raising=False)
    monkeypatch.delenv("OPENROUTER_API_KEY_BACKUP", raising=False)
    monkeypatch.setattr(llm_client, "_clients", None)

    client.post("/profile", json={
        "learner_id": "u_personalization", "level": "A2", "region": "Madrid",
        "purpose": "trip", "interests": ["food"], "weak_areas": []
    })
    scenario_id = client.get("/scenario", params={"learner_id": "u_personalization"}).json()["scenario_id"]

    before = _get_learner_row("u_personalization")
    before_pace = before.pace_score
    before_confidence = before.confidence_score

    client.post("/conversation", json={
        "learner_id": "u_personalization", "scenario_id": scenario_id,
        "message": "no entiendo", "turn_number": 1, "response_time_ms": 500,
    })

    after = _get_learner_row("u_personalization")
    after_pace = after.pace_score
    after_confidence = after.confidence_score

    assert after_pace > before_pace  # fast response (500ms) raises pace
    assert after_confidence < before_confidence  # "no entiendo" triggers repair, lowers confidence


def test_repair():
    r = client.post("/repair", json={
        "learner_utterance": "Quiero un cafe", "expected_pattern": "Quiero un café, por favor"
    })
    assert r.status_code == 200
    assert r.json()["error_type"] in ["lexical", "grammar", "register", "comprehension"]
    assert r.json()["strategy"] in ["clarify", "rephrase", "hint"]


def test_conversation_rate_limit_triggers_after_20_requests_per_minute(monkeypatch):
    monkeypatch.delenv("OPENROUTER_API_KEY", raising=False)
    monkeypatch.delenv("OPENROUTER_API_KEY_BACKUP", raising=False)
    monkeypatch.setattr(llm_client, "_clients", None)

    client.post("/profile", json={
        "learner_id": "u_ratelimit", "level": "A2", "region": "Madrid",
        "purpose": "trip", "interests": ["food"], "weak_areas": []
    })
    scenario_id = client.get("/scenario", params={"learner_id": "u_ratelimit"}).json()["scenario_id"]

    statuses = []
    for i in range(25):
        r = client.post("/conversation", json={
            "learner_id": "u_ratelimit", "scenario_id": scenario_id,
            "message": "hola", "turn_number": i + 1
        })
        statuses.append(r.status_code)

    assert 429 in statuses, "expected at least one 429 after exceeding 20/minute on /conversation"
    assert statuses[:20].count(200) == 20, "first 20 requests within the limit should all succeed"


def test_conversation_flags_repair_on_comprehension_signal(monkeypatch):
    monkeypatch.delenv("OPENROUTER_API_KEY", raising=False)
    monkeypatch.delenv("OPENROUTER_API_KEY_BACKUP", raising=False)
    monkeypatch.setattr(llm_client, "_clients", None)

    scenario_id = client.get("/scenario", params={"learner_id": "u1"}).json()["scenario_id"]
    r = client.post("/conversation", json={
        "learner_id": "u1", "scenario_id": scenario_id,
        "message": "no entiendo", "turn_number": 2
    })
    assert r.status_code == 200
    body = r.json()
    assert body["repair_triggered"] is True
    assert body["repair"]["error_type"] == "comprehension"


def test_readiness():
    r = client.get("/readiness", params={"learner_id": "u1"})
    assert r.status_code == 200
    body = r.json()
    assert 0 <= body["aggregate_score"] <= 1
    assert body["purpose"] == "trip"
    assert set(body["breakdown"].keys()) == {
        "language_accuracy", "repair_success_rate", "register_appropriateness", "transfer_success"
    }
    assert abs(sum(body["weights_used"].values()) - 1.0) < 1e-6


def test_readiness_requires_existing_learner():
    r = client.get("/readiness", params={"learner_id": "never_created"})
    assert r.status_code == 404


def test_readiness_reflects_repair_history(monkeypatch):
    monkeypatch.delenv("OPENROUTER_API_KEY", raising=False)
    monkeypatch.delenv("OPENROUTER_API_KEY_BACKUP", raising=False)
    monkeypatch.setattr(llm_client, "_clients", None)

    client.post("/profile", json={
        "learner_id": "u_readiness", "level": "A2", "region": "Madrid",
        "purpose": "trip", "interests": ["food"], "weak_areas": []
    })
    scenario_id = client.get("/scenario", params={"learner_id": "u_readiness"}).json()["scenario_id"]

    # correct turn, no repair
    client.post("/conversation", json={
        "learner_id": "u_readiness", "scenario_id": scenario_id,
        "message": "Quiero un café, por favor.", "turn_number": 1
    })
    before = client.get("/readiness", params={"learner_id": "u_readiness"}).json()

    # comprehension-repair turn
    client.post("/conversation", json={
        "learner_id": "u_readiness", "scenario_id": scenario_id,
        "message": "no entiendo", "turn_number": 2
    })
    after = client.get("/readiness", params={"learner_id": "u_readiness"}).json()

    assert after["breakdown"]["repair_success_rate"] < before["breakdown"]["repair_success_rate"]
