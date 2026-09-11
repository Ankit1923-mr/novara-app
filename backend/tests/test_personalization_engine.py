"""
Independent tests for the Personalization Engine.
Baseline (Review 3): simulate 10 interactions with varying response
times/correctness and verify scores shift in the expected direction
after each one.
"""

from app.personalization_engine import update_scores, _ema, _pace_signal_from_response_time


def test_ema_moves_toward_new_signal_not_away_from_it():
    result = _ema(old_value=0.5, new_signal=1.0)
    assert 0.5 < result < 1.0


def test_pace_signal_fast_response_is_high():
    assert _pace_signal_from_response_time(1000) == 1.0


def test_pace_signal_slow_response_is_low():
    assert _pace_signal_from_response_time(20000) == 0.0


def test_pace_signal_midpoint_is_between_bounds():
    signal = _pace_signal_from_response_time(8500)  # midpoint of 2000-15000
    assert 0.4 < signal < 0.6


def test_repair_triggered_lowers_confidence_score():
    result = update_scores(pace_score=0.5, confidence_score=0.5, repair_triggered=True)
    assert result["confidence_score"] < 0.5


def test_no_repair_raises_confidence_score():
    result = update_scores(pace_score=0.5, confidence_score=0.5, repair_triggered=False)
    assert result["confidence_score"] > 0.5


def test_fast_response_time_raises_pace_score():
    result = update_scores(pace_score=0.5, confidence_score=0.5, repair_triggered=False, response_time_ms=1000)
    assert result["pace_score"] > 0.5


def test_slow_response_time_lowers_pace_score():
    result = update_scores(pace_score=0.5, confidence_score=0.5, repair_triggered=False, response_time_ms=18000)
    assert result["pace_score"] < 0.5


def test_missing_response_time_leaves_pace_score_unchanged():
    result = update_scores(pace_score=0.63, confidence_score=0.5, repair_triggered=False, response_time_ms=None)
    assert result["pace_score"] == 0.63


def test_ten_simulated_interactions_shift_scores_in_expected_direction():
    """Baseline check: 10 interactions with deliberately varying response
    times/correctness. Verify scores move in the expected direction after
    each one — not just that they end up somewhere plausible."""

    # (repair_triggered, response_time_ms, expected_pace_direction, expected_confidence_direction)
    # direction: "up", "down", or "same" (pace "same" when response_time_ms is None)
    interactions = [
        (False, 1500, "up", "up"),      # fast + correct
        (True, 1500, "up", "down"),     # fast + needs repair
        (False, 1500, "up", "up"),      # fast + correct
        (False, 16000, "down", "up"),   # slow + correct
        (True, 16000, "down", "down"),  # slow + needs repair
        (False, None, "same", "up"),    # no timing sent + correct
        (True, None, "same", "down"),   # no timing sent + needs repair
        (False, 2000, "up", "up"),      # fast + correct
        (True, 9000, None, "down"),     # midpoint timing + needs repair (direction depends on current pace)
        (False, 1000, "up", "up"),      # fast + correct
    ]

    pace_score = 0.5
    confidence_score = 0.5

    for repair_triggered, response_time_ms, expected_pace_dir, expected_confidence_dir in interactions:
        result = update_scores(pace_score, confidence_score, repair_triggered, response_time_ms)
        new_pace, new_confidence = result["pace_score"], result["confidence_score"]

        if expected_pace_dir == "up":
            assert new_pace > pace_score
        elif expected_pace_dir == "down":
            assert new_pace < pace_score
        elif expected_pace_dir == "same":
            assert new_pace == pace_score
        # expected_pace_dir == None: direction not asserted, just sanity range below

        if expected_confidence_dir == "up":
            assert new_confidence > confidence_score
        elif expected_confidence_dir == "down":
            assert new_confidence < confidence_score

        assert 0.0 <= new_pace <= 1.0
        assert 0.0 <= new_confidence <= 1.0

        pace_score, confidence_score = new_pace, new_confidence
