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

# Automatic pace adjustment: distinct from pace_score above (which only
# reacts to response time). This reacts to correctness - repeated mistakes
# slow the learner down, a sustained correct streak speeds them up - and
# writes directly to pace_preference, the same field the learner's manual
# +/- control uses, so there's one number every module reads regardless of
# who moved it last.
REPAIRS_BEFORE_SLOWING_DOWN = 2
CORRECT_STREAK_BEFORE_SPEEDING_UP = 10
PACE_STEP = 0.25
PACE_MIN = 0.5
PACE_MAX = 2.0


def next_pace_preference(
    current_pace_preference: float,
    consecutive_correct: int,
    consecutive_repairs: int,
    repair_triggered: bool,
) -> dict:
    """One turn's worth of automatic pace adjustment.

    Returns {pace_preference, consecutive_correct, consecutive_repairs,
    pace_changed, pace_change_reason}. The counters always update; the
    preference itself only moves once a streak threshold is actually
    crossed, at which point the triggering counter resets to 0 so the next
    adjustment requires a fresh streak, not one more turn past the
    threshold.
    """
    new_pace = current_pace_preference
    reason: Optional[str] = None

    if repair_triggered:
        new_correct = 0
        new_repairs = consecutive_repairs + 1
        if new_repairs >= REPAIRS_BEFORE_SLOWING_DOWN:
            new_pace = max(PACE_MIN, round(current_pace_preference - PACE_STEP, 2))
            new_repairs = 0
            if new_pace != current_pace_preference:
                reason = f"slowed down after {REPAIRS_BEFORE_SLOWING_DOWN} mistakes in a row"
    else:
        new_repairs = 0
        new_correct = consecutive_correct + 1
        if new_correct >= CORRECT_STREAK_BEFORE_SPEEDING_UP:
            new_pace = min(PACE_MAX, round(current_pace_preference + PACE_STEP, 2))
            new_correct = 0
            if new_pace != current_pace_preference:
                reason = f"sped up after {CORRECT_STREAK_BEFORE_SPEEDING_UP} correct turns in a row"

    return {
        "pace_preference": new_pace,
        "consecutive_correct": new_correct,
        "consecutive_repairs": new_repairs,
        "pace_changed": reason is not None,
        "pace_change_reason": reason,
    }


def _ema(old_value: float, new_signal: float, alpha: float = EMA_ALPHA) -> float:
    return alpha * new_signal + (1 - alpha) * old_value


def _pace_signal_from_response_time(response_time_ms: int) -> float:
    if response_time_ms <= FAST_MS:
        return 1.0
    if response_time_ms >= SLOW_MS:
        return 0.0
    # linear interpolation between the two bounds
    return 1.0 - (response_time_ms - FAST_MS) / (SLOW_MS - FAST_MS)


# Public alias: main.py needs this to build an atomic SQL EMA update
# (see /conversation) without duplicating the pace-signal formula.
pace_signal_from_response_time = _pace_signal_from_response_time


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
