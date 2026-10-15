package com.example.novara.data.api

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
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface NovaraApiService {

    @GET("/")
    suspend fun getHealth(): Map<String, String>

    @POST("auth/signup")
    suspend fun signup(
        @Body request: SignupRequest
    ): AuthResponse

    @POST("auth/login")
    suspend fun login(
        @Body request: LoginRequest
    ): AuthResponse

    @POST("profile")
    suspend fun createProfile(
        @Body request: ProfileRequest
    ): ProfileResponse

    @GET("scenario")
    suspend fun getScenario(
        @Query("learner_id") learnerId: String
    ): ScenarioResponse

    @POST("conversation")
    suspend fun sendConversation(
        @Body request: ConversationRequest
    ): ConversationResponse

    @GET("conversation/history")
    suspend fun getConversationHistory(
        @Query("learner_id") learnerId: String,
        @Query("scenario_id") scenarioId: String
    ): ConversationHistoryResponse

    @POST("repair")
    suspend fun submitRepair(
        @Body request: RepairRequest
    ): RepairDetails

    @GET("readiness")
    suspend fun getReadiness(
        @Query("learner_id") learnerId: String
    ): ReadinessResponse

    @GET("me")
    suspend fun getMe(
        @Query("learner_id") learnerId: String
    ): LearnerState

    @GET("topics")
    suspend fun getTopics(
        @Query("learner_id") learnerId: String
    ): List<TopicSummary>

    @GET("topics/{topic_id}")
    suspend fun getTopicDetail(
        @Path("topic_id") topicId: String
    ): TopicDetail

    @POST("topics/{topic_id}/submit")
    suspend fun submitQuiz(
        @Path("topic_id") topicId: String,
        @Body submission: QuizSubmission
    ): QuizResult

    @POST("profile/pace")
    suspend fun setPacePreference(
        @Query("learner_id") learnerId: String,
        @Query("pace_preference") pacePreference: Float
    )
}
