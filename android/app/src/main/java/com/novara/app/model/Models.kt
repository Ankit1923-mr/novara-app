package com.novara.app.model

import com.google.gson.annotations.SerializedName

/**
 * NOVARA Data Models strictly matching `docs/api-contract.md`.
 */

// ─── 0. POST /auth/signup, /auth/login ───────────────────────────────
// Not part of the frozen 5-endpoint contract. Mirrors
// backend/app/models.py's SignupRequest/LoginRequest/AuthResponse.
data class SignupRequest(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String
)

data class LoginRequest(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String
)

data class AuthResponse(
    @SerializedName("token") val token: String,
    @SerializedName("email") val email: String,
    @SerializedName("learner_id") val learnerId: String,
    @SerializedName("has_profile") val hasProfile: Boolean
)

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
// Additive field, not part of the frozen 5-endpoint contract - exposes
// the Adaptive Engine's scoring breakdown for each candidate situation.
// Mirrors backend/app/models.py's SituationScore exactly. Nullable/
// default-empty since older backend responses (and any response that
// doesn't populate it) simply omit the field - Gson already tolerated
// this silently before this field existed in the Kotlin model; this
// just makes the data representable instead of silently discarded.
data class SituationScore(
    @SerializedName("situation_tag") val situationTag: String,
    @SerializedName("interest_overlap") val interestOverlap: List<String>,
    @SerializedName("weak_area_overlap") val weakAreaOverlap: List<String>,
    @SerializedName("novelty_bonus") val noveltyBonus: Int,
    @SerializedName("score") val score: Int
)

data class ScenarioResponse(
    @SerializedName("scenario_id") val scenarioId: String,
    @SerializedName("purpose") val purpose: String,
    @SerializedName("title") val title: String,
    @SerializedName("setting") val setting: String,
    @SerializedName("situation_tags") val situationTags: List<String>,
    @SerializedName("opening_line") val openingLine: String,
    @SerializedName("selection_scores") val selectionScores: List<SituationScore>? = null
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
    @SerializedName("translation") val translation: String? = null,
    @SerializedName("repair_triggered") val repairTriggered: Boolean = false,
    @SerializedName("repair") val repair: RepairDetail? = null,
    @SerializedName("pace_changed") val paceChanged: Boolean = false,
    @SerializedName("pace_change_reason") val paceChangeReason: String? = null,
    @SerializedName("pace_preference") val pacePreference: Double? = null
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
    val translation: String? = null,
    val originalEnglish: String? = null,
    val errorType: String? = null,
    val strategy: String? = null,
    val accentScore: Int? = null,
    val timestamp: Long = System.currentTimeMillis()
)
