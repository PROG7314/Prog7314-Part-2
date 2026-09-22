package com.example.aegis.domain.repository

import com.example.aegis.domain.model.ApiResult
import com.example.aegis.domain.model.HoursSummary
import com.example.aegis.domain.model.Listing
import com.example.aegis.domain.model.ListingDraft
import com.example.aegis.domain.model.ListingType
import com.example.aegis.domain.model.Signup
import com.example.aegis.domain.model.UserProfile

/*
 * Repository contracts. ViewModels depend on these interfaces, never on Retrofit.
 */

interface ListingRepository {
    // Filters are applied by the API (server-side), not on the device.
    suspend fun getListings(
        causes: List<String> = emptyList(),
        skills: List<String> = emptyList(),
        type: ListingType? = null,
        area: String? = null
    ): ApiResult<List<Listing>>

    suspend fun getListing(id: String): ApiResult<Listing>
    suspend fun createListing(draft: ListingDraft): ApiResult<Listing>
    suspend fun updateListing(id: String, draft: ListingDraft): ApiResult<Listing>
    suspend fun deleteListing(id: String): ApiResult<Unit>
}

interface SignupRepository {
    // Volunteer signs up.
    suspend fun signUp(listingId: String): ApiResult<Signup>

    //A full Scheduled listing comes back as Failure(kind = CONFLICT).
    suspend fun getMySignups(): ApiResult<List<Signup>>

    // Volunteer cancels their own sign-up.
    suspend fun cancelSignup(signupId: String): ApiResult<Signup>

    // ----- Organisation only ------
    suspend fun getListingSignups(listingId: String): ApiResult<List<Signup>>

    // Organisation only. null hours = the API defaults to the listing's minHours.
    suspend fun completeSignup(signupId: String, hoursCompleted: Double? = null): ApiResult<Signup>

    suspend fun getHoursSummary(): ApiResult<HoursSummary>
}

interface UserRepository {
    suspend fun getMe(): ApiResult<UserProfile>

    // Sends the volunteer's selected causes and skills to the API (Custom Feature #1).
    suspend fun updateInterests(interests: List<String>, skills: List<String>): ApiResult<UserProfile>

    suspend fun updateSettings(preferredLanguage: String, notificationsEnabled: Boolean): ApiResult<Unit>
}