package com.example.novara.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProfileRequest(
    @SerialName("learner_id") val learnerId: String,
    val language: String = "spanish",
    val level: String, // "A1" | "A2" | "B1" | "B2"
    val region: String,
    val purpose: String, // "trip" | "casual"
    val interests: List<String>,
    @SerialName("weak_areas") val weakAreas: List<String>
)

@Serializable
data class ProfileResponse(
    @SerialName("learner_id") val learnerId: String,
    @SerialName("profile_created") val profileCreated: Boolean,
    @SerialName("weakness_vector") val weaknessVector: Map<String, Float> = emptyMap(),
    @SerialName("pace_score") val paceScore: Float = 0.5f,
    @SerialName("confidence_score") val confidenceScore: Float = 0.5f
)

@Serializable
data class LearnerSession(
    val learnerId: String,
    val level: String,
    val purpose: String,
    val region: String,
    val createdAt: Long = System.currentTimeMillis()
)
