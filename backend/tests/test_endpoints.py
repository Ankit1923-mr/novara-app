from fastapi.testclient import TestClient
from app.main import app
from app.db import get_session_factory
from app.db_models import LearnerModel
from tests.conftest import TEST_API_KEY, auth_headers
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


def test_demo_api_key_is_also_accepted(monkeypatch):
    monkeypatch.setenv("NOVARA_API_KEY_DEMO", "demo-key-for-this-test")
    demo_client = TestClient(app, headers={"X-API-Key": "demo-key-for-this-test"})
    r = demo_client.post("/profile", headers=auth_headers("demo_key_test_learner"), json={
        "learner_id": "demo_key_test_learner", "level": "A2", "region": "Madrid",
        "purpose": "trip", "interests": [], "weak_areas": []
    })
    assert r.status_code == 200


def test_profile_rejects_missing_bearer_token():
    r = client.post("/profile", json={
        "learner_id": "u_no_token", "level": "A2", "region": "Madrid",
        "purpose": "trip", "interests": [], "weak_areas": []
    })
    assert r.status_code == 401


def test_profile_rejects_malformed_bearer_token():
    r = client.post("/profile", headers={"Authorization": "Bearer not-a-real-jwt"}, json={
        "learner_id": "u_bad_token", "level": "A2", "region": "Madrid",
        "purpose": "trip", "interests": [], "weak_areas": []
    })
    assert r.status_code == 401


def test_profile_rejects_expired_bearer_token():
    import jwt as pyjwt
    from datetime import datetime, timedelta, timezone
    from app.auth_service import _get_jwt_secret, JWT_ALGORITHM

    expired_token = pyjwt.encode(
        {"sub": "u_expired", "iat": datetime.now(timezone.utc) - timedelta(days=30),
         "exp": datetime.now(timezone.utc) - timedelta(days=1)},
        _get_jwt_secret(), algorithm=JWT_ALGORITHM,
    )
    r = client.post("/profile", headers={"Authorization": f"Bearer {expired_token}"}, json={
        "learner_id": "u_expired", "level": "A2", "region": "Madrid",
        "purpose": "trip", "interests": [], "weak_areas": []
    })
    assert r.status_code == 401


def test_profile_rejects_mismatched_learner_id():
    r = client.post("/profile", headers=auth_headers("u_owns_this_one"), json={
        "learner_id": "u_someone_elses_id", "level": "A2", "region": "Madrid",
        "purpose": "trip", "interests": [], "weak_areas": []
    })
    assert r.status_code == 403
    assert "learner_id" in r.json()["detail"]


def test_profile_mismatch_error_identical_for_existing_and_nonexistent_learner_id():
    # "u1" is created elsewhere in this file; "definitely_never_created" never is.
    # Both must produce the exact same 403 response - otherwise the error itself
    # would disclose whether a given learner_id exists.
    r_existing = client.post("/profile", headers=auth_headers("u_attacker"), json={
        "learner_id": "u1", "level": "A2", "region": "Madrid",
        "purpose": "trip", "interests": [], "weak_areas": []
    })
    r_missing = client.post("/profile", headers=auth_headers("u_attacker"), json={
        "learner_id": "definitely_never_created", "level": "A2", "region": "Madrid",
        "purpose": "trip", "interests": [], "weak_areas": []
    })
    assert r_existing.status_code == r_missing.status_code == 403
    assert r_existing.json() == r_missing.json()


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

    non_raising_client.post("/profile", headers=auth_headers("u_crash_test"), json={
        "learner_id": "u_crash_test", "level": "A2", "region": "Madrid",
        "purpose": "trip", "interests": [], "weak_areas": []
    })
    r = non_raising_client.get("/scenario", headers=auth_headers("u_crash_test"), params={"learner_id": "u_crash_test"})

    assert r.status_code == 500
    body = r.json()
    assert body == {"error": "An unexpected error occurred.", "code": "INTERNAL_ERROR"}
    assert "RuntimeError" not in r.text
    assert "Traceback" not in r.text


def test_profile():
    r = client.post("/profile", headers=auth_headers("u1"), json={
        "learner_id": "u1", "level": "A2", "region": "Madrid",
        "purpose": "trip", "interests": ["food"], "weak_areas": ["listening"]
    })
    assert r.status_code == 200
    assert r.json()["profile_created"] is True


def test_scenario():
    # depends on test_profile having created learner "u1" first (pytest
    # runs this file top-to-bottom); test_scenario_requires_existing_profile
    # below covers the case independently.
    r = client.get("/scenario", headers=auth_headers("u1"), params={"learner_id": "u1"})
    assert r.status_code == 200
    assert r.json()["situation_tags"]
    assert r.json()["purpose"] == "trip"


def test_scenario_returns_vocabulary_with_translations_and_culture_notes():
    # The learner must get actual teaching material over the real HTTP
    # endpoint, not just a role-play opening line.
    r = client.get("/scenario", headers=auth_headers("u1"), params={"learner_id": "u1"})
    assert r.status_code == 200
    vocab = r.json()["vocabulary"]
    assert len(vocab) > 0
    for item in vocab:
        assert set(item.keys()) == {"phrase", "meaning", "cultural_note"}
        assert item["phrase"] and item["meaning"] and item["cultural_note"]


