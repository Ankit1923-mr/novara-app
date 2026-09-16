"""
NOVARA Repair Engine v1 — Review 3 scope.

Rule-based error classifier (not ML) + a fixed strategy lookup table.
Standalone module, no FastAPI import, independently testable; wired
into /repair and /conversation.

classify_error(utterance, expected_pattern) -> one of:
    "comprehension" — learner didn't understand / signaled confusion
    "lexical"        — wrong or missing content word (vocabulary gap)
    "register"       — wrong formality level (tú vs. usted, slang vs. formal)
    "grammar"        — close to correct but conjugation/word-order/accent off

select_strategy(error_type) -> "clarify" | "rephrase" | "hint"
generate_repair(error_type, strategy, expected_pattern) -> in-context repair text

Baseline (Review 3): >=70% classification accuracy on a 20-utterance
hand-labeled test set — see tests/test_repair_engine.py.
"""

import re
import unicodedata

COMPREHENSION_MARKERS = [
    "no entiendo", "perdon", "perdón", "repite", "repetir", "eh", "mande",
    "disculpa", "que dices", "como dices", "qué dices", "cómo dices",
]

# Informal-register markers (slang) vs. formal (usted-style) — supplementary
# to the tú/usted verb-conjugation pairs below. Rough heuristic, not a full
# conjugation parser — good enough for MVP.
INFORMAL_MARKERS = ["vos", "guay", "flipa", "tio", "tío", "finde", "peli"]
FORMAL_MARKERS = ["usted", "señor", "señora", "podría", "quisiera", "me pone"]

# tú-form -> usted-form pairs for the most common verbs in our scenario set.
# If the learner's utterance uses the tú form where the expected pattern
# uses the matching usted form, that's a register error, not grammar.
VERB_CONJUGATION_PAIRS = [
    ("tienes", "tiene"), ("quieres", "quiere"), ("puedes", "puede"),
    ("eres", "es"), ("vas", "va"), ("dices", "dice"), ("sabes", "sabe"),
]

# Closed-class words stripped out before comparing content words for the
# lexical-gap check — keeps "quiero un té" vs "quiero un café" from
# looking like a near-total match just because the carrier words agree.
STOPWORDS = {
    "un", "una", "el", "la", "los", "las", "por", "favor", "de", "a",
    "al", "del", "que", "qué", "es", "esta", "está", "son", "con", "en",
    "y", "o", "me", "se", "su",
}

STRATEGY_BY_ERROR_TYPE = {
    "comprehension": "clarify",
    "lexical": "hint",
    "register": "rephrase",
    "grammar": "rephrase",
}


def _normalize(text: str) -> str:
    text = text.lower().strip()
    text = "".join(c for c in unicodedata.normalize("NFD", text) if unicodedata.category(c) != "Mn")
    text = re.sub(r"[^\w\s]", "", text)
    return text


def _content_words(text: str) -> set[str]:
    words = _normalize(text).split()
    return {w for w in words if w not in STOPWORDS}


def _content_overlap_ratio(utterance: str, expected_pattern: str) -> float:
    u_content = _content_words(utterance)
    e_content = _content_words(expected_pattern)
    if not e_content:
        return 1.0
    return len(u_content & e_content) / len(e_content)


def _has_register_mismatch(utterance: str, expected_pattern: str) -> bool:
    normalized_utterance = _normalize(utterance)
    normalized_expected = _normalize(expected_pattern)

    for informal, formal in VERB_CONJUGATION_PAIRS:
        if informal in normalized_utterance.split() and formal in normalized_expected.split():
            return True

    has_informal_marker = any(marker in utterance.lower() for marker in INFORMAL_MARKERS)
    has_formal_expected = any(marker in expected_pattern.lower() for marker in FORMAL_MARKERS)
    return has_informal_marker and has_formal_expected


def classify_error(utterance: str, expected_pattern: str) -> str:
    normalized = _normalize(utterance)

    if not normalized or len(normalized) <= 3:
        return "comprehension"

    # Register check first: it's a more specific structural signal
    # (a matched tú/usted verb pair) than the generic keyword markers
    # below, some of which (e.g. "repetir") can legitimately appear in
    # an otherwise well-formed but wrong-register request.
    if _has_register_mismatch(utterance, expected_pattern):
        return "register"

    if any(marker in normalized for marker in COMPREHENSION_MARKERS):
        return "comprehension"

    overlap = _content_overlap_ratio(utterance, expected_pattern)
    if overlap < 0.6:
        return "lexical"

    return "grammar"


