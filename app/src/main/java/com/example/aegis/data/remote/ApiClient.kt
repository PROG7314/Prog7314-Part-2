package com.example.aegis.data.remote

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * How the network layer gets the backend-issued JWT.
 * Returning null = not logged in.
 */
interface TokenProvider {
    fun getToken(): String?
}

/**
 * Adds "Authorization: Bearer <JWT>" to every request except POST auth/login.
 */
class AuthInterceptor(private val tokenProvider: TokenProvider) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val isLogin = original.url.encodedPath.endsWith("/auth/login")
        val token = tokenProvider.getToken()

        val request = if (!isLogin && !token.isNullOrBlank()) {
            original.newBuilder().header("Authorization", "Bearer $token").build()
        } else {
            original
        }
        return chain.proceed(request)
    }
}

object ApiClient {

    /**
     * @param baseUrl - full base including the version prefix
     * @param debug - true = log request/response bodies to Logcat
     */
    fun create(baseUrl: String, tokenProvider: TokenProvider, debug: Boolean): AegisApi {
        val logging = HttpLoggingInterceptor().apply {
            level = if (debug) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
            redactHeader("Authorization")
        }

        // Long timeouts on purpose
        val client = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(AuthInterceptor(tokenProvider))
            .addInterceptor(logging) // after auth so the (redacted) header shows up in the log
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl.trimEnd('/') + "/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AegisApi::class.java)
    }
}