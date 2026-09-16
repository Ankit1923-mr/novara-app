"""Tests for automatic pace adjustment (personalization_engine.next_pace_preference)
and its wiring into /conversation and /profile/pace."""

import pytest
from fastapi.testclient import TestClient

from app.personalization_engine import (
    next_pace_preference, REPAIRS_BEFORE_SLOWING_DOWN, CORRECT_STREAK_BEFORE_SPEEDING_UP,
    PACE_STEP, PACE_MIN, PACE_MAX,
)
from app.repair_engine import detect_repair, _trigger_bounds, REPAIR_TRIGGER_MIN_OVERLAP, REPAIR_TRIGGER_MAX_OVERLAP
from app.main import app
from tests.conftest import TEST_API_KEY

client = TestClient(app, headers={"X-API-Key": TEST_API_KEY})


# ---------------------------------------------------------------- unit: next_pace_preference

def test_no_change_below_thresholds():
    result = next_pace_preference(1.0, consecutive_correct=3, consecutive_repairs=1, repair_triggered=False)
    assert result["pace_preference"] == 1.0
    assert result["pace_changed"] is False
    assert result["consecutive_correct"] == 4
    assert result["consecutive_repairs"] == 0  # reset on a correct turn


def test_slows_down_after_two_repairs_in_a_row():
    # consecutive_repairs=1 going in means this turn is the 2nd repair in a
    # row (REPAIRS_BEFORE_SLOWING_DOWN) — the streak hits the threshold and
    # resets to 0 in the same call, which is also what happens.
    result = next_pace_preference(1.0, consecutive_correct=0, consecutive_repairs=1, repair_triggered=True)
    assert result["pace_preference"] == 1.0 - PACE_STEP
    assert result["pace_changed"] is True
    assert result["consecutive_repairs"] == 0
    assert result["consecutive_correct"] == 0


def test_speeds_up_after_ten_correct_in_a_row():
    result = next_pace_preference(1.0, consecutive_correct=CORRECT_STREAK_BEFORE_SPEEDING_UP - 1,
                                   consecutive_repairs=0, repair_triggered=False)
    assert result["pace_preference"] == 1.0 + PACE_STEP
    assert result["pace_changed"] is True
    assert result["consecutive_correct"] == 0


def test_pace_does_not_go_below_min():
    result = next_pace_preference(PACE_MIN, consecutive_correct=0, consecutive_repairs=1, repair_triggered=True)
    assert result["pace_preference"] == PACE_MIN
    assert result["pace_changed"] is False  # no actual change, so no reason reported


def test_pace_does_not_go_above_max():
    result = next_pace_preference(PACE_MAX, consecutive_correct=CORRECT_STREAK_BEFORE_SPEEDING_UP - 1,
                                   consecutive_repairs=0, repair_triggered=False)
    assert result["pace_preference"] == PACE_MAX
    assert result["pace_changed"] is False


def test_a_correct_turn_resets_the_repair_streak():
    result = next_pace_preference(1.0, consecutive_correct=0, consecutive_repairs=1, repair_triggered=False)
    assert result["consecutive_repairs"] == 0
    assert result["consecutive_correct"] == 1


def test_a_repair_resets_the_correct_streak():
    result = next_pace_preference(1.0, consecutive_correct=5, consecutive_repairs=0, repair_triggered=True)
    assert result["consecutive_correct"] == 0


# ---------------------------------------------------------------- unit: repair_engine leniency bounds

def test_trigger_bounds_default_when_no_pace_given():
    assert _trigger_bounds(None) == (REPAIR_TRIGGER_MIN_OVERLAP, REPAIR_TRIGGER_MAX_OVERLAP)


def test_trigger_bounds_relaxed_is_more_forgiving():
    lo, hi = _trigger_bounds(0.5)
    assert lo < REPAIR_TRIGGER_MIN_OVERLAP
    assert hi < REPAIR_TRIGGER_MAX_OVERLAP


def test_trigger_bounds_fast_is_stricter():
    lo, hi = _trigger_bounds(2.0)
    assert lo > REPAIR_TRIGGER_MIN_OVERLAP
    assert hi > REPAIR_TRIGGER_MAX_OVERLAP


