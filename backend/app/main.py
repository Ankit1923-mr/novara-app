"""
NOVARA backend — Day 0 scaffold.

Every endpoint here returns hardcoded, schema-valid mock data so Android
can build against a live contract immediately. Replace each mock body
with real logic (Knowledge Graph, Adaptive Engine, Repair Engine,
Personalization Engine, Readiness Scoring, LLM call) task by task —
see docs/api-contract.md for the frozen shapes.
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

app = FastAPI(title="NOVARA API")


@app.post("/profile", response_model=ProfileResponse)
def create_profile(req: ProfileRequest):
    return ProfileResponse(
        learner_id=req.learner_id,
        profile_created=True,
        weakness_vector={area: 1.0 for area in req.weak_areas} or {"listening": 0.0},
        pace_score=0.5,
        confidence_score=0.5,
    )


@app.get("/scenario", response_model=ScenarioResponse)
def get_scenario(learner_id: str):
    return ScenarioResponse(
        scenario_id="mock-scenario-cafe",
        purpose="trip",
        title="Ordering coffee",
        setting="A café in Madrid",
        situation_tags=["food", "ordering"],
        opening_line="Hola, ¿qué le pongo?",
    )


@app.post("/conversation", response_model=ConversationResponse)
def post_conversation(req: ConversationRequest):
    if req.turn_number == 3:
        return ConversationResponse(
            reply="¿Puede repetir, por favor?",
            repair_triggered=True,
            repair=RepairDetail(
                error_type="lexical",
                strategy="clarify",
                repair_text="Se dice 'para llevar', no 'para llevo'.",
            ),
        )
    return ConversationResponse(
        reply="¿Para aquí o para llevar?",
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
