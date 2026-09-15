"""
NOVARA backend.

All six modules are wired to real logic: Knowledge Graph, Adaptive
Learning Engine, AI Conversation Partner, Repair Engine, Personalization
Engine, and Readiness Scoring Engine. Learner and scenario state persists
in Postgres (Supabase) via SQLAlchemy — see db.py and db_models.py.

Every endpoint except the health check requires an X-API-Key header
matching NOVARA_API_KEY (see auth.py) — the live deployment is public,
and this is what stops a stranger from burning the free-tier LLM quota
or filling the database with junk learners.

See docs/api-contract.md for the frozen request/response shapes.
"""

from contextlib import asynccontextmanager
from datetime import datetime, timezone, date, timedelta
from fastapi import FastAPI, HTTPException, Depends, Request, Query
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from sqlalchemy import update as sa_update, func, cast, Numeric
from sqlalchemy.orm import Session
from slowapi import Limiter, _rate_limit_exceeded_handler
from slowapi.util import get_remote_address
from slowapi.errors import RateLimitExceeded
from slowapi.middleware import SlowAPIMiddleware

from app.models import (
    ProfileRequest,
    ProfileResponse,
    ScenarioResponse,
    ConversationRequest,
    ConversationResponse,
    RepairDetail,
    RepairRequest,
    ReadinessResponse,
    MAX_ID_LENGTH,
    SignupRequest,
    LoginRequest,
    AuthResponse,
    TopicSummary,
    TopicDetail,
    QuizSubmission,
    QuizResult,
)
from app.adaptive_engine import build_scenario
from app.conversation_engine import handle_turn, reset_all_history_for_learner, remove_turn_from_history
from app.knowledge_graph import get_subgraph, list_situations
from app.repair_engine import repair as run_repair, detect_repair
from app.personalization_engine import (
    update_scores, EMA_ALPHA, CONFIDENT_SIGNAL, UNCONFIDENT_SIGNAL, pace_signal_from_response_time,
)
from app.readiness_engine import compute_readiness
from app.db import get_db, init_db
from app.db_models import LearnerModel, ScenarioModel, UserModel
from app.auth import verify_api_key
from app.auth_service import hash_password, verify_password, create_session_token, decode_session_token
from app import lesson_content

@asynccontextmanager
async def lifespan(app: FastAPI):
    init_db()
    yield


app = FastAPI(title="NOVARA API", lifespan=lifespan)

# Rate limiting — per-IP, since the API key is shared by the whole
# Android app rather than per-user. Protects the free-tier LLM quota
# and the database from a runaway client loop or abuse of the public
# URL (auth stops strangers from writing data, but a legitimate-looking
# request storm from one source could still exhaust the LLM quota).
limiter = Limiter(key_func=get_remote_address, default_limits=["100/minute"])
app.state.limiter = limiter
app.add_exception_handler(RateLimitExceeded, _rate_limit_exceeded_handler)
app.add_middleware(SlowAPIMiddleware)

# CORS origin restriction protects browser-based clients (JS enforces
# same-origin); it does nothing for a native Android app, which sends
# no Origin header and isn't subject to same-origin policy. Left wide
# open here deliberately — the real access control is the API key
# below (verify_api_key), not CORS, since our only client is native.
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


CONVERSATION_UPDATE_MAX_RETRIES = 5


@app.exception_handler(Exception)
async def unhandled_exception_handler(request: Request, exc: Exception):
    """Catches anything not already handled as an HTTPException, so a
    bug never leaks a raw Python traceback (with file paths, internals)
    to a client — returns the same {error, code} shape documented for
    every other error case in docs/api-contract.md."""
    return JSONResponse(
        status_code=500,
        content={"error": "An unexpected error occurred.", "code": "INTERNAL_ERROR"},
    )


def _bump_streak(learner: LearnerModel) -> None:
    """Called on any real learning activity (quiz submit, conversation
    turn). Simple day-based streak: consecutive calendar days (UTC)
    extend it, a gap resets it, same day is a no-op."""
    today = datetime.now(timezone.utc).date()
    if learner.last_active_date is None:
        learner.streak_days = 1
    else:
        last = date.fromisoformat(learner.last_active_date)
        if last == today:
            return
        elif last == today - timedelta(days=1):
            learner.streak_days = (learner.streak_days or 0) + 1
        else:
            learner.streak_days = 1
    learner.last_active_date = today.isoformat()


