"""
NOVARA AI Conversation Partner — Review 3 scope.

Builds a scenario-grounded system prompt (persona, setting, register)
and drives a multi-turn conversation through llm_client.call_llm().
History is kept per (learner_id, scenario_id) so a learner can hold a
conversation across multiple /conversation calls.

Repair detection/selection is NOT done here — that's the Repair Engine
(task 4). This module only produces the conversational reply.

History persistence: turns are rows in ConversationTurnModel (Postgres/
SQLite via the caller's db session), not an in-memory dict - a learner's
conversation now survives a backend restart/redeploy. Every function
here takes `db: Session` explicitly and does NOT commit on its own
(except reset_history/reset_all_history_for_learner, which are each a
single complete delete) - handle_turn's inserts are flushed, not
committed, so the caller (main.py's /conversation handler) controls the
transaction boundary and can roll the whole request back atomically
(turns + score updates together) if anything downstream fails, instead
of needing identity-based manual cleanup of a shared in-memory list."""

from typing import Optional
from datetime import datetime, timezone
from sqlalchemy.orm import Session
from app.llm_client import call_llm
from app.db_models import ConversationTurnModel


def _pace_note(pace_preference: Optional[float]) -> str:
    """Translates the learner's pace_preference (0.5-2.0, moved either by
    their own +/- control or by automatic adjustment in main.py) into a
    concrete instruction for reply complexity - this is the cheapest,
    highest-leverage place for pace to actually affect what the learner
    experiences, rather than existing only as a number on a dashboard."""
    if pace_preference is None:
        return ""
    if pace_preference <= 0.75:
        return (
            " The learner is at a relaxed pace — use very simple, short sentences, "
            "common everyday vocabulary, and avoid idioms or regional slang."
        )
    if pace_preference >= 1.5:
        return (
            " The learner is at a fast pace — use natural, native-speed complexity, "
            "including idioms or colloquial phrasing where they fit the scene."
        )
    return ""  # steady (0.75-1.5): no special instruction, current default behavior


def _vocabulary_note(vocabulary: Optional[list[dict]]) -> str:
    """Turns the scenario's existing Knowledge Graph vocabulary (same
    content /scenario already returns) into a conversational objective -
    without this, the AI partner has no reason to ever steer toward the
    phrases the learner was just shown. Capped at 3 phrases to keep the
    prompt focused, not an exhaustive vocabulary dump. Only real,
    already-authored graph content (phrase/meaning/cultural_note) is
    used here - nothing is invented for the prompt."""
    if not vocabulary:
        return ""
    sample = vocabulary[:3]
    lines = "; ".join(f"\"{v['phrase']}\" ({v['meaning']})" for v in sample)
    culture_notes = [v["cultural_note"] for v in sample if v.get("cultural_note")]
    culture_part = ""
    if culture_notes:
        culture_part = (
            f" If it fits naturally, briefly mention relevant cultural context the "
            f"learner should know: {culture_notes[0]}"
        )
    return (
        f" Try to naturally give the learner an opening to use one of these phrases "
        f"during this conversation: {lines}. Don't force it or list them explicitly - "
        f"just steer the conversation toward a moment where one fits.{culture_part}"
    )


def build_system_prompt(scenario: dict, pace_preference: Optional[float] = None,
                         vocabulary: Optional[list[dict]] = None) -> str:
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
        "natural, and consistent with the scenario - respond as the person in the "
        "scene would. If the learner makes a clear mistake or there's a genuinely "
        "useful cultural or politeness point for this situation, you may briefly "
        "and naturally note it in your reply (e.g. a short aside or gentle "
        "correction) - but don't turn this into a grammar lecture or break the "
        "scene to over-explain; a short, friendly note is enough."
        f"{_pace_note(pace_preference)}"
        f"{_vocabulary_note(vocabulary)}"
    )


def get_history(db: Session, learner_id: str, scenario_id: str) -> list[dict]:
    rows = (
        db.query(ConversationTurnModel)
        .filter_by(learner_id=learner_id, scenario_id=scenario_id)
        .order_by(ConversationTurnModel.id)
        .all()
    )
    return [{"role": r.role, "content": r.content} for r in rows]


def _append_turn(db: Session, learner_id: str, scenario_id: str, role: str, content: str) -> None:
    db.add(ConversationTurnModel(
        learner_id=learner_id, scenario_id=scenario_id, role=role, content=content,
        created_at=datetime.now(timezone.utc),
    ))
    db.flush()  # assigns an id and makes this visible to this same transaction's own later queries, without committing


def handle_turn(db: Session, learner_id: str, scenario: dict, message: str, turn_number: int,
                 client: Optional[object] = None, pace_preference: Optional[float] = None,
                 vocabulary: Optional[list[dict]] = None) -> tuple[str, list[dict]]:
    """Returns (reply, appended_messages). Turns are flushed (not
    committed) to `db` as they're created - the caller controls the
    transaction boundary and can db.rollback() to discard this call's
    inserts atomically if anything downstream fails, rather than this
    function needing to track and manually undo exactly what it added."""
    scenario_id = scenario["scenario_id"]
    history = get_history(db, learner_id, scenario_id)
    appended: list[dict] = []

    if turn_number == 1 and not history:
        # Seed with the scenario's opening line as the assistant's first turn
        opening = {"role": "assistant", "content": scenario["opening_line"]}
        _append_turn(db, learner_id, scenario_id, "assistant", scenario["opening_line"])
        history.append(opening)
        appended.append(opening)

    user_msg = {"role": "user", "content": message}
    _append_turn(db, learner_id, scenario_id, "user", message)
    history.append(user_msg)
    appended.append(user_msg)

    system_prompt = build_system_prompt(scenario, pace_preference=pace_preference, vocabulary=vocabulary)
    reply = call_llm(system_prompt, history, client=client)

    assistant_msg = {"role": "assistant", "content": reply}
    _append_turn(db, learner_id, scenario_id, "assistant", reply)
    appended.append(assistant_msg)

    return reply, appended


def reset_history(db: Session, learner_id: str, scenario_id: str) -> None:
    db.query(ConversationTurnModel).filter_by(learner_id=learner_id, scenario_id=scenario_id).delete()
    db.commit()


def reset_all_history_for_learner(db: Session, learner_id: str) -> None:
    """Called when /profile re-creates a learner (fresh purpose/level/etc.) —
    without this, old conversation turns from a previous profile would
    still be sent to the LLM as context on the learner's next turn, even
    though every other piece of their state was reset."""
    db.query(ConversationTurnModel).filter_by(learner_id=learner_id).delete()
    db.commit()
