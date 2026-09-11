from fastapi.testclient import TestClient
from app.main import app

client = TestClient(app)


def test_profile():
    r = client.post("/profile", json={
        "learner_id": "u1", "level": "A2", "region": "Madrid",
        "purpose": "trip", "interests": ["food"], "weak_areas": ["listening"]
    })
    assert r.status_code == 200
    assert r.json()["profile_created"] is True


def test_scenario():
    r = client.get("/scenario", params={"learner_id": "u1"})
    assert r.status_code == 200
    assert r.json()["situation_tags"]


def test_conversation():
    r = client.post("/conversation", json={
        "learner_id": "u1", "scenario_id": "mock-scenario-cafe",
        "message": "Quiero un café.", "turn_number": 1
    })
    assert r.status_code == 200
    assert "reply" in r.json()


def test_repair():
    r = client.post("/repair", json={
        "learner_utterance": "Quiero un cafe", "expected_pattern": "Quiero un café, por favor"
    })
    assert r.status_code == 200
    assert r.json()["error_type"] in ["lexical", "grammar", "register", "comprehension"]


def test_readiness():
    r = client.get("/readiness", params={"learner_id": "u1"})
    assert r.status_code == 200
    assert 0 <= r.json()["aggregate_score"] <= 1
