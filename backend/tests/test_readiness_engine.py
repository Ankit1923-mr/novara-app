"""
Independent tests for the Readiness Scoring Engine.
Baseline (Review 3): 3 synthetic learners (high/mid/low performer) ->
aggregate score ranks them correctly, for both purposes.
"""

from app.readiness_engine import compute_metrics, compute_readiness, WEIGHTS_BY_PURPOSE


def test_weights_sum_to_one_for_every_purpose():
    for purpose, weights in WEIGHTS_BY_PURPOSE.items():
        assert abs(sum(weights.values()) - 1.0) < 1e-9, f"{purpose} weights don't sum to 1.0"


def test_zero_turns_falls_back_to_neutral_metrics_not_zero_or_failing():
    metrics = compute_metrics(total_turns=0, repair_counts={}, distinct_situations_visited=0, total_known_situations=10)
    assert metrics["language_accuracy"] == 0.5
    assert metrics["repair_success_rate"] == 0.5
    assert metrics["register_appropriateness"] == 0.5


def test_no_repairs_gives_perfect_accuracy_and_repair_metrics():
    metrics = compute_metrics(
        total_turns=10, repair_counts={}, distinct_situations_visited=5, total_known_situations=10
    )
    assert metrics["language_accuracy"] == 1.0
    assert metrics["repair_success_rate"] == 1.0
    assert metrics["register_appropriateness"] == 1.0


def test_transfer_success_reflects_situation_breadth():
    full_coverage = compute_metrics(total_turns=10, repair_counts={}, distinct_situations_visited=10, total_known_situations=10)
    half_coverage = compute_metrics(total_turns=10, repair_counts={}, distinct_situations_visited=5, total_known_situations=10)
    no_coverage = compute_metrics(total_turns=10, repair_counts={}, distinct_situations_visited=0, total_known_situations=10)

    assert full_coverage["transfer_success"] == 1.0
    assert half_coverage["transfer_success"] == 0.5
    assert no_coverage["transfer_success"] == 0.0
    assert full_coverage["transfer_success"] > half_coverage["transfer_success"] > no_coverage["transfer_success"]


def test_register_errors_only_affect_register_appropriateness_not_accuracy():
    metrics = compute_metrics(
        total_turns=10, repair_counts={"register": 4}, distinct_situations_visited=5, total_known_situations=10
    )
    assert metrics["register_appropriateness"] == 0.6  # 1 - 4/10
    assert metrics["language_accuracy"] == 1.0  # no lexical/grammar errors


def test_lexical_and_grammar_errors_lower_language_accuracy():
    metrics = compute_metrics(
        total_turns=10, repair_counts={"lexical": 2, "grammar": 1}, distinct_situations_visited=5, total_known_situations=10
    )
    assert metrics["language_accuracy"] == 0.7  # 1 - 3/10


def test_compute_readiness_matches_contract_shape():
    result = compute_readiness(
        purpose="trip", total_turns=10, repair_counts={"lexical": 1},
        distinct_situations_visited=5, total_known_situations=10,
    )
    assert set(result.keys()) == {"aggregate_score", "breakdown", "purpose", "weights_used"}
    assert 0.0 <= result["aggregate_score"] <= 1.0


def test_unknown_purpose_falls_back_to_trip_weights():
    result = compute_readiness(
        purpose="exam", total_turns=10, repair_counts={},
        distinct_situations_visited=5, total_known_situations=10,
    )
    assert result["weights_used"] == WEIGHTS_BY_PURPOSE["trip"]


def test_three_synthetic_learners_rank_correctly_for_trip_purpose():
    high_performer = compute_readiness(
        purpose="trip", total_turns=20, repair_counts={"lexical": 1},
        distinct_situations_visited=9, total_known_situations=10,
    )
    mid_performer = compute_readiness(
        purpose="trip", total_turns=20, repair_counts={"lexical": 4, "grammar": 3, "register": 2},
        distinct_situations_visited=5, total_known_situations=10,
    )
    low_performer = compute_readiness(
        purpose="trip", total_turns=20, repair_counts={"lexical": 10, "grammar": 8, "register": 6, "comprehension": 5},
        distinct_situations_visited=1, total_known_situations=10,
    )

    assert high_performer["aggregate_score"] > mid_performer["aggregate_score"] > low_performer["aggregate_score"]


def test_three_synthetic_learners_rank_correctly_for_casual_purpose():
    high_performer = compute_readiness(
        purpose="casual", total_turns=20, repair_counts={"comprehension": 1},
        distinct_situations_visited=8, total_known_situations=9,
    )
    mid_performer = compute_readiness(
        purpose="casual", total_turns=20, repair_counts={"register": 5, "lexical": 3},
        distinct_situations_visited=4, total_known_situations=9,
    )
    low_performer = compute_readiness(
        purpose="casual", total_turns=20, repair_counts={"register": 10, "lexical": 8, "grammar": 6, "comprehension": 4},
        distinct_situations_visited=1, total_known_situations=9,
    )

    assert high_performer["aggregate_score"] > mid_performer["aggregate_score"] > low_performer["aggregate_score"]
