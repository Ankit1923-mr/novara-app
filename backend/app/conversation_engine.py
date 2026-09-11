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
