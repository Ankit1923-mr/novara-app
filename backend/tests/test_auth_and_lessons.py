"""
Endpoint tests for the web-app additions: account signup/login and the
lesson/quiz flow. Uses the same shared TestClient pattern as
test_endpoints.py.
"""

from fastapi.testclient import TestClient
from app.main import app
from app.db import get_session_factory
from app.db_models import LearnerModel, UserModel
from tests.conftest import TEST_API_KEY

client = TestClient(app, headers={"X-API-Key": TEST_API_KEY})


def _get_user_row(email: str) -> UserModel:
    db = get_session_factory()()
    try:
        return db.get(UserModel, email)
    finally:
        db.close()


# --- signup / login ---

def test_signup_creates_a_real_account():
    r = client.post("/auth/signup", json={"email": "learner1@example.com", "password": "correcthorse123"})
    assert r.status_code == 200
    body = r.json()
    assert body["email"] == "learner1@example.com"
    assert body["learner_id"] == "learner1@example.com"
    assert body["has_profile"] is False
    assert len(body["token"]) > 20

    row = _get_user_row("learner1@example.com")
    assert row is not None
    assert row.password_hash != "correcthorse123"  # never store the plaintext
    assert row.email_verified is False


def test_signup_rejects_duplicate_email():
    client.post("/auth/signup", json={"email": "dup@example.com", "password": "correcthorse123"})
    r = client.post("/auth/signup", json={"email": "dup@example.com", "password": "anotherpassword"})
    assert r.status_code == 409


def test_signup_rejects_short_password():
    r = client.post("/auth/signup", json={"email": "shortpw@example.com", "password": "abc"})
    assert r.status_code == 422


def test_login_with_correct_password_succeeds():
    client.post("/auth/signup", json={"email": "loginuser@example.com", "password": "correcthorse123"})
    r = client.post("/auth/login", json={"email": "loginuser@example.com", "password": "correcthorse123"})
    assert r.status_code == 200
    assert r.json()["email"] == "loginuser@example.com"


def test_login_with_wrong_password_fails():
    client.post("/auth/signup", json={"email": "wrongpw@example.com", "password": "correcthorse123"})
    r = client.post("/auth/login", json={"email": "wrongpw@example.com", "password": "totallywrong"})
    assert r.status_code == 401


def test_login_with_unknown_email_fails_with_same_error_as_wrong_password():
    r1 = client.post("/auth/login", json={"email": "neverexisted@example.com", "password": "whatever123"})
    assert r1.status_code == 401
    # same detail message for both cases - don't let the error reveal whether the email is registered
    client.post("/auth/signup", json={"email": "existsnow@example.com", "password": "correcthorse123"})
    r2 = client.post("/auth/login", json={"email": "existsnow@example.com", "password": "wrongpassword"})
    assert r1.json()["detail"] == r2.json()["detail"]


def test_signup_email_is_case_insensitive_and_trimmed():
    client.post("/auth/signup", json={"email": "  MixedCase@Example.com  ", "password": "correcthorse123"})
    r = client.post("/auth/login", json={"email": "mixedcase@example.com", "password": "correcthorse123"})
    assert r.status_code == 200


def test_has_profile_reflects_whether_profile_exists():
    client.post("/auth/signup", json={"email": "profiletest@example.com", "password": "correcthorse123"})
    client.post("/profile", json={
        "learner_id": "profiletest@example.com", "level": "A2", "region": "Madrid",
        "purpose": "trip", "interests": [], "weak_areas": []
    })
    r = client.post("/auth/login", json={"email": "profiletest@example.com", "password": "correcthorse123"})
    assert r.json()["has_profile"] is True


# --- topics / quiz ---

def test_list_topics_returns_at_least_three():
    r = client.get("/topics", params={"learner_id": "any_learner"})
    assert r.status_code == 200
    topics = r.json()
    assert len(topics) >= 3
    assert all(t["completed"] is False for t in topics)


def test_get_topic_detail_includes_vocabulary_and_quiz():
    r = client.get("/topics/greetings")
    assert r.status_code == 200
    body = r.json()
    assert body["topic_id"] == "greetings"
    assert len(body["vocabulary"]) > 0
    assert len(body["quiz"]) > 0
    assert "cultural_note" in body["vocabulary"][0]


def test_get_unknown_topic_404s():
    r = client.get("/topics/does_not_exist")
    assert r.status_code == 404


def test_submit_quiz_requires_existing_learner():
    r = client.post("/topics/greetings/submit", json={"learner_id": "never_created_learner", "answers": [0, 0, 0, 0]})
    assert r.status_code == 404


def test_submit_quiz_perfect_score_marks_topic_completed_and_updates_streak():
    client.post("/profile", json={
        "learner_id": "quiz_learner_1", "level": "A2", "region": "Madrid",
        "purpose": "trip", "interests": [], "weak_areas": []
    })
    topic = client.get("/topics/greetings").json()
    correct_answers = [q["correct_index"] for q in topic["quiz"]]

    r = client.post("/topics/greetings/submit", json={"learner_id": "quiz_learner_1", "answers": correct_answers})
    assert r.status_code == 200
    result = r.json()
    assert result["score"] == 1.0
    assert result["missed_phrases"] == []

    topics = client.get("/topics", params={"learner_id": "quiz_learner_1"}).json()
    greetings = next(t for t in topics if t["topic_id"] == "greetings")
    assert greetings["completed"] is True

    db = get_session_factory()()
    try:
        learner = db.get(LearnerModel, "quiz_learner_1")
        assert learner.streak_days == 1
        assert learner.last_active_date is not None
    finally:
        db.close()


def test_submit_quiz_wrong_answers_tracked_as_mistake_words():
    client.post("/profile", json={
        "learner_id": "quiz_learner_2", "level": "A2", "region": "Madrid",
        "purpose": "trip", "interests": [], "weak_areas": []
    })
    topic = client.get("/topics/greetings").json()
    wrong_answers = [(q["correct_index"] + 1) % len(q["options"]) for q in topic["quiz"]]

    client.post("/topics/greetings/submit", json={"learner_id": "quiz_learner_2", "answers": wrong_answers})

    db = get_session_factory()()
    try:
        learner = db.get(LearnerModel, "quiz_learner_2")
        assert len(learner.mistake_words) > 0
        assert "greetings" not in learner.topics_completed
    finally:
        db.close()


# --- pace preference ---

def test_set_pace_preference_updates_learner():
    client.post("/profile", json={
        "learner_id": "pace_learner_1", "level": "A2", "region": "Madrid",
        "purpose": "trip", "interests": [], "weak_areas": []
    })
    r = client.post("/profile/pace", params={"learner_id": "pace_learner_1", "pace_preference": 1.5})
    assert r.status_code == 200
    assert r.json()["pace_preference"] == 1.5


def test_set_pace_preference_rejects_out_of_range_value():
    client.post("/profile", json={
        "learner_id": "pace_learner_2", "level": "A2", "region": "Madrid",
        "purpose": "trip", "interests": [], "weak_areas": []
    })
    r = client.post("/profile/pace", params={"learner_id": "pace_learner_2", "pace_preference": 5.0})
    assert r.status_code == 422


def test_set_pace_preference_requires_existing_learner():
    r = client.post("/profile/pace", params={"learner_id": "never_created", "pace_preference": 1.2})
    assert r.status_code == 404
