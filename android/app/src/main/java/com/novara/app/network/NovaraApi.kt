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

    @POST("/auth/signup")
    suspend fun signup(@Body request: SignupRequest): Response<AuthResponse>

    @POST("/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

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

    // Set by MainActivity from TokenStore at startup and after every
    // successful login/signup/logout. Attached as a bearer token
    // alongside (never instead of) the existing X-API-Key header -
    // the shared key alone is never sufficient proof of learner
    // identity for any endpoint the backend now enforces ownership on.
    @Volatile
    var bearerToken: String? = null

    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        val builder = original.newBuilder()
            .header("X-API-Key", API_KEY)
            .header("Content-Type", "application/json")
            .method(original.method, original.body)
        bearerToken?.let { builder.header("Authorization", "Bearer $it") }
        chain.proceed(builder.build())
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val resilientDns = object : okhttp3.Dns {
        override fun lookup(hostname: String): List<java.net.InetAddress> {
            return try {
                okhttp3.Dns.SYSTEM.lookup(hostname)
            } catch (e: java.net.UnknownHostException) {
                if (hostname.contains("novara-api-dnhc.onrender.com")) {
                    try {
                        java.net.InetAddress.getAllByName(hostname).toList()
                    } catch (_: Exception) {
                        listOf(
                            java.net.InetAddress.getByAddress(hostname, byteArrayOf(216.toByte(), 24.toByte(), 57.toByte(), 18.toByte())),
                            java.net.InetAddress.getByAddress(hostname, byteArrayOf(216.toByte(), 24.toByte(), 57.toByte(), 16.toByte()))
                        )
                    }
                } else {
                    throw e
                }
            }
        }
    }

    // Free tier on Render can take 30-50s to cold-start; give 60s timeout
    private val okHttpClient = OkHttpClient.Builder()
        .dns(resilientDns)
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
