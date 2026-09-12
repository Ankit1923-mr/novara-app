"""
NOVARA AI Conversation Partner — Review 3 scope.

Builds a scenario-grounded system prompt (persona, setting, register)
and drives a multi-turn conversation through llm_client.call_llm().
History is kept per (learner_id, scenario_id) so a learner can hold a
conversation across multiple /conversation calls.

Repair detection/selection is NOT done here — that's the Repair Engine
(task 4). This module only produces the conversational reply.
"""

from typing import Optional
from app.llm_client import call_llm

# (learner_id, scenario_id) -> list of {"role": ..., "content": ...}
HISTORY: dict[tuple[str, str], list[dict]] = {}


def build_system_prompt(scenario: dict) -> str:
    register_note = (
        "Use informal, casual Spanish (tú form, colloquial expressions) "
        "appropriate for a relaxed conversation."
        if scenario.get("purpose") == "casual"
        else "Use polite, neutral-to-formal Spanish (usted where natural) "
        "appropriate for a real-world transactional situation."
    )
    return (
        f"You are role-playing as a native Spanish speaker in this scenario: "
        f"\"{scenario['title']}\", set in {scenario['setting']}. "
        f"{register_note} "
        "Stay in character and in Spanish only. Keep replies short (1-2 sentences), "
        "natural, and consistent with the scenario. Do not break character or "
        "explain grammar — just respond as the person in the scene would."
    )


def handle_turn(learner_id: str, scenario: dict, message: str, turn_number: int,
                 client: Optional[object] = None) -> str:
    key = (learner_id, scenario["scenario_id"])
    history = HISTORY.setdefault(key, [])

    if turn_number == 1 and not history:
        # Seed with the scenario's opening line as the assistant's first turn
        history.append({"role": "assistant", "content": scenario["opening_line"]})

    history.append({"role": "user", "content": message})

    system_prompt = build_system_prompt(scenario)
    reply = call_llm(system_prompt, history, client=client)

    history.append({"role": "assistant", "content": reply})
    return reply


def get_history(learner_id: str, scenario_id: str) -> list[dict]:
    return HISTORY.get((learner_id, scenario_id), [])


def reset_history(learner_id: str, scenario_id: str) -> None:
    HISTORY.pop((learner_id, scenario_id), None)


def reset_all_history_for_learner(learner_id: str) -> None:
    """Called when /profile re-creates a learner (fresh purpose/level/etc.) —
    without this, old conversation turns from a previous profile would
    still be sent to the LLM as context on the learner's next turn, even
    though every other piece of their state was reset."""
    for key in [k for k in HISTORY if k[0] == learner_id]:
        del HISTORY[key]


def history_length(learner_id: str, scenario_id: str) -> int:
    return len(HISTORY.get((learner_id, scenario_id), []))


def truncate_history(learner_id: str, scenario_id: str, keep_length: int) -> None:
    """Rolls back history to what it was before a turn that ultimately
    failed downstream (e.g. a DB commit error after the LLM reply came
    back) — otherwise the in-memory conversation would diverge from the
    persisted state, and the LLM would see a turn that officially never
    happened."""
    key = (learner_id, scenario_id)
    if key in HISTORY:
        HISTORY[key] = HISTORY[key][:keep_length]
