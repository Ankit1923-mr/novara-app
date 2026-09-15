package com.novara.app.model

import com.google.gson.annotations.SerializedName

/**
 * NOVARA Data Models strictly matching `docs/api-contract.md`.
 */

// ─── 1. POST /profile ───────────────────────────────────────────────
data class LearnerProfile(
    @SerializedName("learner_id") val learnerId: String,
    @SerializedName("language") val language: String = "spanish",
    @SerializedName("level") val level: String, // "A1" | "A2" | "B1" | "B2"
    @SerializedName("region") val region: String, // e.g. "Madrid"
    @SerializedName("purpose") val purpose: String, // "trip" | "casual"
    @SerializedName("interests") val interests: List<String>,
    @SerializedName("weak_areas") val weakAreas: List<String>,
    // Client-side local personalization attributes
    val nativeLanguage: String? = "English",
    val accentTarget: String? = "es-ES"
)

data class ProfileResponse(
    @SerializedName("learner_id") val learnerId: String,
    @SerializedName("profile_created") val profileCreated: Boolean,
    @SerializedName("weakness_vector") val weaknessVector: Map<String, Double>?,
    @SerializedName("pace_score") val paceScore: Double?,
    @SerializedName("confidence_score") val confidenceScore: Double?
)

// ─── 2. GET /scenario ───────────────────────────────────────────────
data class ScenarioResponse(
    @SerializedName("scenario_id") val scenarioId: String,
    @SerializedName("purpose") val purpose: String,
    @SerializedName("title") val title: String,
    @SerializedName("setting") val setting: String,
    @SerializedName("situation_tags") val situationTags: List<String>,
    @SerializedName("opening_line") val openingLine: String
)

// ─── 3. POST /conversation ──────────────────────────────────────────
data class ConversationRequest(
    @SerializedName("learner_id") val learnerId: String,
    @SerializedName("scenario_id") val scenarioId: String,
    @SerializedName("message") val message: String,
    @SerializedName("turn_number") val turnNumber: Int,
    @SerializedName("response_time_ms") val responseTimeMs: Long? = null
)

data class RepairDetail(
    @SerializedName("error_type") val errorType: String, // lexical | grammar | register | comprehension
    @SerializedName("strategy") val strategy: String,    // clarify | rephrase | hint
    @SerializedName("repair_text") val repairText: String
)

data class ConversationResponse(
    @SerializedName("reply") val reply: String,
    @SerializedName("repair_triggered") val repairTriggered: Boolean,
    @SerializedName("repair") val repair: RepairDetail?
)

// ─── 4. GET /readiness ──────────────────────────────────────────────
data class ReadinessResponse(
    @SerializedName("learner_id") val learnerId: String,
    @SerializedName("aggregate_score") val aggregateScore: Double,
    @SerializedName("breakdown") val breakdown: Map<String, Double>,
    @SerializedName("purpose") val purpose: String,
    @SerializedName("weights_used") val weightsUsed: Map<String, Double>?
)

// ─── UI Chat Message Entity ─────────────────────────────────────────
enum class MessageRole { USER, ASSISTANT, REPAIR, SYSTEM }

data class ChatMessage(
    val id: String,
    val role: MessageRole,
    val text: String,
    val errorType: String? = null,
    val strategy: String? = null,
    val accentScore: Int? = null,
    val timestamp: Long = System.currentTimeMillis()
)