def select_strategy(error_type: str) -> str:
    return STRATEGY_BY_ERROR_TYPE.get(error_type, "clarify")


def generate_repair(error_type: str, strategy: str, expected_pattern: str) -> str:
    if strategy == "clarify":
        return "¿Puede repetir, por favor?"
    if strategy == "hint":
        return f"Se dice: \"{expected_pattern}\"."
    if strategy == "rephrase":
        return f"Prueba con: \"{expected_pattern}\"."
    return expected_pattern


def repair(utterance: str, expected_pattern: str) -> dict:
    """Full pipeline: classify -> select strategy -> generate repair text.
    Matches the /repair contract response shape."""
    error_type = classify_error(utterance, expected_pattern)
    strategy = select_strategy(error_type)
    repair_text = generate_repair(error_type, strategy, expected_pattern)
    return {
        "error_type": error_type,
        "strategy": strategy,
        "repair_text": repair_text,
    }


# Content-overlap band for deciding whether a learner's free-form turn is
# "attempting" a known graph phrase (worth repairing) vs. a legitimate
# open-ended reply the conversation doesn't script for (skip repair —
# the AI Conversation Partner allows free dialogue, not just memorized
# lines, so most turns will have low overlap with any single graph phrase
# and that's expected, not an error).
REPAIR_TRIGGER_MIN_OVERLAP = 0.3
REPAIR_TRIGGER_MAX_OVERLAP = 0.85  # above this, close enough — no repair needed

# Pace-adjusted leniency: a learner at a relaxed pace is more forgiven for
# being "close enough" (lower bar to skip repair) and more of their attempts
# get recognized as worth correcting at all (lower floor); a learner at a
# fast pace is held to a stricter standard on both ends. Steady pace uses
# the plain defaults above.
RELAXED_TRIGGER_BOUNDS = (0.2, 0.75)
FAST_TRIGGER_BOUNDS = (0.35, 0.9)


def _trigger_bounds(pace_preference: float | None) -> tuple[float, float]:
    if pace_preference is None:
        return REPAIR_TRIGGER_MIN_OVERLAP, REPAIR_TRIGGER_MAX_OVERLAP
    if pace_preference <= 0.75:
        return RELAXED_TRIGGER_BOUNDS
    if pace_preference >= 1.5:
        return FAST_TRIGGER_BOUNDS
    return REPAIR_TRIGGER_MIN_OVERLAP, REPAIR_TRIGGER_MAX_OVERLAP


def detect_repair(utterance: str, candidate_patterns: list[str], pace_preference: float | None = None) -> dict | None:
    """Finds the graph phrase closest to `utterance` among
    `candidate_patterns` and decides whether it's worth repairing.

    Returns None when either: utterance matches a candidate closely
    enough (correct), or matches none closely enough to be a clear
    attempt at any of them (treated as free-form conversation, not an
    error against this scenario's known phrases).
    """
    if not candidate_patterns:
        return None

    normalized = _normalize(utterance)
    if not normalized or len(normalized) <= 3:
        # comprehension signals ("eh", "", "¿?") always worth flagging,
        # regardless of overlap with any candidate
        return repair(utterance, candidate_patterns[0])

    if any(marker in normalized for marker in COMPREHENSION_MARKERS):
        return repair(utterance, candidate_patterns[0])

    best_pattern = None
    best_overlap = -1.0
    for pattern in candidate_patterns:
        overlap = _content_overlap_ratio(utterance, pattern)
        if overlap > best_overlap:
            best_overlap = overlap
            best_pattern = pattern

    min_overlap, max_overlap = _trigger_bounds(pace_preference)
    if best_overlap >= max_overlap:
        return None  # close enough, no repair
    if best_overlap < min_overlap:
        return None  # not a recognizable attempt at any known phrase — free-form reply
    return repair(utterance, best_pattern)