def test_scenario_requires_existing_profile():
    r = client.get("/scenario", headers=auth_headers("never_created"), params={"learner_id": "never_created"})
    assert r.status_code == 404


def test_scenario_rejects_mismatched_learner_id():
    r = client.get("/scenario", headers=auth_headers("u1"), params={"learner_id": "someone_else"})
    assert r.status_code == 403


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
    scenario_id = client.get("/scenario", headers=auth_headers("u1"), params={"learner_id": "u1"}).json()["scenario_id"]
    r = client.post("/conversation", headers=auth_headers("u1"), json={
        "learner_id": "u1", "scenario_id": scenario_id,
        "message": "Quiero un café.", "turn_number": 1
    })
    assert r.status_code == 200
    assert "reply" in r.json()
    assert isinstance(r.json()["reply"], str) and len(r.json()["reply"]) > 0
    assert r.json()["reply"].startswith("[offline]")


def test_scenario_persists_chosen_situation_for_vocabulary_alignment():
    # Regression test: /conversation used to rebuild its candidate nodes
    # by iterating every tag in situation_tags, which could pull in
    # unrelated phrases from OTHER situations sharing a tag (e.g.
    # "ordering" appears on both a café node and a restaurant node).
    # Fixed by persisting chosen_situation and looking up by that one
    # situation only. This confirms the DB round trip, not just the
    # adaptive_engine function in isolation.
    from app.db import get_session_factory
    from app.db_models import ScenarioModel
    from app.knowledge_graph import get_subgraph

    scenario = client.get("/scenario", headers=auth_headers("u1"), params={"learner_id": "u1"}).json()

    db = get_session_factory()()
    try:
        row = db.get(ScenarioModel, scenario["scenario_id"])
        assert row.chosen_situation is not None
        expected_nodes = get_subgraph(scenario["purpose"], situation_tag=row.chosen_situation)
    finally:
        db.close()

    expected_phrases = {n["phrase"] for n in expected_nodes}
    actual_phrases = {v["phrase"] for v in scenario["vocabulary"]}
    assert actual_phrases == expected_phrases


def test_conversation_requires_existing_scenario():
    r = client.post("/conversation", headers=auth_headers("u1"), json={
        "learner_id": "u1", "scenario_id": "never_created",
        "message": "Hola", "turn_number": 1
    })
    assert r.status_code == 404


def test_conversation_requires_existing_learner():
    r = client.post("/conversation", headers=auth_headers("never_created"), json={
        "learner_id": "never_created", "scenario_id": "anything",
        "message": "Hola", "turn_number": 1
    })
    assert r.status_code == 404


def test_conversation_rejects_mismatched_learner_id():
    r = client.post("/conversation", headers=auth_headers("u1"), json={
        "learner_id": "someone_else", "scenario_id": "anything",
        "message": "Hola", "turn_number": 1
    })
    assert r.status_code == 403


