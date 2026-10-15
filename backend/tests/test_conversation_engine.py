"""
Independent tests for the AI Conversation Partner (conversation_engine.py).
The OpenRouter call itself is mocked via a fake client injected into
handle_turn/call_llm - no live API key needed, and no network calls in CI.

History is persisted via ConversationTurnModel (see db_models.py) - each
test uses its own db session from the shared in-memory test database
(see conftest.py), and clears any rows a prior test left for the same
learner_id/scenario_id before asserting.
"""

import os
import tempfile

from app.conversation_engine import build_system_prompt, handle_turn, get_history, reset_history
from app.db import get_session_factory, reset_engine_cache, init_db, get_engine


TRIP_SCENARIO = {
    "scenario_id": "scenario-cafe-test",
    "purpose": "trip",
    "title": "Ordering coffee",
    "setting": "A café in Madrid",
    "situation_tags": ["cafe", "ordering"],
    "opening_line": "Hola, ¿qué le pongo?",
}

CASUAL_SCENARIO = {
    "scenario_id": "scenario_smalltalk-test",
    "purpose": "casual",
    "title": "Making small talk",
    "setting": "A casual conversation with a local",
    "situation_tags": ["smalltalk"],
    "opening_line": "¿Qué tal el finde?",
}


class FakeOpenRouterClient:
    """Minimal stand-in matching the OpenAI-compatible shape call_llm
    expects: client.chat.completions.create(...).choices[0].message.content"""

    def __init__(self, canned_reply="¿Para aquí o para llevar?"):
        self.canned_reply = canned_reply
        self.calls = []

    class _Message:
        def __init__(self, content):
            self.content = content

    class _Choice:
        def __init__(self, content):
            self.message = FakeOpenRouterClient._Message(content)

    class _Response:
        def __init__(self, content):
            self.choices = [FakeOpenRouterClient._Choice(content)]

    class _Completions:
        def __init__(self, outer):
            self.outer = outer

        def create(self, model, max_tokens, messages):
            self.outer.calls.append({"model": model, "messages": messages})
            return FakeOpenRouterClient._Response(self.outer.canned_reply)

    class _Chat:
        def __init__(self, outer):
            self.completions = FakeOpenRouterClient._Completions(outer)

    @property
    def chat(self):
        return FakeOpenRouterClient._Chat(self)


def _db():
    return get_session_factory()()


def setup_function():
    # Each test uses a distinct learner_id, but clear defensively in case
    # a previous failed run left rows behind for one of these ids.
    db = _db()
    try:
        for learner_id in ("learner_a", "learner_b", "learner_c", "learner_d", "learner_e1", "learner_e2"):
            reset_history(db, learner_id, TRIP_SCENARIO["scenario_id"])
    finally:
        db.close()


def test_build_system_prompt_includes_scenario_setting():
    prompt = build_system_prompt(TRIP_SCENARIO)
    assert "café in Madrid" in prompt
    assert "Ordering coffee" in prompt


def test_build_system_prompt_differs_by_purpose_register():
    trip_prompt = build_system_prompt(TRIP_SCENARIO)
    casual_prompt = build_system_prompt(CASUAL_SCENARIO)
    assert "formal" in trip_prompt.lower()
    assert "informal" in casual_prompt.lower()
    assert trip_prompt != casual_prompt


def test_handle_turn_returns_reply_and_calls_llm_with_system_prompt():
    db = _db()
    fake_client = FakeOpenRouterClient(canned_reply="¿Para aquí o para llevar?")
    reply, appended = handle_turn(db, "learner_a", TRIP_SCENARIO, "Quiero un café.", turn_number=1, client=fake_client)
    db.commit()

    assert reply == "¿Para aquí o para llevar?"
    assert len(appended) == 3  # opening line (first turn) + user msg + assistant reply
    assert len(fake_client.calls) == 1
    system_message = fake_client.calls[0]["messages"][0]
    assert system_message["role"] == "system"
    assert "café in Madrid" in system_message["content"]
    db.close()


