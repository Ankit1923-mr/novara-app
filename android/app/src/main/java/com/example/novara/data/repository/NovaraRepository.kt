package com.example.novara.data.repository

import com.example.novara.data.api.NovaraApiService
import com.example.novara.data.api.RetrofitClient
import com.example.novara.data.model.AuthResponse
import com.example.novara.data.model.ConversationHistoryResponse
import com.example.novara.data.model.ConversationRequest
import com.example.novara.data.model.ConversationResponse
import com.example.novara.data.model.LearnerState
import com.example.novara.data.model.LoginRequest
import com.example.novara.data.model.ProfileRequest
import com.example.novara.data.model.ProfileResponse
import com.example.novara.data.model.QuizResult
import com.example.novara.data.model.QuizSubmission
import com.example.novara.data.model.ReadinessResponse
import com.example.novara.data.model.RepairDetails
import com.example.novara.data.model.RepairRequest
import com.example.novara.data.model.ScenarioResponse
import com.example.novara.data.model.SignupRequest
import com.example.novara.data.model.TopicDetail
import com.example.novara.data.model.TopicSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NovaraRepository(
    private val apiService: NovaraApiService = RetrofitClient.apiService
) {

    suspend fun getHealth(): Result<Map<String, String>> =
        withContext(Dispatchers.IO) {
            runCatching { apiService.getHealth() }
        }

    suspend fun signup(request: SignupRequest): Result<AuthResponse> =
        withContext(Dispatchers.IO) {
            runCatching { apiService.signup(request) }
        }

    suspend fun login(request: LoginRequest): Result<AuthResponse> =
        withContext(Dispatchers.IO) {
            runCatching { apiService.login(request) }
        }

    suspend fun createProfile(request: ProfileRequest): Result<ProfileResponse> =
        withContext(Dispatchers.IO) {
            runCatching { apiService.createProfile(request) }
        }

    suspend fun getScenario(learnerId: String): Result<ScenarioResponse> =
        withContext(Dispatchers.IO) {
            runCatching { apiService.getScenario(learnerId) }
        }

    suspend fun sendConversation(request: ConversationRequest): Result<ConversationResponse> =
        withContext(Dispatchers.IO) {
            runCatching { apiService.sendConversation(request) }
        }

    suspend fun getConversationHistory(learnerId: String, scenarioId: String): Result<ConversationHistoryResponse> =
        withContext(Dispatchers.IO) {
            runCatching { apiService.getConversationHistory(learnerId, scenarioId) }
        }

    suspend fun submitRepair(request: RepairRequest): Result<RepairDetails> =
        withContext(Dispatchers.IO) {
            runCatching { apiService.submitRepair(request) }
        }

    suspend fun getReadiness(learnerId: String): Result<ReadinessResponse> =
        withContext(Dispatchers.IO) {
            runCatching { apiService.getReadiness(learnerId) }
        }

    suspend fun getMe(learnerId: String): Result<LearnerState> =
        withContext(Dispatchers.IO) {
            runCatching { apiService.getMe(learnerId) }
        }

    suspend fun getTopics(learnerId: String): Result<List<TopicSummary>> =
        withContext(Dispatchers.IO) {
            runCatching { apiService.getTopics(learnerId) }
        }

    suspend fun getTopicDetail(topicId: String): Result<TopicDetail> =
        withContext(Dispatchers.IO) {
            runCatching { apiService.getTopicDetail(topicId) }
        }

    suspend fun submitQuiz(topicId: String, submission: QuizSubmission): Result<QuizResult> =
        withContext(Dispatchers.IO) {
            runCatching { apiService.submitQuiz(topicId, submission) }
        }

    suspend fun setPacePreference(learnerId: String, pacePreference: Float): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching { apiService.setPacePreference(learnerId, pacePreference) }
        }
}
