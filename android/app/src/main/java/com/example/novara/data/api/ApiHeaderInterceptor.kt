package com.example.novara.data.api

import okhttp3.Interceptor
import okhttp3.Response

class ApiHeaderInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val key = RetrofitClient.apiKey.ifBlank { "fXTFEdbxbsOE9fuU8E-xbYiwOEyRzgZBzJcwA_DaJ9c" }
        val requestBuilder = original.newBuilder()
            .header("X-API-Key", key)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")

        RetrofitClient.authToken?.let { token ->
            if (token.isNotBlank()) {
                requestBuilder.header("Authorization", "Bearer $token")
            }
        }

        val request = requestBuilder.build()
        return chain.proceed(request)
    }
}