def _get_current_user_email(request: Request) -> str:
    """Reads and validates the Authorization: Bearer <jwt> header for the
    web-app's per-user session. Separate from verify_api_key (the
    app-level gate) - a web-app request needs both a valid API key AND
    a valid session token."""
    auth_header = request.headers.get("Authorization", "")
    if not auth_header.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="Missing bearer token")
    token = auth_header[len("Bearer "):]
    email = decode_session_token(token)
    if email is None:
        raise HTTPException(status_code=401, detail="Invalid or expired session token")
    return email


def _scenario_model_to_dict(s: ScenarioModel) -> dict:
    return {
        "scenario_id": s.scenario_id,
        "purpose": s.purpose,
        "title": s.title,
        "setting": s.setting,
        "situation_tags": s.situation_tags,
        "opening_line": s.opening_line,
    }


@app.get("/")
def health_check():
    return {"status": "ok", "service": "novara-api"}


@app.post("/auth/signup", response_model=AuthResponse, dependencies=[Depends(verify_api_key)])
def signup(req: SignupRequest, db: Session = Depends(get_db)):
    email = req.email.strip().lower()
    existing = db.get(UserModel, email)
    if existing is not None:
        raise HTTPException(status_code=409, detail="An account with this email already exists")

    user = UserModel(
        email=email,
        password_hash=hash_password(req.password),
        email_verified=False,
        created_at=datetime.now(timezone.utc),
    )
    db.add(user)
    db.commit()

    token = create_session_token(email)
    has_profile = db.get(LearnerModel, email) is not None
    return AuthResponse(token=token, email=email, learner_id=email, has_profile=has_profile)


@app.post("/auth/login", response_model=AuthResponse, dependencies=[Depends(verify_api_key)])
def login(req: LoginRequest, db: Session = Depends(get_db)):
    email = req.email.strip().lower()
    user = db.get(UserModel, email)
    if user is None or not verify_password(req.password, user.password_hash):
        # deliberately the same error for "no such user" and "wrong password" -
        # distinguishing them lets an attacker enumerate registered emails
        raise HTTPException(status_code=401, detail="Incorrect email or password")

    token = create_session_token(email)
    has_profile = db.get(LearnerModel, email) is not None
    return AuthResponse(token=token, email=email, learner_id=email, has_profile=has_profile)


@app.post("/profile", response_model=ProfileResponse, dependencies=[Depends(verify_api_key)])
def create_profile(req: ProfileRequest, db: Session = Depends(get_db)):
    learner = db.get(LearnerModel, req.learner_id)
    if learner is None:
        learner = LearnerModel(learner_id=req.learner_id)
        db.add(learner)

    learner.purpose = req.purpose
    learner.interests = req.interests
    learner.weak_areas = req.weak_areas
    learner.pace_score = 0.5
    learner.confidence_score = 0.5
    learner.recently_seen = []
    learner.total_turns = 0
    learner.repair_counts = {}
    # Bumped unconditionally, even if purpose/level end up unchanged from
    # before - this is what /conversation's stale-write guard compares
    # against, and it must change on every reset regardless of the new
    # values, not just when they differ from the old ones.
    learner.profile_generation = (learner.profile_generation or 0) + 1
    db.commit()

    # A fresh profile means a fresh conversation, too — otherwise the LLM
    # would see turns from a previous purpose/level as if they still count.
    reset_all_history_for_learner(req.learner_id)

    return ProfileResponse(
        learner_id=req.learner_id,
        profile_created=True,
        weakness_vector={area: 1.0 for area in req.weak_areas} or {"listening": 0.0},
        pace_score=0.5,
        confidence_score=0.5,
    )


