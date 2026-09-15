package com.novara.app.network

import com.novara.app.data.MockDataProvider
import com.novara.app.data.ProfileStore
import com.novara.app.model.*

/**
 * Task 7: Repository with single Mock <-> Real Backend swap toggle.
 */
class NovaraRepository(private val profileStore: ProfileStore) {

    private val api = NovaraNetworkClient.api

    var isMockMode: Boolean
        get() = profileStore.useMock
        set(value) { profileStore.useMock = value }

    suspend fun createProfile(profile: LearnerProfile): Result<ProfileResponse> {
        return if (isMockMode) {
            Result.success(
                ProfileResponse(
                    learnerId = profile.learnerId,
                    profileCreated = true,
                    weaknessVector = mapOf("listening" to 0.8),
                    paceScore = 0.5,
                    confidenceScore = 0.5
                )
            )
        } else {
            try {
                // Ensure supported purpose for live backend ("trip" | "casual")
                val backendPurpose = if (profile.purpose == "exam" || profile.purpose == "casual") "casual" else "trip"
                val requestPayload = profile.copy(purpose = backendPurpose)
                val response = api.createProfile(requestPayload)
                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(Exception("HTTP ${response.code()}: ${response.errorBody()?.string()}"))
                }
            } catch (e: Exception) {
                // Return failure with fallback option
                Result.failure(e)
            }
        }
    }

    suspend fun getScenario(learnerId: String, fallbackPurpose: String = "trip"): Result<ScenarioResponse> {
        if (isMockMode || fallbackPurpose == "exam" || fallbackPurpose == "relocation") {
            return Result.success(MockDataProvider.getScenarioForPurpose(fallbackPurpose))
        }

        return try {
            val response = api.getScenario(learnerId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                // Fallback to mock on error to maintain crash-free experience
                Result.success(MockDataProvider.getScenarioForPurpose(fallbackPurpose))
            }
        } catch (e: Exception) {
            Result.success(MockDataProvider.getScenarioForPurpose(fallbackPurpose))
        }
    }

    suspend fun sendMessage(
        learnerId: String,
        scenarioId: String,
        message: String,
        turnNumber: Int,
        responseTimeMs: Long? = null
    ): Result<ConversationResponse> {
        if (isMockMode) {
            return Result.success(MockDataProvider.getReplyForTurn(turnNumber, message))
        }

        return try {
            val req = ConversationRequest(learnerId, scenarioId, message, turnNumber, responseTimeMs)
            val response = api.sendMessage(req)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                // Fallback to mock reply
                Result.success(MockDataProvider.getReplyForTurn(turnNumber, message))
            }
        } catch (e: Exception) {
            Result.success(MockDataProvider.getReplyForTurn(turnNumber, message))
        }
    }

    suspend fun getReadiness(learnerId: String, sampleKey: String? = null): Result<ReadinessResponse> {
        if (sampleKey != null && MockDataProvider.readinessPayloads.containsKey(sampleKey)) {
            return Result.success(MockDataProvider.readinessPayloads[sampleKey]!!)
        }

        if (isMockMode) {
            return Result.success(MockDataProvider.readinessPayloads["high"]!!)
        }

        return try {
            val response = api.getReadiness(learnerId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.success(MockDataProvider.readinessPayloads["mid"]!!)
            }
        } catch (e: Exception) {
            Result.success(MockDataProvider.readinessPayloads["mid"]!!)
        }
    }
}
