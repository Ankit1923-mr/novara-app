from pydantic import BaseModel
from typing import Optional, Literal

Purpose = Literal["trip", "casual"]
Level = Literal["A1", "A2", "B1", "B2"]
ErrorType = Literal["lexical", "grammar", "register", "comprehension"]
Strategy = Literal["clarify", "rephrase", "hint"]


class ProfileRequest(BaseModel):
    learner_id: str
    language: str = "spanish"
    level: Level
    region: str
    purpose: Purpose
    interests: list[str] = []
    weak_areas: list[str] = []


class ProfileResponse(BaseModel):
    learner_id: str
    profile_created: bool
    weakness_vector: dict[str, float]
    pace_score: float
    confidence_score: float


class ScenarioResponse(BaseModel):
    scenario_id: str
    purpose: Purpose
    title: str
    setting: str
    situation_tags: list[str]
    opening_line: str


class ConversationRequest(BaseModel):
    learner_id: str
    scenario_id: str
    message: str
    turn_number: int
    response_time_ms: Optional[int] = None  # optional: how long the learner took to answer, feeds Personalization Engine's pace score


class RepairDetail(BaseModel):
    error_type: ErrorType
    strategy: Strategy
    repair_text: str


class ConversationResponse(BaseModel):
    reply: str
    repair_triggered: bool
    repair: Optional[RepairDetail] = None


class RepairRequest(BaseModel):
    learner_utterance: str
    expected_pattern: str


class ReadinessResponse(BaseModel):
    learner_id: str
    aggregate_score: float
    breakdown: dict[str, float]
    purpose: Purpose
    weights_used: dict[str, float]
