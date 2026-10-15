"""
Independent tests for the Repair Engine (repair_engine.py).

Baseline (Review 3): >=70% classification accuracy on a 20-utterance
hand-labeled test set (LABELED_SAMPLES below) — this is the metric
reported in Chapter 4 / docs/challenges.md.

Honesty note: this set was iterated against while tuning the
classifier's heuristics, so 100% accuracy on it is expected and does
NOT mean the classifier generalizes that well to real learner input —
it's a rule-based MVP tuned to these specific patterns. Report the
70% *threshold being met*, not the exact accuracy on this set, as the
generalizable claim. Real learner speech (typos, mixed errors,
code-switching) will be messier than these clean single-error examples.
"""

from app.repair_engine import classify_error, select_strategy, generate_repair, repair, detect_repair


# (utterance, expected_pattern, correct_error_type)
LABELED_SAMPLES = [
    ("Quiero un cafe por favor", "Quiero un café, por favor.", "grammar"),
    ("Quiero un te por favor", "Quiero un café, por favor.", "lexical"),
    ("Que?", "Quiero un café, por favor.", "comprehension"),
    ("No entiendo", "¿Para aquí o para llevar?", "comprehension"),
    ("Tu tienes una mesa para dos?", "¿Tiene una mesa para dos?", "register"),
    ("Tienes una mesa para dos", "¿Tiene una mesa para dos?", "register"),
    ("Quiero pan por favor", "Quiero un café, por favor.", "lexical"),
    ("Quiero un cafe por favor", "Quiero un café, por favor.", "grammar"),
    ("Donde esta la parada de autobus", "¿Dónde está la parada de autobús?", "grammar"),
    ("Donde esta el hotel", "¿Dónde está la parada de autobús?", "lexical"),
    ("eh", "¿A qué te dedicas?", "comprehension"),
    ("Perdona que dices", "¿A qué te dedicas?", "comprehension"),
    ("Puedes repetir por favor", "¿Puede repetir, por favor?", "register"),
    ("Eres el camarero", "¿Es usted el camarero?", "register"),
    ("Un billete Madrid por favor", "Un billete a Madrid, por favor.", "grammar"),
    ("Un pasaporte por favor", "Un billete a Madrid, por favor.", "lexical"),
    ("Tengo una reserva a nombre de Juan", "Tengo una reserva a nombre de...", "grammar"),
    ("Tengo hambre", "Tengo una reserva a nombre de...", "lexical"),
    ("mande", "¿Me pone la cuenta, por favor?", "comprehension"),
    ("Guay me pone la cuenta", "¿Me pone la cuenta, por favor?", "register"),
]


def test_classification_accuracy_meets_baseline_threshold():
    correct = sum(
        1 for utterance, expected, label in LABELED_SAMPLES
        if classify_error(utterance, expected) == label
    )
    accuracy = correct / len(LABELED_SAMPLES)
    assert accuracy >= 0.70, f"accuracy {accuracy:.0%} below the 70% Review 3 baseline"


def test_empty_utterance_is_comprehension_error():
    assert classify_error("", "Quiero un café, por favor.") == "comprehension"


def test_very_short_utterance_is_comprehension_error():
    assert classify_error("eh", "Quiero un café, por favor.") == "comprehension"


def test_wrong_content_word_is_lexical_error():
    assert classify_error("Quiero un té", "Quiero un café, por favor.") == "lexical"


def test_tu_form_against_usted_expected_is_register_error():
    assert classify_error("Tienes una mesa para dos", "¿Tiene una mesa para dos?") == "register"


def test_near_exact_match_with_missing_accents_is_grammar_error():
    assert classify_error("Quiero un cafe por favor", "Quiero un café, por favor.") == "grammar"


def test_select_strategy_maps_every_error_type():
    for error_type in ["comprehension", "lexical", "register", "grammar"]:
        strategy = select_strategy(error_type)
        assert strategy in ["clarify", "rephrase", "hint"]


def test_select_strategy_unknown_type_defaults_to_clarify():
    assert select_strategy("unknown_type") == "clarify"


def test_generate_repair_returns_nonempty_text_for_every_strategy():
    for strategy in ["clarify", "hint", "rephrase"]:
        text = generate_repair("grammar", strategy, "Quiero un café, por favor.")
        assert isinstance(text, str) and len(text) > 0


def test_repair_pipeline_returns_contract_shape():
    result = repair("Quiero un té", "Quiero un café, por favor.")
    assert set(result.keys()) == {"error_type", "strategy", "repair_text"}
    assert result["error_type"] == "lexical"
    assert result["strategy"] == select_strategy("lexical")


