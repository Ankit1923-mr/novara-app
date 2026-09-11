"""
NOVARA backend.

/profile, /scenario and /conversation are wired to real logic
(Knowledge Graph + Adaptive Learning Engine + AI Conversation Partner).
/repair and /readiness are still mocked — Repair Engine and Readiness
Scoring Engine land in later tasks. See docs/api-contract.md for the
frozen shapes.
"""

from fastapi import FastAPI, HTTPException

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

app = FastAPI(title="NOVARA API")

# In-memory learner store for MVP. learner_id -> {purpose, interests,
# weak_areas, pace_score, confidence_score, recently_seen: list[str]}.
# Swap for a real DB once persistence matters beyond a demo session.
LEARNERS: dict[str, dict] = {}

# scenario_id -> scenario dict, so /conversation can look up the scenario
# a learner is currently in without the client re-sending the full object.
SCENARIOS: dict[str, dict] = {}


@app.post("/profile", response_model=ProfileResponse)
def create_profile(req: ProfileRequest):
    LEARNERS[req.learner_id] = {
        "purpose": req.purpose,
        "interests": req.interests,
        "weak_areas": req.weak_areas,
        "pace_score": 0.5,
        "confidence_score": 0.5,
        "recently_seen": [],
    }
    return ProfileResponse(
        learner_id=req.learner_id,
        profile_created=True,
        weakness_vector={area: 1.0 for area in req.weak_areas} or {"listening": 0.0},
        pace_score=0.5,
        confidence_score=0.5,
    )


@app.get("/scenario", response_model=ScenarioResponse)
def get_scenario(learner_id: str):
    learner = LEARNERS.get(learner_id)
    if learner is None:
        raise HTTPException(status_code=404, detail="learner_id not found — call /profile first")

    scenario = build_scenario(
        learner_id=learner_id,
        purpose=learner["purpose"],
        interests=learner["interests"],
        weak_areas=learner["weak_areas"],
        recently_seen=learner["recently_seen"],
    )

    for tag in scenario["situation_tags"]:
        if tag not in learner["recently_seen"]:
            learner["recently_seen"].append(tag)

    SCENARIOS[scenario["scenario_id"]] = scenario
    return ScenarioResponse(**scenario)


@app.post("/conversation", response_model=ConversationResponse)
def post_conversation(req: ConversationRequest):
    scenario = SCENARIOS.get(req.scenario_id)
    if scenario is None:
        raise HTTPException(status_code=404, detail="scenario_id not found — call /scenario first")

    reply = handle_turn(
        learner_id=req.learner_id,
        scenario=scenario,
        message=req.message,
        turn_number=req.turn_number,
    )

    # Repair detection/selection lands in task 4 (Repair Engine) — the
    # conversation reply is real from here on, repair stays a stub.
    return ConversationResponse(
        reply=reply,
        repair_triggered=False,
        repair=None,
    )


@app.post("/repair", response_model=RepairDetail)
def post_repair(req: RepairRequest):
    return RepairDetail(
        error_type="grammar",
        strategy="rephrase",
        repair_text="Try: 'Quiero un café, por favor.'",
    )


@app.get("/readiness", response_model=ReadinessResponse)
def get_readiness(learner_id: str):
    if not learner_id:
        raise HTTPException(status_code=400, detail="learner_id required")
    return ReadinessResponse(
        learner_id=learner_id,
        aggregate_score=0.72,
        breakdown={
            "language_accuracy": 0.8,
            "repair_success_rate": 0.65,
            "register_appropriateness": 0.7,
            "transfer_success": 0.7,
        },
        purpose="trip",
        weights_used={
            "language_accuracy": 0.3,
            "repair_success_rate": 0.3,
            "register_appropriateness": 0.2,
            "transfer_success": 0.2,
        },
    )
