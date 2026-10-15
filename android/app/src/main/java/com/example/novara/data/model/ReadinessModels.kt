package com.example.novara.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ReadinessResponse(
    @SerialName("learner_id") val learnerId: String,
    @SerialName("aggregate_score") val aggregateScore: Float = 0f,
    val breakdown: ScoreBreakdown = ScoreBreakdown(),
    val purpose: String = "trip",
    @SerialName("weights_used") val weightsUsed: ScoreBreakdown = ScoreBreakdown()
)

@Serializable
data class ScoreBreakdown(
    @SerialName("language_accuracy") val languageAccuracy: Float = 0f,
    @SerialName("repair_success_rate") val repairSuccessRate: Float = 0f,
    @SerialName("register_appropriateness") val registerAppropriateness: Float = 0f,
    @SerialName("transfer_success") val transferSuccess: Float = 0f
)
