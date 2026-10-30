package com.example.aegis.data.repository

import com.example.aegis.data.remote.AegisApi
import com.example.aegis.data.remote.dto.CompleteSignupRequest
import com.example.aegis.data.remote.dto.SettingsRequest
import com.example.aegis.data.remote.dto.UpdateProfileRequest
import com.example.aegis.data.remote.safeApiCall
import com.example.aegis.domain.model.ApiResult
import com.example.aegis.domain.model.HoursSummary
import com.example.aegis.domain.model.Listing
import com.example.aegis.domain.model.ListingDraft
import com.example.aegis.domain.model.ListingType
import com.example.aegis.domain.model.Signup
import com.example.aegis.domain.model.UserProfile
import com.example.aegis.domain.repository.ListingRepository
import com.example.aegis.domain.repository.SignupRepository
import com.example.aegis.domain.repository.UserRepository
import com.example.aegis.data.remote.dto.OpenConversationRequest
import com.example.aegis.data.remote.dto.SendMessageRequest
import com.example.aegis.domain.model.ChatMessage
import com.example.aegis.domain.model.Conversation
import com.example.aegis.domain.repository.MessageRepository
import java.time.Instant

/*
 * Retrofit-backed repositories. Every call goes through safeApiCall, so failures come back as
 * ApiResult.
 */

class ApiListingRepository(private val api: AegisApi) : ListingRepository {

    override suspend fun getListings(
        causes: List<String>,
        skills: List<String>,
        type: ListingType?,
        area: String?
    ): ApiResult<List<Listing>> = safeApiCall(TAG) {
        // For the multiple values are sent comma-separated ("Animals,Children").
        api.getListings(
            cause = causes.joinToString(",").ifBlank { null },
            skill = skills.joinToString(",").ifBlank { null },
            type = type?.apiValue,
            area = area?.takeIf { it.isNotBlank() }
        ).items.map { it.toDomain() }
    }

    override suspend fun getListing(id: String): ApiResult<Listing> =
        safeApiCall(TAG) { api.getListing(id).toDomain() }

    override suspend fun createListing(draft: ListingDraft): ApiResult<Listing> =
        safeApiCall(TAG) { api.createListing(draft.toCreateRequest()).toDomain() }

    override suspend fun updateListing(id: String, draft: ListingDraft): ApiResult<Listing> =
        safeApiCall(TAG) { api.updateListing(id, draft.toUpdateRequest()).toDomain() }

    override suspend fun deleteListing(id: String): ApiResult<Unit> =
        safeApiCall(TAG) { api.deleteListing(id) }

    private companion object {
        const val TAG = "ListingRepository"
    }
}

class ApiSignupRepository(private val api: AegisApi) : SignupRepository {

    override suspend fun signUp(listingId: String): ApiResult<Signup> =
        safeApiCall(TAG) { api.signUp(listingId, emptyMap()).toDomain() }

    override suspend fun getMySignups(): ApiResult<List<Signup>> =
        safeApiCall(TAG) { api.getMySignups().map { it.toDomain() } }

    override suspend fun cancelSignup(signupId: String): ApiResult<Signup> =
        safeApiCall(TAG) { api.cancelSignup(signupId).toDomain() }

    override suspend fun getListingSignups(listingId: String): ApiResult<List<Signup>> =
        safeApiCall(TAG) { api.getListingSignups(listingId).map { it.toDomain() } }

    override suspend fun completeSignup(signupId: String, hoursCompleted: Double?): ApiResult<Signup> =
        safeApiCall(TAG) { api.completeSignup(signupId, CompleteSignupRequest(hoursCompleted)).toDomain() }

    override suspend fun getHoursSummary(): ApiResult<HoursSummary> =
        safeApiCall(TAG) { api.getHoursLog().toDomain() }

    private companion object {
        const val TAG = "SignupRepository"
    }
}

class ApiUserRepository(private val api: AegisApi) : UserRepository {

    override suspend fun getMe(): ApiResult<UserProfile> =
        safeApiCall(TAG) { api.getMe().toDomain() }

    override suspend fun updateInterests(interests: List<String>, skills: List<String>): ApiResult<UserProfile> =
        safeApiCall(TAG) {
            api.updateMe(UpdateProfileRequest(interests = interests, skills = skills)).toDomain()
        }

    override suspend fun updateSettings(
        preferredLanguage: String?,
        notificationsEnabled: Boolean?,
        darkMode: Boolean?
    ): ApiResult<Unit> =
        safeApiCall(TAG) { api.updateSettings(SettingsRequest(preferredLanguage, notificationsEnabled, darkMode)) }

    private companion object {
        const val TAG = "UserRepository"
    }
}

class ApiMessageRepository(private val api: AegisApi) : MessageRepository {

    override suspend fun openConversation(listingId: String, volunteerId: String?): ApiResult<Conversation> =
        safeApiCall(TAG) { api.openConversation(listingId, OpenConversationRequest(volunteerId)).toDomain() }

    override suspend fun getConversations(): ApiResult<List<Conversation>> =
        safeApiCall(TAG) { api.getConversations().map { it.toDomain() } }

    override suspend fun getMessages(conversationId: String, since: Instant?): ApiResult<List<ChatMessage>> =
        safeApiCall(TAG) { api.getMessages(conversationId, since?.toString()).map { it.toDomain() } }

    override suspend fun sendMessage(conversationId: String, text: String): ApiResult<ChatMessage> =
        safeApiCall(TAG) { api.sendMessage(conversationId, SendMessageRequest(text)).toDomain() }

    override suspend fun markRead(conversationId: String): ApiResult<Unit> =
        safeApiCall(TAG) { api.markConversationRead(conversationId) }

    private companion object {
        const val TAG = "MessageRepository"
    }
}
