package com.example.novara.data.api

import kotlinx.serialization.json.Json
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private const val DEFAULT_BASE_URL = "https://novara-api-dnhc.onrender.com/"
    var baseUrl: String = DEFAULT_BASE_URL
        private set

    var apiKey: String = "fXTFEdbxbsOE9fuU8E-xbYiwOEyRzgZBzJcwA_DaJ9c"
    var authToken: String? = null

    // Fallback DNS for Android Emulator DNS resolution issues
    private val resilientDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            return try {
                Dns.SYSTEM.lookup(hostname)
            } catch (e: UnknownHostException) {
                if (hostname.contains("novara-api-dnhc.onrender.com")) {
                    try {
                        InetAddress.getAllByName(hostname).toList()
                    } catch (_: Exception) {
                        // Render / Cloudflare IP fallback for novara-api-dnhc.onrender.com
                        listOf(
                            InetAddress.getByAddress(hostname, byteArrayOf(216.toByte(), 24.toByte(), 57.toByte(), 18.toByte())),
                            InetAddress.getByAddress(hostname, byteArrayOf(216.toByte(), 24.toByte(), 57.toByte(), 16.toByte()))
                        )
                    }
                } else {
                    throw e
                }
            }
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .dns(resilientDns)
        .addInterceptor(ApiHeaderInterceptor())
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var _apiService: NovaraApiService? = null

    val apiService: NovaraApiService
        get() {
            return _apiService ?: synchronized(this) {
                _apiService ?: Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(okHttpClient)
                    .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                    .build()
                    .create(NovaraApiService::class.java).also { _apiService = it }
            }
        }

    fun updateBaseUrl(newUrl: String) {
        val trimmed = newUrl.trim()
        if (trimmed.isBlank()) return
        val formattedUrl = if (trimmed.endsWith("/")) trimmed else "$trimmed/"
        if (baseUrl != formattedUrl) {
            baseUrl = formattedUrl
            synchronized(this) {
                _apiService = null
            }
        }
    }
}
