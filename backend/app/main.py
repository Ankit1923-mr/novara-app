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
from fastapi import FastAPI, HTTPException, Depends, Request, Query
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
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
)
from app.adaptive_engine import build_scenario
from app.conversation_engine import handle_turn
from app.knowledge_graph import get_subgraph, list_situations
from app.repair_engine import repair as run_repair, detect_repair
from app.personalization_engine import update_scores
from app.readiness_engine import compute_readiness
from app.db import get_db, init_db
from app.db_models import LearnerModel, ScenarioModel
from app.auth import verify_api_key

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
    db.commit()

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

    reply = handle_turn(
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

    learner.total_turns += 1
    if repair_result is not None:
        error_type = repair_result["error_type"]
        repair_counts = dict(learner.repair_counts)
        repair_counts[error_type] = repair_counts.get(error_type, 0) + 1
        learner.repair_counts = repair_counts

    updated_scores = update_scores(
        pace_score=learner.pace_score,
        confidence_score=learner.confidence_score,
        repair_triggered=repair_result is not None,
        response_time_ms=req.response_time_ms,
    )
    learner.pace_score = updated_scores["pace_score"]
    learner.confidence_score = updated_scores["confidence_score"]

    db.commit()

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
