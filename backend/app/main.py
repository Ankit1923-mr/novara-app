"""
NOVARA backend.

All six modules are wired to real logic: Knowledge Graph, Adaptive
Learning Engine, AI Conversation Partner, Repair Engine, Personalization
Engine, and Readiness Scoring Engine. Learner and scenario state persists
in Postgres (Supabase) via SQLAlchemy — see db.py and db_models.py.
See docs/api-contract.md for the frozen request/response shapes.
"""

from contextlib import asynccontextmanager
from fastapi import FastAPI, HTTPException, Depends
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy.orm import Session

from app.models import (
    ProfileRequest,
    ProfileResponse,
    ScenarioResponse,
    ConversationRequest,
    ConversationResponse,
    RepairDetail,
    RepairRequest,
    ReadinessResponse,
)
from app.adaptive_engine import build_scenario
from app.conversation_engine import handle_turn
from app.knowledge_graph import get_subgraph, list_situations
from app.repair_engine import repair as run_repair, detect_repair
from app.personalization_engine import update_scores
from app.readiness_engine import compute_readiness
from app.db import get_db, init_db
from app.db_models import LearnerModel, ScenarioModel

@asynccontextmanager
async def lifespan(app: FastAPI):
    init_db()
    yield


app = FastAPI(title="NOVARA API", lifespan=lifespan)

# Wide open for MVP demo purposes — the Android app has no fixed origin
# during development (emulator, physical device, different networks).
# Tighten this to specific origins before any real deployment beyond
# the class demo.
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
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


@app.post("/profile", response_model=ProfileResponse)
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


@app.get("/scenario", response_model=ScenarioResponse)
def get_scenario(learner_id: str, db: Session = Depends(get_db)):
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


@app.post("/conversation", response_model=ConversationResponse)
def post_conversation(req: ConversationRequest, db: Session = Depends(get_db)):
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


@app.post("/repair", response_model=RepairDetail)
def post_repair(req: RepairRequest):
    result = run_repair(req.learner_utterance, req.expected_pattern)
    return RepairDetail(**result)


@app.get("/readiness", response_model=ReadinessResponse)
def get_readiness(learner_id: str, db: Session = Depends(get_db)):
    if not learner_id:
        raise HTTPException(status_code=400, detail="learner_id required")

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