def test_handle_turn_seeds_history_with_opening_line_on_first_turn():
    db = _db()
    fake_client = FakeOpenRouterClient()
    handle_turn(db, "learner_b", TRIP_SCENARIO, "Quiero un café.", turn_number=1, client=fake_client)
    db.commit()

    history = get_history(db, "learner_b", TRIP_SCENARIO["scenario_id"])
    assert history[0] == {"role": "assistant", "content": TRIP_SCENARIO["opening_line"]}
    assert history[1] == {"role": "user", "content": "Quiero un café."}
    db.close()


def test_handle_turn_accumulates_history_across_multiple_turns():
    db = _db()
    fake_client = FakeOpenRouterClient(canned_reply="Claro.")
    handle_turn(db, "learner_c", TRIP_SCENARIO, "Quiero un café.", turn_number=1, client=fake_client)
    db.commit()
    handle_turn(db, "learner_c", TRIP_SCENARIO, "Para llevar.", turn_number=2, client=fake_client)
    db.commit()

    history = get_history(db, "learner_c", TRIP_SCENARIO["scenario_id"])
    # opening line + (user, assistant) x2 = 5 entries
    assert len(history) == 5
    assert history[-1] == {"role": "assistant", "content": "Claro."}
    db.close()


def test_reset_history_clears_conversation():
    db = _db()
    fake_client = FakeOpenRouterClient()
    handle_turn(db, "learner_d", TRIP_SCENARIO, "Hola.", turn_number=1, client=fake_client)
    db.commit()
    assert get_history(db, "learner_d", TRIP_SCENARIO["scenario_id"]) != []

    reset_history(db, "learner_d", TRIP_SCENARIO["scenario_id"])
    assert get_history(db, "learner_d", TRIP_SCENARIO["scenario_id"]) == []
    db.close()


def test_separate_learners_have_independent_histories():
    db = _db()
    fake_client = FakeOpenRouterClient()
    handle_turn(db, "learner_e1", TRIP_SCENARIO, "Hola.", turn_number=1, client=fake_client)
    db.commit()
    handle_turn(db, "learner_e2", TRIP_SCENARIO, "Buenos días.", turn_number=1, client=fake_client)
    db.commit()

    h1 = get_history(db, "learner_e1", TRIP_SCENARIO["scenario_id"])
    h2 = get_history(db, "learner_e2", TRIP_SCENARIO["scenario_id"])
    assert h1 != h2
    db.close()


def test_failed_turn_rolls_back_without_committing_partial_history():
    """handle_turn only flushes (never commits) - if the caller rolls back
    instead of committing (e.g. because something downstream failed, same
    pattern as main.py's /conversation handler), none of this call's turns
    should be persisted."""
    db = _db()
    fake_client = FakeOpenRouterClient()
    handle_turn(db, "learner_rollback_test", TRIP_SCENARIO, "Hola.", turn_number=1, client=fake_client)
    db.rollback()

    fresh_db = _db()
    try:
        assert get_history(fresh_db, "learner_rollback_test", TRIP_SCENARIO["scenario_id"]) == []
    finally:
        fresh_db.close()
    db.close()


