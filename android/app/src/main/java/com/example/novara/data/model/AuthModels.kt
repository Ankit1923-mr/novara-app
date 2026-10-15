package com.example.novara.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SignupRequest(
    val email: String,
    val password: String
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class AuthResponse(
    val token: String,
    val email: String,
    @SerialName("learner_id") val learnerId: String,
    @SerialName("has_profile") val hasProfile: Boolean
)

@Serializable
data class LearnerState(
    @SerialName("learner_id") val learnerId: String,
    val purpose: String,
    val region: String,
    val interests: List<String> = emptyList(),
    @SerialName("weak_areas") val weakAreas: List<String> = emptyList(),
    @SerialName("pace_score") val paceScore: Float = 0.5f,
    @SerialName("pace_preference") val pacePreference: Float = 1.0f,
    @SerialName("confidence_score") val confidenceScore: Float = 0.5f,
    @SerialName("streak_days") val streakDays: Int = 0,
    @SerialName("topics_completed") val topicsCompleted: List<String> = emptyList(),
    @SerialName("mistake_words") val mistakeWords: Map<String, Int> = emptyMap(),
    @SerialName("total_turns") val totalTurns: Int = 0
)