@app.get("/scenario", response_model=ScenarioResponse, dependencies=[Depends(verify_api_key)])
def get_scenario(learner_id: str = Query(min_length=1, max_length=MAX_ID_LENGTH), db: Session = Depends(get_db)):
    learner = db.get(LearnerModel, learner_id)
    if learner is None:
        raise HTTPException(status_code=404, detail="learner_id not found — call /profile first")

    scenario = build_scenario(
        learner_id=learner_id,
        purpose=learner.purpose,
        interests=learner.interests,
        weak_areas=learner.weak_areas,
        recently_seen=learner.recently_seen,
    )

    recently_seen = list(learner.recently_seen)
    for tag in scenario["situation_tags"]:
        if tag not in recently_seen:
            recently_seen.append(tag)
    learner.recently_seen = recently_seen

    scenario_row = db.get(ScenarioModel, scenario["scenario_id"])
    if scenario_row is None:
        scenario_row = ScenarioModel(scenario_id=scenario["scenario_id"])
        db.add(scenario_row)
    scenario_row.purpose = scenario["purpose"]
    scenario_row.title = scenario["title"]
    scenario_row.setting = scenario["setting"]
    scenario_row.situation_tags = scenario["situation_tags"]
    scenario_row.opening_line = scenario["opening_line"]

    db.commit()
    return ScenarioResponse(**scenario)


