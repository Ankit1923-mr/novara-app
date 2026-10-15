package com.example.novara.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TopicSummary(
    @SerialName("topic_id") val topicId: String,
    val title: String,
    @SerialName("order_index") val orderIndex: Int,
    val completed: Boolean
)

@Serializable
data class TopicDetail(
    @SerialName("topic_id") val topicId: String,
    val title: String,
    val vocabulary: List<VocabItem> = emptyList(),
    val quiz: List<QuizQuestion> = emptyList()
)

@Serializable
data class VocabItem(
    val phrase: String,
    val meaning: String,
    @SerialName("cultural_note") val culturalNote: String
)

@Serializable
data class QuizQuestion(
    val question: String,
    val options: List<String>,
    @SerialName("correct_index") val correctIndex: Int
)

@Serializable
data class QuizSubmission(
    @SerialName("learner_id") val learnerId: String,
    val answers: List<Int>
)

@Serializable
data class QuizResult(
    @SerialName("topic_id") val topicId: String,
    val score: Float,
    @SerialName("correct_count") val correctCount: Int,
    val total: Int,
    @SerialName("missed_phrases") val missedPhrases: List<String> = emptyList(),
    @SerialName("pace_changed") val paceChanged: Boolean = false,
    @SerialName("pace_change_reason") val paceChangeReason: String? = null,
    @SerialName("pace_preference") val pacePreference: Float? = null
)
