package com.example.aegis.data.remote.dto

// Data transfer objects (DTOs) for the Aegis REST API.

// ---------- Auth ----------
data class LoginRequest(val firebaseIdToken: String)

data class LoginResponse(val token: String, val user: AuthUserDto)

data class AuthUserDto(val id: String, val role: String? = null, val isNewUser: Boolean = false)

// role is "volunteer" or "organization".
data class RoleRequest(val role: String)

// ---------- Users / profile ----------
// Full profile (fields vary by role).
data class ProfileDto(
    val id: String,
    val email: String? = null,
    val role: String? = null,
    val name: String? = null,
    val profileImageUrl: String? = null,
    val bannerImageUrl: String? = null,
    val bio: String? = null,              // volunteer only
    val description: String? = null,      // organization only
    val interests: List<String>? = null,  // volunteer only
    val skills: List<String>? = null,     // volunteer only
    val preferredLanguage: String? = null,
    val notificationsEnabled: Boolean? = null,
    val darkMode: Boolean? = null
)

// Send only the fields you are changing - nulls are omitted from the JSON.
data class UpdateProfileRequest(
    val name: String? = null,
    val profileImageUrl: String? = null,
    val bannerImageUrl: String? = null,
    val bio: String? = null,
    val description: String? = null,
    val interests: List<String>? = null,
    val skills: List<String>? = null
)

// preferredLanguage: "en" | "af" | "zu" | "xh"
// Nulls are omitted from the JSON, so each call sends only the settings it names.
data class SettingsRequest(
    val preferredLanguage: String? = null,
    val notificationsEnabled: Boolean? = null,
    val darkMode: Boolean? = null
)

// ---------- Listings ----------
data class ListingsResponse(val items: List<ListingDto>)

data class ListingDto(
    val id: String,
    val organizationId: String? = null,
    val organizationName: String? = null,
    val title: String,
    val description: String? = null,
    val causeCategories: List<String>? = null,
    val skillsRequired: List<String>? = null,
    val type: String,                      // "Scheduled" | "Ongoing"
    val dateTime: String? = null,          // ISO-8601, Scheduled only
    val spacesTotal: Int? = null,          // Scheduled only
    val spacesRemaining: Int? = null,
    val signupCount: Int? = null,
    val area: String? = null,
    val minHours: Double? = null,
    val images: List<String>? = null,      // Firebase Storage URLs, max 3
    val translations: Map<String, ListingTranslationDto>? = null
)

// Optional Afrikaans / isiZulu text for a listing, keyed by language code.
data class ListingTranslationDto(val title: String? = null, val description: String? = null)

data class CreateListingRequest(
    val title: String,
    val description: String,
    val causeCategories: List<String>,
    val skillsRequired: List<String>,
    val type: String,
    val dateTime: String?,      // required if Scheduled
    val spacesTotal: Int?,      // required if Scheduled
    val area: String,
    val minHours: Double,
    val images: List<String>,
    val translations: Map<String, ListingTranslationDto> = emptyMap()
)

// Any subset of the fields above.
data class UpdateListingRequest(
    val title: String? = null,
    val description: String? = null,
    val causeCategories: List<String>? = null,
    val skillsRequired: List<String>? = null,
    val type: String? = null,
    val dateTime: String? = null,
    val spacesTotal: Int? = null,
    val area: String? = null,
    val minHours: Double? = null,
    val images: List<String>? = null,
    val translations: Map<String, ListingTranslationDto>? = null
)

// ---------- Sign-ups & hours ----------
data class SignupDto(
    val id: String,
    val listingId: String,
    val volunteerId: String? = null,
    val status: String,                       // "confirmed" | "completed" | "cancelled"
    val hoursCompleted: Double? = null,
    val signedUpAt: String? = null,
    val completedAt: String? = null,
    val volunteer: VolunteerSummaryDto? = null, // org view: "brief volunteer summary (name, id)" ToDo confirm name
    val listing: ListingDto? = null             // ToDo confirm if Commitments screen needs title/dateTime/area
)

data class VolunteerSummaryDto(val id: String? = null, val name: String? = null)

// Body for PATCH /signups/{id}/complete. null = "default to the listing's minHours".
data class CompleteSignupRequest(val hoursCompleted: Double? = null)

data class HoursLogEntryDto(
    val id: String? = null,
    val volunteerId: String? = null,
    val listingId: String? = null,
    val organizationId: String? = null,
    val listingTitle: String? = null,
    val hoursCompleted: Double = 0.0,
    val completedAt: String? = null
)

data class HoursLogResponse(val entries: List<HoursLogEntryDto> = emptyList(), val totalHours: Double = 0.0)

// ---------- Errors ----------
data class ErrorEnvelopeDto(val error: ErrorBodyDto? = null)

data class ErrorBodyDto(val code: String? = null, val message: String? = null, val field: String? = null)

// ---------- Messaging ----------
data class PartyDto(val id: String, val name: String? = null)

data class ConversationDto(
    val id: String,
    val listingId: String,
    val listingTitle: String? = null,
    val otherParty: PartyDto,
    val lastMessage: String? = null,
    val lastMessageAt: String? = null,
    val unreadCount: Int? = null
)

data class MessageDto(
    val id: String,
    val senderId: String,
    val text: String,
    val sentAt: String? = null
)

data class SendMessageRequest(val text: String)

// A volunteer sends {} (the API uses the caller); an organization names the volunteer to message.
data class OpenConversationRequest(val volunteerId: String? = null)
