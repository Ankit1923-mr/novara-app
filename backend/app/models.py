from pydantic import BaseModel, Field
from typing import Optional, Literal

Purpose = Literal["trip", "casual"]
Level = Literal["A1", "A2", "B1", "B2"]
ErrorType = Literal["lexical", "grammar", "register", "comprehension"]
Strategy = Literal["clarify", "rephrase", "hint"]

# Shared bounds — kept as named constants so the reasoning behind each
# limit lives in one place instead of being a bare number wherever it's used.
MAX_ID_LENGTH = 100          # learner_id / scenario_id
MAX_SHORT_TEXT_LENGTH = 200  # region, single utterances used for matching
MAX_MESSAGE_LENGTH = 1000    # a conversation turn or repair sample — generous for a sentence, not a paste
MAX_LIST_ITEMS = 20          # interests / weak_areas — no real learner needs more than this


class ProfileRequest(BaseModel):
    learner_id: str = Field(min_length=1, max_length=MAX_ID_LENGTH)
    language: str = "spanish"
    level: Level
    region: str = Field(min_length=1, max_length=MAX_SHORT_TEXT_LENGTH)
    purpose: Purpose
    interests: list[str] = Field(default=[], max_length=MAX_LIST_ITEMS)
    weak_areas: list[str] = Field(default=[], max_length=MAX_LIST_ITEMS)


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
    learner_id: str = Field(min_length=1, max_length=MAX_ID_LENGTH)
    scenario_id: str = Field(min_length=1, max_length=MAX_ID_LENGTH)
    message: str = Field(min_length=1, max_length=MAX_MESSAGE_LENGTH)
    turn_number: int = Field(gt=0)
    response_time_ms: Optional[int] = Field(default=None, ge=0)  # optional: how long the learner took to answer, feeds Personalization Engine's pace score


class RepairDetail(BaseModel):
    error_type: ErrorType
    strategy: Strategy
    repair_text: str


class ConversationResponse(BaseModel):
    reply: str
    repair_triggered: bool
    repair: Optional[RepairDetail] = None


class RepairRequest(BaseModel):
    learner_utterance: str = Field(min_length=1, max_length=MAX_MESSAGE_LENGTH)
    expected_pattern: str = Field(min_length=1, max_length=MAX_MESSAGE_LENGTH)


class ReadinessResponse(BaseModel):
    learner_id: str
    aggregate_score: float
    breakdown: dict[str, float]
    purpose: Purpose
    weights_used: dict[str, float]
