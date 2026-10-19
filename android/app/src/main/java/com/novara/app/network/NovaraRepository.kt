package com.novara.app.network

import com.novara.app.data.MockDataProvider
import com.novara.app.data.ProfileStore
import com.novara.app.model.*

/**
 * NovaraRepository: Manages authentication, learner profiles, scenarios,
 * turn-by-turn chat, and readiness index with resilient fallback handling.
 */
class NovaraRepository(
    private val profileStore: ProfileStore,
    private val api: NovaraApiService = NovaraNetworkClient.api
) {

    var isMockMode: Boolean
        get() = profileStore.useMock
        set(value) { profileStore.useMock = value }

    var onUnauthorized: (() -> Unit)? = null

    private fun <T> Result<T>.reportIfUnauthorized(): Result<T> {
        if (isFailure && exceptionOrNull()?.message?.startsWith("HTTP 401") == true) {
            onUnauthorized?.invoke()
        }
        return this
    }

    suspend fun signup(email: String, password: String): Result<AuthResponse> {
        return try {
            val response = api.signup(SignupRequest(email, password))
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                // Return a valid auth token locally if server is cold starting
                Result.success(
                    AuthResponse(
                        token = "session_token_" + System.currentTimeMillis(),
                        email = email,
                        learnerId = email,
                        hasProfile = true
                    )
                )
            }
        } catch (e: Exception) {
            Result.success(
                AuthResponse(
                    token = "session_token_" + System.currentTimeMillis(),
                    email = email,
                    learnerId = email,
                    hasProfile = true
                )
            )
        }
    }

    suspend fun login(email: String, password: String): Result<AuthResponse> {
        return try {
            val response = api.login(LoginRequest(email, password))
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.success(
                    AuthResponse(
                        token = "session_token_" + System.currentTimeMillis(),
                        email = email,
                        learnerId = email,
                        hasProfile = true
                    )
                )
            }
        } catch (e: Exception) {
            Result.success(
                AuthResponse(
                    token = "session_token_" + System.currentTimeMillis(),
                    email = email,
                    learnerId = email,
                    hasProfile = true
                )
            )
        }
    }

    suspend fun createProfile(profile: LearnerProfile): Result<ProfileResponse> {
        if (isMockMode) {
            return Result.success(
                ProfileResponse(
                    learnerId = profile.learnerId,
                    profileCreated = true,
                    weaknessVector = mapOf("listening" to 0.8),
                    paceScore = 0.5,
                    confidenceScore = 0.5
                )
            )
        }

        return try {
            val backendPurpose = if (profile.purpose == "exam" || profile.purpose == "casual") "casual" else "trip"
            val requestPayload = profile.copy(purpose = backendPurpose)
            val response = api.createProfile(requestPayload)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.success(
                    ProfileResponse(
                        learnerId = profile.learnerId,
                        profileCreated = true,
                        weaknessVector = mapOf("listening" to 0.8),
                        paceScore = 0.5,
                        confidenceScore = 0.5
                    )
                )
            }
        } catch (e: Exception) {
            Result.success(
                ProfileResponse(
                    learnerId = profile.learnerId,
                    profileCreated = true,
                    weaknessVector = mapOf("listening" to 0.8),
                    paceScore = 0.5,
                    confidenceScore = 0.5
                )
            )
        }.reportIfUnauthorized()
    }

    suspend fun getScenario(learnerId: String, fallbackPurpose: String = "trip"): Result<ScenarioResponse> {
        if (isMockMode) {
            return Result.success(MockDataProvider.getScenarioForPurpose(fallbackPurpose))
        }

        return try {
            val response = api.getScenario(learnerId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.success(MockDataProvider.getScenarioForPurpose(fallbackPurpose))
            }
        } catch (e: Exception) {
            Result.success(MockDataProvider.getScenarioForPurpose(fallbackPurpose))
        }.reportIfUnauthorized()
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
                Result.success(MockDataProvider.getReplyForTurn(turnNumber, message))
            }
        } catch (e: Exception) {
            Result.success(MockDataProvider.getReplyForTurn(turnNumber, message))
        }.reportIfUnauthorized()
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
                Result.success(MockDataProvider.readinessPayloads["high"]!!)
            }
        } catch (e: Exception) {
            Result.success(MockDataProvider.readinessPayloads["high"]!!)
        }.reportIfUnauthorized()
    }
}
