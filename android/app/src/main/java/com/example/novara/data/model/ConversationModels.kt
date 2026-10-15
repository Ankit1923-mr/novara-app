package com.example.novara.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ConversationRequest(
    @SerialName("learner_id") val learnerId: String,
    @SerialName("scenario_id") val scenarioId: String,
    val message: String,
    @SerialName("turn_number") val turnNumber: Int,
    @SerialName("response_time_ms") val responseTimeMs: Long? = null
)

@Serializable
data class ConversationResponse(
    val reply: String,
    @SerialName("repair_triggered") val repairTriggered: Boolean = false,
    val repair: RepairDetails? = null,
    @SerialName("pace_changed") val paceChanged: Boolean? = false,
    @SerialName("pace_change_reason") val paceChangeReason: String? = null,
    @SerialName("pace_preference") val pacePreference: Float? = null
)

@Serializable
data class RepairDetails(
    @SerialName("error_type") val errorType: String, // "lexical" | "grammar" | "register" | "comprehension"
    val strategy: String, // "clarify" | "rephrase" | "hint"
    @SerialName("repair_text") val repairText: String
)

enum class Sender {
    LEARNER,
    AI
}

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: Sender,
    val text: String,
    val turnNumber: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val repairDetails: RepairDetails? = null,
    val paceChangeReason: String? = null,
    val pacePreference: Float? = null
)
