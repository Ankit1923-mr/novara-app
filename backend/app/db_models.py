"""
SQLAlchemy ORM models — the persistent shape of what used to live in
main.py's in-memory LEARNERS/SCENARIOS dicts.
"""

from sqlalchemy import Column, String, Float, Integer, JSON, DateTime, Boolean, Index
from app.db import Base


class LearnerModel(Base):
    __tablename__ = "learners"

    learner_id = Column(String, primary_key=True)
    # Optimistic concurrency control: every UPDATE includes a
    # "WHERE version_id = <value the row had when we read it>" clause,
    # and SQLAlchemy raises StaleDataError if another transaction already
    # moved the version forward. This is what actually protects
    # total_turns/repair_counts from a lost update when two /conversation
    # calls for the same learner run concurrently - a plain read-modify-
    # write in Python has no such protection on its own. See the retry
    # loop in main.py's /conversation handler.
    version_id = Column(Integer, nullable=False, default=0)
    # Bumped on every /profile call, regardless of whether purpose/level
    # actually changed value. Comparing purpose alone to detect "did a
    # reset happen while my /conversation request was in flight" fails
    # for a same-purpose reset (trip -> trip) or an A->B->A sequence that
    # lands back on the original purpose - profile_generation always
    # changes, so it's what /conversation's stale-write guard checks.
    profile_generation = Column(Integer, nullable=False, default=0)
    purpose = Column(String, nullable=False)
    # level and region were accepted by ProfileRequest from Day 0 but never
    # actually persisted anywhere - a real gap an external test report
    # flagged. Fixed here rather than left silently dropped.
    level = Column(String, nullable=True)
    region = Column(String, nullable=True)
    interests = Column(JSON, default=list)
    weak_areas = Column(JSON, default=list)
    pace_score = Column(Float, default=0.5)
    confidence_score = Column(Float, default=0.5)
    recently_seen = Column(JSON, default=list)
    total_turns = Column(Integer, default=0)
    repair_counts = Column(JSON, default=dict)

    # --- Web app additions: streak/pace/curriculum tracking ---
    # User-adjustable speed multiplier (learner can raise/lower it directly);
    # distinct from pace_score, which the Personalization Engine computes
    # from observed response times. This is what the "faster / slower"
    # control in the UI writes to.
    pace_preference = Column(Float, default=1.0)
    streak_days = Column(Integer, default=0)
    last_active_date = Column(String, nullable=True)  # ISO date "YYYY-MM-DD"
    topics_completed = Column(JSON, default=list)      # list of topic_id
    mistake_words = Column(JSON, default=dict)          # {"vocab phrase": wrong_count}
    cefr_level = Column(String, nullable=True)          # e.g. "A1" - set once thresholds are met

    # --- Automatic pace adjustment ---
    # Counters the Personalization Engine uses to move pace_preference on
    # its own (2 repairs in a row -> slower, 10 correct in a row -> faster),
    # independent of pace_score's response-time signal above. Both counters
    # reset to 0 whenever the learner manually overrides pace_preference via
    # /profile/pace, so auto-adjustment resumes counting from wherever the
    # learner explicitly chose, rather than fighting their override.
    consecutive_correct = Column(Integer, default=0)
    consecutive_repairs = Column(Integer, default=0)

    __mapper_args__ = {"version_id_col": version_id}


class UserModel(Base):
    """A web-app account. Deliberately separate from LearnerModel: signup
    creates a UserModel row only. The matching LearnerModel row (purpose,
    interests, etc.) is created afterward by the onboarding questionnaire
    calling the existing, contract-frozen POST /profile with
    learner_id = the user's email - no change needed to that contract or
    to Sakshi's Android integration."""
    __tablename__ = "users"

    email = Column(String, primary_key=True)
    password_hash = Column(String, nullable=False)
    # Structurally present, always False for now - real email delivery
    # (SMTP/transactional email service) is not wired up yet. Documented
    # honestly as a gap, not silently pretended to work.
    email_verified = Column(Boolean, default=False)
    created_at = Column(DateTime, nullable=True)


class ScenarioModel(Base):
    __tablename__ = "scenarios"

    scenario_id = Column(String, primary_key=True)
    purpose = Column(String, nullable=False)
    title = Column(String, nullable=False)
    setting = Column(String, nullable=False)
    situation_tags = Column(JSON, default=list)
    opening_line = Column(String, nullable=False)
    # The single situation tag this scenario was actually built around
    # (build_scenario's "chosen_situation") - distinct from situation_tags
    # above, which is the union of every tag across all matching nodes.
    # Needed so /conversation can look up the SAME node set /scenario
    # used, instead of re-querying get_subgraph per tag in situation_tags
    # (which can pull in unrelated nodes from other situations that
    # happen to share one tag, e.g. "ordering"). Nullable for scenario
    # rows created before this column existed.
    chosen_situation = Column(String, nullable=True)


class ConversationTurnModel(Base):
    """A single message in a learner's conversation for one scenario.
    Replaces conversation_engine.py's old module-level in-memory HISTORY
    dict, which lost every conversation on process restart/redeploy and
    exposed no history to any client. Ordered by `id` (autoincrement
    insertion order) - not by created_at, which can collide at
    sub-millisecond resolution between two turns committed in the same
    transaction, making it an unreliable sort key on its own."""
    __tablename__ = "conversation_turns"

    id = Column(Integer, primary_key=True, autoincrement=True)
    learner_id = Column(String, nullable=False)
    scenario_id = Column(String, nullable=False)
    role = Column(String, nullable=False)  # "user" | "assistant"
    content = Column(String, nullable=False)
    created_at = Column(DateTime, nullable=False)

    __table_args__ = (
        Index("ix_conversation_turns_learner_scenario", "learner_id", "scenario_id"),
    )
