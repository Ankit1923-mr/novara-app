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
                 client: Optional[object] = None) -> tuple[str, list[dict]]:
    """Returns (reply, appended_messages) — the caller keeps `appended_messages`
    so that if something downstream fails, it can roll back exactly the
    entries THIS call added via remove_turn_from_history(), rather than
    truncating the shared list by length (unsafe: a concurrent sibling
    call for the same learner+scenario may have appended its own turn
    in between, and length-based truncation would silently delete it too)."""
    key = (learner_id, scenario["scenario_id"])
    scenario_id = scenario["scenario_id"]
    history = HISTORY.setdefault(key, [])
    appended: list[dict] = []

    try:
        if turn_number == 1 and not history:
            # Seed with the scenario's opening line as the assistant's first turn
            opening = {"role": "assistant", "content": scenario["opening_line"]}
            history.append(opening)
            appended.append(opening)

        user_msg = {"role": "user", "content": message}
        history.append(user_msg)
        appended.append(user_msg)

        system_prompt = build_system_prompt(scenario)
        reply = call_llm(system_prompt, history, client=client)

        assistant_msg = {"role": "assistant", "content": reply}
        history.append(assistant_msg)
        appended.append(assistant_msg)

        return reply, appended
    except Exception:
        # Self-cleaning: if call_llm raises (e.g. a test or a future
        # caller injects a failure directly into it, bypassing
        # llm_client's own exception handling, which normally never
        # raises), this call must not leave an orphan user message with
        # no matching reply sitting in shared history - a concurrent
        # sibling call for the same learner+scenario could otherwise see
        # it as legitimate prior context. The caller (main.py) can no
        # longer rely on `appended` for cleanup here since we never
        # returned it, so we clean up after ourselves before re-raising.
        remove_turn_from_history(learner_id, scenario_id, appended)
        raise


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


def remove_turn_from_history(learner_id: str, scenario_id: str, messages: list[dict]) -> None:
    """Rolls back exactly the message objects a failed turn appended,
    identified by object identity (not value equality — two different
    turns can easily share identical content, e.g. two learners both
    typing "hola", and value-based removal could delete the wrong one).
    Safe under concurrent turns on the same (learner, scenario): a
    sibling's messages are different objects and are left untouched."""
    key = (learner_id, scenario_id)
    if key not in HISTORY:
        return
    ids_to_remove = {id(m) for m in messages}
    HISTORY[key] = [m for m in HISTORY[key] if id(m) not in ids_to_remove]
