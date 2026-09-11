"""
NOVARA Country Readiness Scoring Engine — Review 3 scope.

Computes a purpose-weighted readiness score from a learner's actual
interaction history (turns, repairs by error type, distinct situations
practiced) — not a hardcoded number. Standalone module, no FastAPI
import, independently testable; wired into /readiness.

Four competency dimensions, each in [0, 1]:
    language_accuracy        — turns free of lexical/grammar errors
    repair_success_rate      — turns free of ANY repair (comprehension included)
    register_appropriateness — turns free of register errors
    transfer_success         — breadth: how many of this purpose's known
                                situations the learner has actually practiced,
                                not just repetitions of one scenario

score = sum(weight_i * metric_i), weights are purpose-specific (see
WEIGHTS_BY_PURPOSE below for the rationale behind each).
"""

from typing import Optional

# Purpose-specific weights. Each set sums to 1.0.
#
# Trip: a traveler needs to function correctly across MANY different
# unplanned situations (airport, hotel, transport, restaurant) usually
# under time pressure, and gets little benefit from any single situation
# mastered in isolation — so transfer (breadth across situations) is
# weighted heaviest, and register matters least (tourists are forgiven
# formality mistakes more than social/cultural ones).
#
# Casual: a casual learner's goal is comfortable, socially appropriate
# conversation with the SAME kind of people (peers, acquaintances)
# repeatedly — getting the register right (not sounding stiff or rude)
# matters more than covering many disconnected situations, so register
# appropriateness is weighted heaviest here instead.
WEIGHTS_BY_PURPOSE = {
    "trip": {
        "language_accuracy": 0.25,
        "repair_success_rate": 0.25,
        "register_appropriateness": 0.15,
        "transfer_success": 0.35,
    },
    "casual": {
        "language_accuracy": 0.20,
        "repair_success_rate": 0.20,
        "register_appropriateness": 0.35,
        "transfer_success": 0.25,
    },
}

ERROR_TYPES = ["lexical", "grammar", "register", "comprehension"]


def _safe_ratio(numerator: float, denominator: float, default: float = 0.5) -> float:
    """Returns 1 - numerator/denominator, clamped to [0, 1]. Falls back to
    `default` (neutral — not enough data yet, not "failing") when there's
    no denominator to divide by, e.g. a learner with zero turns so far."""
    if denominator <= 0:
        return default
    return max(0.0, min(1.0, 1.0 - (numerator / denominator)))


def compute_metrics(
    total_turns: int,
    repair_counts: dict[str, int],
    distinct_situations_visited: int,
    total_known_situations: int,
) -> dict[str, float]:
    lexical_and_grammar = repair_counts.get("lexical", 0) + repair_counts.get("grammar", 0)
    total_repairs = sum(repair_counts.get(t, 0) for t in ERROR_TYPES)
    register_errors = repair_counts.get("register", 0)

    return {
        "language_accuracy": round(_safe_ratio(lexical_and_grammar, total_turns), 4),
        "repair_success_rate": round(_safe_ratio(total_repairs, total_turns), 4),
        "register_appropriateness": round(_safe_ratio(register_errors, total_turns), 4),
        "transfer_success": round(
            _safe_ratio(
                total_known_situations - distinct_situations_visited,
                total_known_situations,
                default=0.0,  # no known situations at all is a config error, not "neutral"
            ),
            4,
        ) if total_known_situations > 0 else 0.0,
    }


def compute_readiness(
    purpose: str,
    total_turns: int,
    repair_counts: dict[str, int],
    distinct_situations_visited: int,
    total_known_situations: int,
) -> dict:
    """Full pipeline: metrics -> purpose-weighted aggregate score.
    Matches the /readiness contract response shape (minus learner_id,
    added by the caller)."""
    weights = WEIGHTS_BY_PURPOSE.get(purpose, WEIGHTS_BY_PURPOSE["trip"])
    breakdown = compute_metrics(total_turns, repair_counts, distinct_situations_visited, total_known_situations)
    aggregate = round(sum(weights[k] * breakdown[k] for k in weights), 4)

    return {
        "aggregate_score": aggregate,
        "breakdown": breakdown,
        "purpose": purpose,
        "weights_used": weights,
    }
