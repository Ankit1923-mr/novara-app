package com.novara.app.network

import com.novara.app.data.MockDataProvider
import com.novara.app.data.ProfileStore
import com.novara.app.model.*

/**
 * Task 7: Repository with single Mock <-> Real Backend swap toggle.
 */
class NovaraRepository(
    private val profileStore: ProfileStore,
    private val api: NovaraApiService = NovaraNetworkClient.api
) {

    var isMockMode: Boolean
        get() = profileStore.useMock
        set(value) { profileStore.useMock = value }

    // Set once by MainActivity. Invoked whenever a live-mode call comes
    // back 401 (expired/invalid session token) so the app can clear the
    // stored token and return to login - once, not as a retry loop. A
    // 401 from a bad X-API-Key should never happen client-side (the key
    // is a build constant), so this only fires for the per-user token.
    var onUnauthorized: (() -> Unit)? = null

    private fun <T> Result<T>.reportIfUnauthorized(): Result<T> {
        if (isFailure && exceptionOrNull()?.message?.startsWith("HTTP 401") == true) {
            onUnauthorized?.invoke()
        }
        return this
    }

    // Accounts always hit the real backend - there is no meaningful "mock"
    // signup/login, and a learner can't practice offline without an
    // account existing server-side first (the ownership check needs a
    // real signed token, which only the real /auth endpoints can issue).
    suspend fun signup(email: String, password: String): Result<AuthResponse> {
        return try {
            val response = api.signup(SignupRequest(email, password))
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("HTTP ${response.code()}: ${response.errorBody()?.string()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun login(email: String, password: String): Result<AuthResponse> {
        return try {
            val response = api.login(LoginRequest(email, password))
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("HTTP ${response.code()}: ${response.errorBody()?.string()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

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
            }.reportIfUnauthorized()
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
                // Live mode must surface a real failure, not silently substitute
                // mock content while still showing a "LIVE BACKEND" indicator.
                Result.failure(Exception("HTTP ${response.code()}: ${response.errorBody()?.string()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
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
                // Live mode must surface a real failure, not silently substitute
                // a mock reply while still showing a "LIVE BACKEND" indicator.
                Result.failure(Exception("HTTP ${response.code()}: ${response.errorBody()?.string()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
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
                // Live mode must surface a real failure, not silently substitute
                // a sample payload while still showing a "LIVE BACKEND" indicator.
                Result.failure(Exception("HTTP ${response.code()}: ${response.errorBody()?.string()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }.reportIfUnauthorized()
    }
}