@app.post("/conversation", response_model=ConversationResponse, dependencies=[Depends(verify_api_key)])
@limiter.limit("20/minute")
def post_conversation(request: Request, req: ConversationRequest, db: Session = Depends(get_db)):
    learner = db.get(LearnerModel, req.learner_id)
    if learner is None:
        raise HTTPException(status_code=404, detail="learner_id not found — call /profile first")

    scenario_row = db.get(ScenarioModel, req.scenario_id)
    if scenario_row is None:
        raise HTTPException(status_code=404, detail="scenario_id not found — call /scenario first")
    scenario = _scenario_model_to_dict(scenario_row)

    # Captured so that if /profile resets this learner while this request
    # is still in flight (e.g. a slow LLM call), we can detect it below and
    # discard this turn's effect rather than applying a stale update to the
    # learner's brand-new profile. profile_generation, not purpose, is the
    # correct guard: purpose alone misses a same-purpose reset (trip -> trip)
    # or an A->B->A sequence that lands back on the original value, since
    # both leave purpose looking unchanged even though a reset happened.
    initial_generation = learner.profile_generation

    # The LLM call and repair detection don't depend on the learner's
    # current scores, so they run once - only the score/counter update
    # below needs to be retried under contention.
    appended_messages: list[dict] = []
    try:
        reply, appended_messages = handle_turn(
            learner_id=req.learner_id,
            scenario=scenario,
            message=req.message,
            turn_number=req.turn_number,
        )

        # Check the learner's message against the graph phrases relevant to
        # this scenario's situation tags — detect_repair() decides whether
        # it's close enough to a known phrase to be worth repairing, exactly
        # matches (no repair), or is free-form conversation outside any
        # known phrase (also no repair — the AI partner allows open dialogue).
        candidate_nodes = [
            n for tag in scenario["situation_tags"]
            for n in get_subgraph(scenario["purpose"], situation_tag=tag)
        ]
        candidate_patterns = [n["phrase"] for n in candidate_nodes] or [scenario["opening_line"]]
        repair_result = detect_repair(req.message, candidate_patterns)

        # Personalization Engine is still the source of truth for the EMA
        # formula/signals — called here so its documented role holds even
        # though the actual persisted write below re-derives the same
        # formula as a SQL expression rather than trusting this call's
        # possibly-stale-by-write-time return value (see the atomic update
        # note just below for why).
        update_scores(
            pace_score=learner.pace_score,
            confidence_score=learner.confidence_score,
            repair_triggered=repair_result is not None,
            response_time_ms=req.response_time_ms,
        )

        # Race-free update for total_turns/pace/confidence: expressed as a
        # single SQL "col = f(col)" statement, so the database itself reads
        # and writes the column atomically per row — there is no read-then-
        # write window in Python for a concurrent request to land in, unlike
        # loading the value, computing in Python, and writing it back.
        confidence_signal = UNCONFIDENT_SIGNAL if repair_result is not None else CONFIDENT_SIGNAL
        pace_signal = pace_signal_from_response_time(req.response_time_ms) if req.response_time_ms is not None else None

        # Everything (total_turns, scores, repair_counts, version bump) is
        # applied in ONE UPDATE per attempt, guarded by
        # "WHERE version_id = :expected AND profile_generation = :expected"
        # in the statement itself — not a preceding SELECT. That distinction
        # matters: a guard checked via a separate read-then-compare has a
        # race window between the check and the write where a reset could
        # still land; a guard baked into the UPDATE's own WHERE clause is
        # evaluated by the database atomically as part of executing that
        # one statement, so nothing can slip in between "check" and "write"
        # because there is no gap - they're the same operation.
        # rowcount == 0 after executing means one of two things: another
        # /conversation call for this learner committed first (version_id
        # moved - legitimate race, retry with fresh values) or /profile
        # reset this learner (profile_generation moved - not a race to
        # retry, the turn's effect must be discarded entirely).
        for attempt in range(CONVERSATION_UPDATE_MAX_RETRIES):
            current = db.get(LearnerModel, req.learner_id)
            if current is not None:
                db.refresh(current)
            if current is None or current.profile_generation != initial_generation:
                remove_turn_from_history(req.learner_id, req.scenario_id, appended_messages)
                return ConversationResponse(
                    reply=reply,
                    repair_triggered=repair_result is not None,
                    repair=RepairDetail(**repair_result) if repair_result else None,
                )

            repair_counts = dict(current.repair_counts)
            if repair_result is not None:
                error_type = repair_result["error_type"]
                repair_counts[error_type] = repair_counts.get(error_type, 0) + 1

            values = {
                "total_turns": current.total_turns + 1,
                "version_id": current.version_id + 1,
                "repair_counts": repair_counts,
                # rounded to 4dp to match update_scores()'s own rounding —
                # otherwise floating-point drift compounds differently
                # between the two code paths over repeated turns. Postgres's
                # round() only accepts numeric, not double precision/float,
                # hence the explicit cast (SQLite doesn't care either way).
                "confidence_score": func.round(
                    cast(EMA_ALPHA * confidence_signal + (1 - EMA_ALPHA) * current.confidence_score, Numeric), 4
                ),
            }
            if pace_signal is not None:
                values["pace_score"] = func.round(
                    cast(EMA_ALPHA * pace_signal + (1 - EMA_ALPHA) * current.pace_score, Numeric), 4
                )

            today_iso = datetime.now(timezone.utc).date().isoformat()
            if current.last_active_date == today_iso:
                pass  # already counted today, no change
            elif current.last_active_date == (datetime.now(timezone.utc).date() - timedelta(days=1)).isoformat():
                values["streak_days"] = (current.streak_days or 0) + 1
                values["last_active_date"] = today_iso
            else:
                values["streak_days"] = 1
                values["last_active_date"] = today_iso

            result = db.execute(
                sa_update(LearnerModel)
                .where(
                    LearnerModel.learner_id == req.learner_id,
                    LearnerModel.version_id == current.version_id,
                    LearnerModel.profile_generation == initial_generation,
                )
                .values(**values)
            )
            db.commit()
            if result.rowcount == 1:
                break
        else:
            raise HTTPException(
                status_code=409,
                detail="Too many concurrent updates for this learner — please retry.",
            )
    except Exception:
        # DB changes roll back on their own (session is discarded
        # without a commit), but the in-memory conversation history
        # mutated by handle_turn() would otherwise survive a failure
        # here and diverge from the persisted state - undo it too.
        # Removed by identity (not by truncating to a saved length): a
        # concurrent sibling call for the same learner+scenario may have
        # appended its own, already-successful turn in between, and
        # length-based truncation would silently delete that too.
        remove_turn_from_history(req.learner_id, req.scenario_id, appended_messages)
        raise

    return ConversationResponse(
        reply=reply,
        repair_triggered=repair_result is not None,
        repair=RepairDetail(**repair_result) if repair_result else None,
    )


