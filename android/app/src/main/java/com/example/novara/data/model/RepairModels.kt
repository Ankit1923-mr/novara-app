package com.example.novara.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RepairRequest(
    @SerialName("learner_utterance") val learnerUtterance: String,
    @SerialName("expected_pattern") val expectedPattern: String
)
