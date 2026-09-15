package com.novara.app.network

import com.novara.app.model.*
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface NovaraApiService {

    @POST("/profile")
    suspend fun createProfile(@Body profile: LearnerProfile): Response<ProfileResponse>

    @GET("/scenario")
    suspend fun getScenario(@Query("learner_id") learnerId: String): Response<ScenarioResponse>

    @POST("/conversation")
    suspend fun sendMessage(@Body request: ConversationRequest): Response<ConversationResponse>

    @GET("/readiness")
    suspend fun getReadiness(@Query("learner_id") learnerId: String): Response<ReadinessResponse>

    @GET("/")
    suspend fun checkHealth(): Response<Map<String, Any>>
}

object NovaraNetworkClient {
    private const val BASE_URL = "https://novara-api-dnhc.onrender.com"
    private const val API_KEY = "fXTFEdbxbsOE9fuU8E-xbYiwOEyRzgZBzJcwA_DaJ9c"

    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        val request = original.newBuilder()
            .header("X-API-Key", API_KEY)
            .header("Content-Type", "application/json")
            .method(original.method, original.body)
            .build()
        chain.proceed(request)
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    // Free tier on Render can take 30-50s to cold-start; give 60s timeout
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val api: NovaraApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NovaraApiService::class.java)
    }
}