@app.post("/repair", response_model=RepairDetail, dependencies=[Depends(verify_api_key)])
def post_repair(req: RepairRequest):
    result = run_repair(req.learner_utterance, req.expected_pattern)
    return RepairDetail(**result)


@app.get("/readiness", response_model=ReadinessResponse, dependencies=[Depends(verify_api_key)])
def get_readiness(learner_id: str = Query(min_length=1, max_length=MAX_ID_LENGTH), db: Session = Depends(get_db)):
    learner = db.get(LearnerModel, learner_id)
    if learner is None:
        raise HTTPException(status_code=404, detail="learner_id not found — call /profile first")

    total_known_situations = len(list_situations(learner.purpose))
    result = compute_readiness(
        purpose=learner.purpose,
        total_turns=learner.total_turns,
        repair_counts=learner.repair_counts,
        distinct_situations_visited=len(learner.recently_seen),
        total_known_situations=total_known_situations,
    )

    return ReadinessResponse(learner_id=learner_id, **result)


@app.get("/topics", response_model=list[TopicSummary], dependencies=[Depends(verify_api_key)])
def get_topics(learner_id: str = Query(min_length=1, max_length=MAX_ID_LENGTH), db: Session = Depends(get_db)):
    learner = db.get(LearnerModel, learner_id)
    completed = set(learner.topics_completed) if learner else set()
    return [
        TopicSummary(topic_id=t["topic_id"], title=t["title"], order_index=t["order_index"],
                      completed=t["topic_id"] in completed)
        for t in lesson_content.list_topics()
    ]


@app.get("/topics/{topic_id}", response_model=TopicDetail, dependencies=[Depends(verify_api_key)])
def get_topic_detail(topic_id: str):
    topic = lesson_content.get_topic(topic_id)
    if topic is None:
        raise HTTPException(status_code=404, detail="topic not found")
    return TopicDetail(topic_id=topic["topic_id"], title=topic["title"],
                        vocabulary=topic["vocabulary"],
                        quiz=[{"question": q["question"], "options": q["options"], "correct_index": q["correct_index"]}
                              for q in topic["quiz"]])


@app.post("/topics/{topic_id}/submit", response_model=QuizResult, dependencies=[Depends(verify_api_key)])
def submit_quiz(topic_id: str, req: QuizSubmission, db: Session = Depends(get_db)):
    if lesson_content.get_topic(topic_id) is None:
        raise HTTPException(status_code=404, detail="topic not found")

    learner = db.get(LearnerModel, req.learner_id)
    if learner is None:
        raise HTTPException(status_code=404, detail="learner_id not found — call /profile first")

    result = lesson_content.grade_quiz(topic_id, req.answers)

    # Track exactly which phrases this learner keeps missing, across every
    # quiz attempt - this is the "focus on where they're actually making
    # mistakes" signal the adaptive engine can build on, rather than a
    # generic difficulty knob.
    mistake_words = dict(learner.mistake_words)
    for phrase in result["missed_phrases"]:
        mistake_words[phrase] = mistake_words.get(phrase, 0) + 1
    learner.mistake_words = mistake_words

    if result["score"] >= 0.7 and topic_id not in learner.topics_completed:
        learner.topics_completed = learner.topics_completed + [topic_id]

    _bump_streak(learner)
    db.commit()

    return QuizResult(**result)


@app.post("/profile/pace", dependencies=[Depends(verify_api_key)])
def set_pace_preference(learner_id: str = Query(min_length=1, max_length=MAX_ID_LENGTH),
                         pace_preference: float = Query(ge=0.5, le=2.0),
                         db: Session = Depends(get_db)):
    """Learner-controlled speed multiplier - distinct from pace_score,
    which the Personalization Engine infers from response times. This is
    the direct 'faster / slower' control the UI exposes."""
    learner = db.get(LearnerModel, learner_id)
    if learner is None:
        raise HTTPException(status_code=404, detail="learner_id not found — call /profile first")
    learner.pace_preference = pace_preference
    db.commit()
    return {"learner_id": learner_id, "pace_preference": pace_preference}