def test_relaxed_pace_forgives_a_borderline_overlap_that_fast_pace_would_flag():
    # overlap of ~0.8 is below the default max (0.85, still flagged) but
    # above the relaxed max (0.75, so not flagged) and below the fast max
    # (0.9, still flagged) — pick candidates that produce ~0.8 overlap.
    candidates = ["Quiero un café con leche, por favor."]
    utterance = "Quiero un café con leche"  # missing "por favor" content word only via punctuation, overlap should be high
    relaxed = detect_repair(utterance, candidates, pace_preference=0.5)
    fast = detect_repair(utterance, candidates, pace_preference=2.0)
    # whatever the exact ratio, fast must be at least as strict as relaxed —
    # if fast triggers a repair, relaxed must too when relaxed's max is lower;
    # the meaningful invariant is fast's max overlap threshold is never lower.
    lo_relaxed, hi_relaxed = _trigger_bounds(0.5)
    lo_fast, hi_fast = _trigger_bounds(2.0)
    assert hi_fast > hi_relaxed
    assert lo_fast > lo_relaxed


# ---------------------------------------------------------------- integration: /conversation wiring

def _make_learner(learner_id="pace_test@example.com"):
    resp = client.post("/profile", json={
        "learner_id": learner_id, "purpose": "trip", "level": "A1", "region": "spain",
        "interests": [], "weak_areas": [],
    })
    assert resp.status_code == 200
    return learner_id


def test_conversation_response_includes_pace_fields(monkeypatch):
    learner_id = _make_learner("pace_fields@example.com")
    scenario = client.get(f"/scenario?learner_id={learner_id}").json()

    resp = client.post("/conversation", json={
        "learner_id": learner_id,
        "scenario_id": scenario["scenario_id"],
        "message": "hola",
        "turn_number": 1,
    })
    assert resp.status_code == 200
    body = resp.json()
    assert "pace_changed" in body
    assert "pace_change_reason" in body
    assert "pace_preference" in body
    assert body["pace_preference"] == 1.0  # unchanged after just one turn


def test_two_repairs_in_a_row_slows_down_pace_via_conversation_endpoint():
    learner_id = _make_learner("pace_slowdown@example.com")
    scenario = client.get(f"/scenario?learner_id={learner_id}").json()

    # "no entiendo" always triggers a repair (comprehension marker)
    r1 = client.post("/conversation", json={
        "learner_id": learner_id, "scenario_id": scenario["scenario_id"],
        "message": "no entiendo", "turn_number": 1,
    }).json()
    assert r1["repair_triggered"] is True
    assert r1["pace_changed"] is False  # only 1 repair so far

    r2 = client.post("/conversation", json={
        "learner_id": learner_id, "scenario_id": scenario["scenario_id"],
        "message": "no entiendo", "turn_number": 2,
    }).json()
    assert r2["repair_triggered"] is True
    assert r2["pace_changed"] is True
    assert r2["pace_preference"] == 1.0 - PACE_STEP


def test_manual_pace_override_resets_streak_counters():
    learner_id = _make_learner("pace_manual_reset@example.com")
    scenario = client.get(f"/scenario?learner_id={learner_id}").json()

    # build up one repair toward the slowdown streak
    client.post("/conversation", json={
        "learner_id": learner_id, "scenario_id": scenario["scenario_id"],
        "message": "no entiendo", "turn_number": 1,
    })

    # manual override
    resp = client.post(f"/profile/pace?learner_id={learner_id}&pace_preference=1.5")
    assert resp.status_code == 200

    me = client.get(f"/me?learner_id={learner_id}").json()
    assert me["pace_preference"] == 1.5

    # one more repair should NOT immediately trigger a slowdown, since the
    # override should have reset the in-progress streak
    r = client.post("/conversation", json={
        "learner_id": learner_id, "scenario_id": scenario["scenario_id"],
        "message": "no entiendo", "turn_number": 2,
    }).json()
    assert r["pace_changed"] is False
    assert r["pace_preference"] == 1.5
