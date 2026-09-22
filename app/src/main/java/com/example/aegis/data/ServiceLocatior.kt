package com.example.aegis.data

import com.example.aegis.data.remote.AegisApi
import com.example.aegis.data.remote.ApiClient
import com.example.aegis.data.remote.TokenProvider
import com.example.aegis.data.repository.ApiListingRepository
import com.example.aegis.data.repository.ApiSignupRepository
import com.example.aegis.data.repository.ApiUserRepository
import com.example.aegis.domain.repository.ListingRepository
import com.example.aegis.domain.repository.SignupRepository
import com.example.aegis.domain.repository.UserRepository

/**
 * The place that builds the API client and repositories.
 * Call init() once in Application.onCreate() before any screen touches a repository.
 */
object ServiceLocator {

    private var apiInstance: AegisApi? = null

    fun init(baseUrl: String, tokenProvider: TokenProvider, debug: Boolean) {
        apiInstance = ApiClient.create(baseUrl, tokenProvider, debug)
    }

    fun api(): AegisApi =
        checkNotNull(apiInstance) { "ServiceLocator.init() must be called in Application.onCreate() first" }

    val listingRepository: ListingRepository by lazy { ApiListingRepository(api()) }
    val signupRepository: SignupRepository by lazy { ApiSignupRepository(api()) }
    val userRepository: UserRepository by lazy { ApiUserRepository(api()) }
}