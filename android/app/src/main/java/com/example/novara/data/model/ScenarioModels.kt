package com.example.novara.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ScenarioResponse(
    @SerialName("scenario_id") val scenarioId: String,
    val purpose: String,
    val title: String,
    val setting: String,
    @SerialName("situation_tags") val situationTags: List<String> = emptyList(),
    @SerialName("opening_line") val openingLine: String,
    @SerialName("selection_scores") val selectionScores: List<SelectionScore>? = null
)

@Serializable
data class SelectionScore(
    @SerialName("situation_tag") val situationTag: String,
    @SerialName("interest_overlap") val interestOverlap: List<String> = emptyList(),
    @SerialName("weak_area_overlap") val weakAreaOverlap: List<String> = emptyList(),
    @SerialName("novelty_bonus") val noveltyBonus: Float = 0f,
    val score: Float = 0f
)
