"""
SQLAlchemy ORM models — the persistent shape of what used to live in
main.py's in-memory LEARNERS/SCENARIOS dicts.
"""

from sqlalchemy import Column, String, Float, Integer, JSON
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
    purpose = Column(String, nullable=False)
    interests = Column(JSON, default=list)
    weak_areas = Column(JSON, default=list)
    pace_score = Column(Float, default=0.5)
    confidence_score = Column(Float, default=0.5)
    recently_seen = Column(JSON, default=list)
    total_turns = Column(Integer, default=0)
    repair_counts = Column(JSON, default=dict)

    __mapper_args__ = {"version_id_col": version_id}


class ScenarioModel(Base):
    __tablename__ = "scenarios"

    scenario_id = Column(String, primary_key=True)
    purpose = Column(String, nullable=False)
    title = Column(String, nullable=False)
    setting = Column(String, nullable=False)
    situation_tags = Column(JSON, default=list)
    opening_line = Column(String, nullable=False)
