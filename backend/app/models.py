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


class LearnerState(BaseModel):
    """Full learner snapshot for the web app's dashboard — fields not
    covered by /profile's creation-time response or /readiness's
    scoring-only response (streak, pace preference, topics done,
    tracked mistakes)."""
    learner_id: str
    purpose: Purpose
    region: str
    interests: list[str]
    weak_areas: list[str]
    pace_score: float
    pace_preference: float
    confidence_score: float
    streak_days: int
    topics_completed: list[str]
    mistake_words: dict[str, int]
    total_turns: int


# --- Web-app accounts ---
MIN_PASSWORD_LENGTH = 8
MAX_EMAIL_LENGTH = 254  # RFC 5321 limit


class SignupRequest(BaseModel):
    email: str = Field(min_length=3, max_length=MAX_EMAIL_LENGTH)
    password: str = Field(min_length=MIN_PASSWORD_LENGTH, max_length=200)


class LoginRequest(BaseModel):
    email: str = Field(min_length=3, max_length=MAX_EMAIL_LENGTH)
    password: str = Field(min_length=1, max_length=200)


class AuthResponse(BaseModel):
    token: str
    email: str
    learner_id: str  # equals email - the web app uses this to call /profile, /scenario, etc.
    has_profile: bool  # false until the onboarding questionnaire has called POST /profile once


# --- Lessons and quizzes ---
class VocabItem(BaseModel):
    phrase: str
    meaning: str
    cultural_note: str


class QuizQuestion(BaseModel):
    question: str
    options: list[str]
    correct_index: int


class TopicSummary(BaseModel):
    topic_id: str
    title: str
    order_index: int
    completed: bool


class TopicDetail(BaseModel):
    topic_id: str
    title: str
    vocabulary: list[VocabItem]
    quiz: list[QuizQuestion]  # correct_index included - fine for an MVP with no anti-cheat requirement


class QuizSubmission(BaseModel):
    learner_id: str = Field(min_length=1, max_length=MAX_ID_LENGTH)
    answers: list[int] = Field(min_length=1, max_length=50)


class QuizResult(BaseModel):
    topic_id: str
    score: float  # 0-1
    correct_count: int
    total: int
    missed_phrases: list[str]  # vocabulary tied to questions the learner got wrong, feeds mistake_words
