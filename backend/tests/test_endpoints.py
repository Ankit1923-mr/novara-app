from fastapi.testclient import TestClient
from app.main import app, LEARNERS
import app.llm_client as llm_client

client = TestClient(app)


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

    before_pace = LEARNERS["u_personalization"]["pace_score"]
    before_confidence = LEARNERS["u_personalization"]["confidence_score"]

    client.post("/conversation", json={
        "learner_id": "u_personalization", "scenario_id": scenario_id,
        "message": "no entiendo", "turn_number": 1, "response_time_ms": 500,
    })

    after_pace = LEARNERS["u_personalization"]["pace_score"]
    after_confidence = LEARNERS["u_personalization"]["confidence_score"]

    assert after_pace > before_pace  # fast response (500ms) raises pace
    assert after_confidence < before_confidence  # "no entiendo" triggers repair, lowers confidence


def test_repair():
    r = client.post("/repair", json={
        "learner_utterance": "Quiero un cafe", "expected_pattern": "Quiero un café, por favor"
    })
    assert r.status_code == 200
    assert r.json()["error_type"] in ["lexical", "grammar", "register", "comprehension"]
    assert r.json()["strategy"] in ["clarify", "rephrase", "hint"]


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
