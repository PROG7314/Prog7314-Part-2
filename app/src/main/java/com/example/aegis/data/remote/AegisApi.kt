package com.example.aegis.data.remote

import com.example.aegis.data.remote.dto.CompleteSignupRequest
import com.example.aegis.data.remote.dto.CreateListingRequest
import com.example.aegis.data.remote.dto.HoursLogResponse
import com.example.aegis.data.remote.dto.ListingDto
import com.example.aegis.data.remote.dto.ListingsResponse
import com.example.aegis.data.remote.dto.LoginRequest
import com.example.aegis.data.remote.dto.LoginResponse
import com.example.aegis.data.remote.dto.ProfileDto
import com.example.aegis.data.remote.dto.RoleRequest
import com.example.aegis.data.remote.dto.SettingsRequest
import com.example.aegis.data.remote.dto.SignupDto
import com.example.aegis.data.remote.dto.UpdateListingRequest
import com.example.aegis.data.remote.dto.UpdateProfileRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import com.example.aegis.data.remote.dto.ConversationDto
import com.example.aegis.data.remote.dto.MessageDto
import com.example.aegis.data.remote.dto.OpenConversationRequest
import com.example.aegis.data.remote.dto.SendMessageRequest

/**
 * The Retrofit interface for the Aegis REST API.
 */
interface AegisApi {

    // ----- Auth -----
    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): LoginResponse

    @POST("auth/role")
    suspend fun setRole(@Body body: RoleRequest): ProfileDto

    @POST("auth/logout")
    suspend fun logout()

    // ----- Users -----
    @GET("users/me")
    suspend fun getMe(): ProfileDto

    @PUT("users/me")
    suspend fun updateMe(@Body body: UpdateProfileRequest): ProfileDto

    @PUT("users/me/settings")
    suspend fun updateSettings(@Body body: SettingsRequest)

    // ----- Listings -----
    // The filtering happens server-side. Pass a null to leave a filter off.
    @GET("listings")
    suspend fun getListings(
        @Query("cause") cause: String?,
        @Query("skill") skill: String?,
        @Query("type") type: String?,
        @Query("area") area: String?
    ): ListingsResponse

    @GET("listings/{id}")
    suspend fun getListing(@Path("id") id: String): ListingDto

    @POST("listings")
    suspend fun createListing(@Body body: CreateListingRequest): ListingDto

    @PUT("listings/{id}")
    suspend fun updateListing(@Path("id") id: String, @Body body: UpdateListingRequest): ListingDto

    @DELETE("listings/{id}")
    suspend fun deleteListing(@Path("id") id: String)

    // ----- Sign-ups & hours -----
    // Spec body is "{ }". 409 = Scheduled listing is full.
    @POST("listings/{id}/signups")
    suspend fun signUp(@Path("id") listingId: String, @Body body: Map<String, String>): SignupDto

    // Owning organisation only.
    @GET("listings/{id}/signups")
    suspend fun getListingSignups(@Path("id") listingId: String): List<SignupDto>

    // Current volunteer's commitments.
    @GET("users/me/signups")
    suspend fun getMySignups(): List<SignupDto>

    // Volunteer cancels their own sign-up - status "cancelled". This is what "Dismiss" maps to.
    @PATCH("signups/{id}")
    suspend fun cancelSignup(@Path("id") signupId: String): SignupDto

    // Owning organisation only. Also writes the hoursLog entry server-side.
    @PATCH("signups/{id}/complete")
    suspend fun completeSignup(@Path("id") signupId: String, @Body body: CompleteSignupRequest): SignupDto

    @GET("users/me/hours-log")
    suspend fun getHoursLog(): HoursLogResponse

    // ----- Messaging -----
    // Opens (or returns) the thread about a listing. Volunteers need a sign-up; an organization
    // passes the volunteer it wants to message.
    @POST("listings/{id}/conversations")
    suspend fun openConversation(
        @Path("id") listingId: String,
        @Body body: OpenConversationRequest
    ): ConversationDto

    @GET("conversations")
    suspend fun getConversations(): List<ConversationDto>

    // `since` (ISO date) returns only newer messages.
    @GET("conversations/{id}/messages")
    suspend fun getMessages(@Path("id") conversationId: String, @Query("since") since: String?): List<MessageDto>

    @POST("conversations/{id}/messages")
    suspend fun sendMessage(@Path("id") conversationId: String, @Body body: SendMessageRequest): MessageDto

    @PATCH("conversations/{id}/read")
    suspend fun markConversationRead(@Path("id") conversationId: String)
}