def test_history_survives_a_new_engine_against_the_same_database_file():
    """Proves persistence beyond this process's in-memory working set:
    writes through one engine bound to a temp SQLITE FILE (not :memory:,
    which is tied to one connection's lifetime), fully disposes that
    engine and every cached session factory, then opens a brand-new
    engine against the same file - simulating a fresh process/backend
    restart - and reads the turns back from there."""
    fd, db_path = tempfile.mkstemp(suffix=".db")
    os.close(fd)
    try:
        original_url = os.environ.get("DATABASE_URL")
        try:
            os.environ["DATABASE_URL"] = f"sqlite:///{db_path}"
            reset_engine_cache()
            init_db()

            db1 = get_session_factory()()
            fake_client = FakeOpenRouterClient(canned_reply="Hola.")
            handle_turn(db1, "restart_learner", TRIP_SCENARIO, "Hola.", turn_number=1, client=fake_client)
            db1.commit()
            db1.close()
            get_engine().dispose()  # fully drop this connection - nothing of it survives in memory

            # Simulate a fresh process: brand-new engine/session factory
            # against the same file, with no shared state from db1 at all.
            reset_engine_cache()
            db2 = get_session_factory()()
            history = get_history(db2, "restart_learner", TRIP_SCENARIO["scenario_id"])
            assert history == [
                {"role": "assistant", "content": TRIP_SCENARIO["opening_line"]},
                {"role": "user", "content": "Hola."},
                {"role": "assistant", "content": "Hola."},
            ]
            db2.close()
            get_engine().dispose()  # release the file handle before cleanup removes it (Windows locks open files)
        finally:
            if original_url is None:
                os.environ.pop("DATABASE_URL", None)
            else:
                os.environ["DATABASE_URL"] = original_url
            reset_engine_cache()
            os.environ["DATABASE_URL"] = "sqlite:///:memory:"
            reset_engine_cache()
            init_db()
    finally:
        os.remove(db_path)


def test_history_is_ordered_by_insertion_not_by_timestamp():
    """created_at can collide at sub-millisecond resolution between turns
    in the same transaction - ordering must come from insertion order
    (the autoincrement id), not from created_at, or a same-timestamp tie
    could return turns out of conversational order."""
    db = _db()
    fake_client = FakeOpenRouterClient(canned_reply="uno")
    handle_turn(db, "learner_order_test", TRIP_SCENARIO, "primero", turn_number=1, client=fake_client)
    db.commit()
    fake_client2 = FakeOpenRouterClient(canned_reply="dos")
    handle_turn(db, "learner_order_test", TRIP_SCENARIO, "segundo", turn_number=2, client=fake_client2)
    db.commit()

    history = get_history(db, "learner_order_test", TRIP_SCENARIO["scenario_id"])
    contents = [h["content"] for h in history]
    assert contents == [
        TRIP_SCENARIO["opening_line"], "primero", "uno", "segundo", "dos",
    ]
    db.close()


# --- vocabulary reaches the AI partner's system prompt (learning objective) ---

def test_system_prompt_includes_taught_vocabulary_when_provided():
    vocab = [{"phrase": "Quiero un café, por favor.", "meaning": "I'd like a coffee, please.",
              "cultural_note": "Standard polite ordering phrase."}]
    prompt = build_system_prompt(TRIP_SCENARIO, vocabulary=vocab)
    assert "Quiero un café, por favor." in prompt
    assert "I'd like a coffee, please." in prompt
    assert "Standard polite ordering phrase." in prompt


def test_system_prompt_caps_vocabulary_at_three_phrases():
    vocab = [
        {"phrase": f"Frase {i}", "meaning": f"Meaning {i}", "cultural_note": f"Note {i}"}
        for i in range(5)
    ]
    prompt = build_system_prompt(TRIP_SCENARIO, vocabulary=vocab)
    assert "Frase 0" in prompt and "Frase 2" in prompt
    assert "Frase 3" not in prompt and "Frase 4" not in prompt


def test_system_prompt_unchanged_when_no_vocabulary_given():
    # Backward compatible - no vocabulary param at all must produce the
    # exact same prompt as before this feature existed.
    without_vocab_param = build_system_prompt(TRIP_SCENARIO)
    with_none = build_system_prompt(TRIP_SCENARIO, vocabulary=None)
    with_empty = build_system_prompt(TRIP_SCENARIO, vocabulary=[])
    assert without_vocab_param == with_none == with_empty
    assert "naturally give the learner" not in without_vocab_param


def test_system_prompt_allows_brief_explanations_without_full_prohibition():
    # The old prompt flatly forbade explaining anything - conflicts with
    # NOVARA actually teaching. Must now permit a brief, in-character
    # note, while still warning against turning every reply into a lecture.
    prompt = build_system_prompt(TRIP_SCENARIO)
    assert "do not break character or" not in prompt.lower()
    assert "grammar lecture" in prompt.lower()
    assert "briefly" in prompt.lower()
