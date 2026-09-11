"""
NOVARA backend.

/profile, /scenario, /conversation and /repair are wired to real logic
(Knowledge Graph + Adaptive Learning Engine + AI Conversation Partner +
Repair Engine + Personalization Engine). /readiness is still mocked —
Readiness Scoring Engine lands in a later task. See docs/api-contract.md
for the frozen shapes.
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
from app.knowledge_graph import get_subgraph
from app.repair_engine import repair as run_repair, detect_repair
from app.personalization_engine import update_scores

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
    learner = LEARNERS.get(req.learner_id)
    if learner is None:
        raise HTTPException(status_code=404, detail="learner_id not found — call /profile first")

    scenario = SCENARIOS.get(req.scenario_id)
    if scenario is None:
        raise HTTPException(status_code=404, detail="scenario_id not found — call /scenario first")

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

    updated_scores = update_scores(
        pace_score=learner["pace_score"],
        confidence_score=learner["confidence_score"],
        repair_triggered=repair_result is not None,
        response_time_ms=req.response_time_ms,
    )
    learner["pace_score"] = updated_scores["pace_score"]
    learner["confidence_score"] = updated_scores["confidence_score"]

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
