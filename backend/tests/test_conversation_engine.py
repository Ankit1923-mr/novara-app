"""
Independent tests for the AI Conversation Partner (conversation_engine.py).
The OpenRouter call itself is mocked via a fake client injected into
handle_turn/call_llm - no live API key needed, and no network calls in CI.
"""

from app.conversation_engine import build_system_prompt, handle_turn, get_history, reset_history, HISTORY


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


def setup_function():
    HISTORY.clear()


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
    fake_client = FakeOpenRouterClient(canned_reply="¿Para aquí o para llevar?")
    reply, appended = handle_turn("learner_a", TRIP_SCENARIO, "Quiero un café.", turn_number=1, client=fake_client)

    assert reply == "¿Para aquí o para llevar?"
    assert len(appended) == 3  # opening line (first turn) + user msg + assistant reply
    assert len(fake_client.calls) == 1
    system_message = fake_client.calls[0]["messages"][0]
    assert system_message["role"] == "system"
    assert "café in Madrid" in system_message["content"]


def test_handle_turn_seeds_history_with_opening_line_on_first_turn():
    fake_client = FakeOpenRouterClient()
    handle_turn("learner_b", TRIP_SCENARIO, "Quiero un café.", turn_number=1, client=fake_client)

    history = get_history("learner_b", TRIP_SCENARIO["scenario_id"])
    assert history[0] == {"role": "assistant", "content": TRIP_SCENARIO["opening_line"]}
    assert history[1] == {"role": "user", "content": "Quiero un café."}


def test_handle_turn_accumulates_history_across_multiple_turns():
    fake_client = FakeOpenRouterClient(canned_reply="Claro.")
    handle_turn("learner_c", TRIP_SCENARIO, "Quiero un café.", turn_number=1, client=fake_client)
    handle_turn("learner_c", TRIP_SCENARIO, "Para llevar.", turn_number=2, client=fake_client)

    history = get_history("learner_c", TRIP_SCENARIO["scenario_id"])
    # opening line + (user, assistant) x2 = 5 entries
    assert len(history) == 5
    assert history[-1] == {"role": "assistant", "content": "Claro."}


def test_reset_history_clears_conversation():
    fake_client = FakeOpenRouterClient()
    handle_turn("learner_d", TRIP_SCENARIO, "Hola.", turn_number=1, client=fake_client)
    assert get_history("learner_d", TRIP_SCENARIO["scenario_id"]) != []

    reset_history("learner_d", TRIP_SCENARIO["scenario_id"])
    assert get_history("learner_d", TRIP_SCENARIO["scenario_id"]) == []


def test_separate_learners_have_independent_histories():
    fake_client = FakeOpenRouterClient()
    handle_turn("learner_e1", TRIP_SCENARIO, "Hola.", turn_number=1, client=fake_client)
    handle_turn("learner_e2", TRIP_SCENARIO, "Buenos días.", turn_number=1, client=fake_client)

    h1 = get_history("learner_e1", TRIP_SCENARIO["scenario_id"])
    h2 = get_history("learner_e2", TRIP_SCENARIO["scenario_id"])
    assert h1 != h2