def test_conversation_updates_personalization_scores(monkeypatch):
    monkeypatch.delenv("OPENROUTER_API_KEY", raising=False)
    monkeypatch.delenv("OPENROUTER_API_KEY_BACKUP", raising=False)
    monkeypatch.setattr(llm_client, "_clients", None)

    client.post("/profile", headers=auth_headers("u_personalization"), json={
        "learner_id": "u_personalization", "level": "A2", "region": "Madrid",
        "purpose": "trip", "interests": ["food"], "weak_areas": []
    })
    scenario_id = client.get("/scenario", headers=auth_headers("u_personalization"), params={"learner_id": "u_personalization"}).json()["scenario_id"]

    before = _get_learner_row("u_personalization")
    before_pace = before.pace_score
    before_confidence = before.confidence_score

    client.post("/conversation", headers=auth_headers("u_personalization"), json={
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

    client.post("/profile", headers=auth_headers("u_ratelimit"), json={
        "learner_id": "u_ratelimit", "level": "A2", "region": "Madrid",
        "purpose": "trip", "interests": ["food"], "weak_areas": []
    })
    scenario_id = client.get("/scenario", headers=auth_headers("u_ratelimit"), params={"learner_id": "u_ratelimit"}).json()["scenario_id"]

    statuses = []
    for i in range(25):
        r = client.post("/conversation", headers=auth_headers("u_ratelimit"), json={
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

    scenario_id = client.get("/scenario", headers=auth_headers("u1"), params={"learner_id": "u1"}).json()["scenario_id"]
    r = client.post("/conversation", headers=auth_headers("u1"), json={
        "learner_id": "u1", "scenario_id": scenario_id,
        "message": "no entiendo", "turn_number": 2
    })
    assert r.status_code == 200
    body = r.json()
    assert body["repair_triggered"] is True
    assert body["repair"]["error_type"] == "comprehension"


def test_conversation_history_returns_turns_in_order(monkeypatch):
    monkeypatch.delenv("OPENROUTER_API_KEY", raising=False)
    monkeypatch.delenv("OPENROUTER_API_KEY_BACKUP", raising=False)
    monkeypatch.setattr(llm_client, "_clients", None)

    client.post("/profile", headers=auth_headers("u_history"), json={
        "learner_id": "u_history", "level": "A2", "region": "Madrid",
        "purpose": "trip", "interests": ["food"], "weak_areas": []
    })
    scenario_id = client.get("/scenario", headers=auth_headers("u_history"), params={"learner_id": "u_history"}).json()["scenario_id"]
    client.post("/conversation", headers=auth_headers("u_history"), json={
        "learner_id": "u_history", "scenario_id": scenario_id,
        "message": "Quiero un café.", "turn_number": 1
    })

    r = client.get("/conversation/history", headers=auth_headers("u_history"),
                    params={"learner_id": "u_history", "scenario_id": scenario_id})
    assert r.status_code == 200
    body = r.json()
    assert body["learner_id"] == "u_history"
    assert body["scenario_id"] == scenario_id
    assert len(body["turns"]) == 3  # opening line + user message + reply
    assert body["turns"][0]["role"] == "assistant"
    assert body["turns"][1] == {"role": "user", "content": "Quiero un café."}


def test_conversation_history_requires_existing_learner():
    r = client.get("/conversation/history", headers=auth_headers("never_created"),
                    params={"learner_id": "never_created", "scenario_id": "anything"})
    assert r.status_code == 404


def test_conversation_history_rejects_cross_account_access(monkeypatch):
    monkeypatch.delenv("OPENROUTER_API_KEY", raising=False)
    monkeypatch.delenv("OPENROUTER_API_KEY_BACKUP", raising=False)
    monkeypatch.setattr(llm_client, "_clients", None)

    client.post("/profile", headers=auth_headers("u_history_owner"), json={
        "learner_id": "u_history_owner", "level": "A2", "region": "Madrid",
        "purpose": "trip", "interests": [], "weak_areas": []
    })
    scenario_id = client.get("/scenario", headers=auth_headers("u_history_owner"), params={"learner_id": "u_history_owner"}).json()["scenario_id"]
    client.post("/conversation", headers=auth_headers("u_history_owner"), json={
        "learner_id": "u_history_owner", "scenario_id": scenario_id,
        "message": "hola", "turn_number": 1
    })

    r = client.get("/conversation/history", headers=auth_headers("u_history_attacker"),
                    params={"learner_id": "u_history_owner", "scenario_id": scenario_id})
    assert r.status_code == 403


def test_conversation_history_rejects_missing_bearer_token():
    r = client.get("/conversation/history", params={"learner_id": "u1", "scenario_id": "anything"})
    assert r.status_code == 401


def test_readiness():
    r = client.get("/readiness", headers=auth_headers("u1"), params={"learner_id": "u1"})
    assert r.status_code == 200
    body = r.json()
    assert 0 <= body["aggregate_score"] <= 1
    assert body["purpose"] == "trip"
    assert set(body["breakdown"].keys()) == {
        "language_accuracy", "repair_success_rate", "register_appropriateness", "transfer_success"
    }
    assert abs(sum(body["weights_used"].values()) - 1.0) < 1e-6
    # Actionable review - a learner must get a concrete next step, not
    # just raw numbers, over the real HTTP response.
    assert isinstance(body["next_step"], str) and len(body["next_step"]) > 0


def test_readiness_requires_existing_learner():
    r = client.get("/readiness", headers=auth_headers("never_created"), params={"learner_id": "never_created"})
    assert r.status_code == 404


def test_readiness_rejects_mismatched_learner_id():
    r = client.get("/readiness", headers=auth_headers("u1"), params={"learner_id": "someone_else"})
    assert r.status_code == 403


def test_readiness_reflects_repair_history(monkeypatch):
    monkeypatch.delenv("OPENROUTER_API_KEY", raising=False)
    monkeypatch.delenv("OPENROUTER_API_KEY_BACKUP", raising=False)
    monkeypatch.setattr(llm_client, "_clients", None)

    client.post("/profile", headers=auth_headers("u_readiness"), json={
        "learner_id": "u_readiness", "level": "A2", "region": "Madrid",
        "purpose": "trip", "interests": ["food"], "weak_areas": []
    })
    scenario_id = client.get("/scenario", headers=auth_headers("u_readiness"), params={"learner_id": "u_readiness"}).json()["scenario_id"]

    # correct turn, no repair
    client.post("/conversation", headers=auth_headers("u_readiness"), json={
        "learner_id": "u_readiness", "scenario_id": scenario_id,
        "message": "Quiero un café, por favor.", "turn_number": 1
    })
    before = client.get("/readiness", headers=auth_headers("u_readiness"), params={"learner_id": "u_readiness"}).json()

    # comprehension-repair turn
    client.post("/conversation", headers=auth_headers("u_readiness"), json={
        "learner_id": "u_readiness", "scenario_id": scenario_id,
        "message": "no entiendo", "turn_number": 2
    })
    after = client.get("/readiness", headers=auth_headers("u_readiness"), params={"learner_id": "u_readiness"}).json()

    assert after["breakdown"]["repair_success_rate"] < before["breakdown"]["repair_success_rate"]