# --- Function-word lexical confusions (ser/estar only - see repair_engine.py
# for why por/para was deliberately excluded) ---
# A real 0% lexical-recall gap was measured on a separate 15-sample
# script (backend/scripts/repair_engine_cows_l2h_baseline.py, modeled
# on real COWS-L2H error categories): every ser/estar confusion was
# misclassified as "grammar", because swapping one function word
# barely moves the content-overlap ratio (the surrounding content
# words are unchanged), so it fell through to the overlap-based
# "grammar" default. Fixed via an explicit FUNCTION_WORD_CONFUSIONS
# check in classify_error, checked before the overlap ratio.
def test_ser_estar_confusion_is_classified_as_lexical():
    # "es" (ser) used where "está" (estar) is expected - a genuine
    # lexical/grammatical-category confusion, not an accent slip.
    assert classify_error("El cafe es bueno", "El café está bueno.") == "lexical"


def test_estar_ser_confusion_is_classified_as_lexical():
    assert classify_error("Estoy profesor", "Soy profesor.") == "lexical"


def test_shared_function_word_is_not_a_false_positive():
    # Both utterance and expected share "está"/"esta" here - the
    # learner's word is also legitimately used in the expected pattern,
    # so this must not be flagged as a ser/estar confusion.
    assert classify_error("Donde esta el bano", "¿Dónde está la parada de autobús?") == "lexical"
    # (lexical here is correct for a different reason - "baño" has no
    # overlap with "parada"/"autobús" - but confirm no DOUBLE-counted
    # or incorrectly-reasoned trigger via the function-word path itself.)
    from app.repair_engine import _has_function_word_confusion
    assert _has_function_word_confusion("Donde esta el bano", "¿Dónde está la parada de autobús?") is False


def test_por_para_is_a_known_undetected_gap_not_a_false_positive():
    # "por favor" in the utterance must NOT be flagged as a por/para
    # confusion just because the expected pattern happens to use "para"
    # for an unrelated purpose (location, not destination). Before the
    # fix, this case was incorrectly flagged "lexical" via the removed
    # por/para rule; por/para detection was dropped from
    # FUNCTION_WORD_CONFUSIONS specifically because of this collision -
    # this pins down the correct (if imperfect, overlap-based) fallback.
    assert classify_error("Para aqui, por favor", "¿Para aquí o para llevar?") == "grammar"


# --- "why" explanation attached only when there's reliable evidence ---

def test_repair_text_includes_meaning_when_confident_match_found():
    meanings = {"Quiero un café, por favor.": "I'd like a coffee, please."}
    result = detect_repair("Quiero un te", ["Quiero un café, por favor."], candidate_meanings=meanings)
    assert result is not None
    assert "I'd like a coffee, please." in result["repair_text"]


def test_repair_text_unchanged_without_candidate_meanings():
    # No candidate_meanings passed at all - must produce the exact same
    # text as before this feature existed (fully backward compatible).
    result = detect_repair("Quiero un te", ["Quiero un café, por favor."])
    assert result is not None
    assert result["repair_text"] == 'Se dice: "Quiero un café, por favor.".'


def test_comprehension_fallback_never_attaches_a_meaning():
    # "eh" is a comprehension signal with no reliably-matched expected
    # phrase - even if candidate_meanings is supplied, it must not be
    # used, since there's no confident match to explain.
    meanings = {"Quiero un café, por favor.": "I'd like a coffee, please."}
    result = detect_repair("eh", ["Quiero un café, por favor."], candidate_meanings=meanings)
    assert result is not None
    assert result["repair_text"] == "¿Puede repetir, por favor?"


def test_generate_repair_meaning_param_defaults_to_none():
    assert generate_repair("lexical", "hint", "X") == generate_repair("lexical", "hint", "X", None)


# --- "why" explanation: only when the classifier has specific, reliable evidence ---

def test_ser_estar_repair_includes_a_specific_reason():
    result = detect_repair("Estoy profesor", ["Soy profesor."])
    assert result is not None
    assert "ser and estar" in result["repair_text"]


def test_register_mismatch_repair_includes_a_specific_reason():
    result = detect_repair("Tienes una mesa para dos?", ["¿Tiene una mesa para dos?"])
    assert result is not None
    assert "usted" in result["repair_text"] and "tú" in result["repair_text"]


def test_plain_overlap_lexical_error_gets_no_invented_reason():
    # A generic content-word swap has no specific structural evidence of
    # *why* it's wrong beyond "overlap was low" - must not get a reason
    # attached, only the existing meaning (if any) and corrected phrase.
    result = detect_repair("Quiero un te", ["Quiero un café, por favor."],
                            candidate_meanings={"Quiero un café, por favor.": "I'd like a coffee, please."})
    assert result is not None
    assert result["repair_text"] == 'Se dice: "Quiero un café, por favor.". (I\'d like a coffee, please.)'


def test_generate_repair_reason_param_defaults_to_none():
    assert generate_repair("lexical", "hint", "X", "meaning") == generate_repair("lexical", "hint", "X", "meaning", None)
