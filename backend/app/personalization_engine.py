"""
NOVARA Personalization Engine v1 — Review 3 scope.

Maintains two rolling per-learner scores, updated after every
conversation turn via an exponential moving average (EMA):

    pace_score       — how quickly the learner responds (0 = slow, 1 = fast)
    confidence_score — how often the learner's turns need repair (0 = low, 1 = high)

Standalone module, no FastAPI import, independently testable; wired
into /conversation, which calls update_scores() after every turn and
writes the result back onto the learner record.

MVP scope note: modality preference and a full personal retention
curve (spaced-repetition scheduling) are NOT implemented here - only
pace and confidence, per the Review 3 baseline. Documented as future
work, not silently dropped.
"""

from typing import Optional

EMA_ALPHA = 0.3  # weight given to the newest signal vs. accumulated history

# response_time_ms bounds used to normalize a raw timing into a 0-1 pace
# signal. Below FAST_MS -> pace signal 1.0 (fast); above SLOW_MS -> 0.0.
FAST_MS = 2000
SLOW_MS = 15000

CONFIDENT_SIGNAL = 0.8   # turn needed no repair
UNCONFIDENT_SIGNAL = 0.2  # turn needed repair


def _ema(old_value: float, new_signal: float, alpha: float = EMA_ALPHA) -> float:
    return alpha * new_signal + (1 - alpha) * old_value


def _pace_signal_from_response_time(response_time_ms: int) -> float:
    if response_time_ms <= FAST_MS:
        return 1.0
    if response_time_ms >= SLOW_MS:
        return 0.0
    # linear interpolation between the two bounds
    return 1.0 - (response_time_ms - FAST_MS) / (SLOW_MS - FAST_MS)


def update_scores(
    pace_score: float,
    confidence_score: float,
    repair_triggered: bool,
    response_time_ms: Optional[int] = None,
) -> dict:
    """Returns updated {pace_score, confidence_score} after one turn.

    response_time_ms is optional — if the client doesn't send it (not
    every turn will), pace_score is left unchanged rather than nudged
    toward a meaningless neutral value.
    """
    new_pace = pace_score
    if response_time_ms is not None:
        pace_signal = _pace_signal_from_response_time(response_time_ms)
        new_pace = _ema(pace_score, pace_signal)

    confidence_signal = UNCONFIDENT_SIGNAL if repair_triggered else CONFIDENT_SIGNAL
    new_confidence = _ema(confidence_score, confidence_signal)

    return {
        "pace_score": round(new_pace, 4),
        "confidence_score": round(new_confidence, 4),
    }
