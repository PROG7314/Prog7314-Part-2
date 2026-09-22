package com.example.aegis.domain.model

import java.time.Instant

/*
 * Domain models - what ViewModels and screens work with.
 */

sealed class ApiResult<out T> {
    data class Success<out T>(val data: T) : ApiResult<T>()
    data class Failure(val error: ApiError) : ApiResult<Nothing>()
}

data class ApiError(
    val kind: Kind,
    // Safe to show to the user
    val message: String,
    val httpCode: Int? = null,
    val code: String? = null,
    // Which form field the server complained about, if any.
    val field: String? = null
) {
    enum class Kind { NETWORK, UNAUTHORIZED, FORBIDDEN, NOT_FOUND, CONFLICT, VALIDATION, SERVER, UNKNOWN }
}

// ---------- Listings ----------
enum class ListingType(val apiValue: String) {
    SCHEDULED("Scheduled"),
    ONGOING("Ongoing");

    companion object {
        fun fromApi(value: String?): ListingType =
            values().firstOrNull { it.apiValue.equals(value, ignoreCase = true) } ?: ONGOING
    }
}

data class Listing(
    val id: String,
    val organizationId: String?,
    val organizationName: String?,
    val title: String,
    val description: String,
    val causeCategories: List<String>,
    val skillsRequired: List<String>,
    val type: ListingType,
    val dateTime: Instant?,
    val spacesTotal: Int?,
    val spacesRemaining: Int?,
    val signupCount: Int?,
    val area: String,
    val minHours: Double,
    val images: List<String>
) {

    fun isEnded(now: Instant = Instant.now()): Boolean =
        type == ListingType.SCHEDULED && dateTime != null && dateTime.isBefore(now)
}

// What the create/edit listing form produces.
data class ListingDraft(
    val title: String,
    val description: String,
    val causeCategories: List<String>,
    val skillsRequired: List<String>,
    val type: ListingType,
    val dateTime: Instant?,     // required if SCHEDULED
    val spacesTotal: Int?,      // required if SCHEDULED
    val area: String,
    val minHours: Double,
    val images: List<String> = emptyList()
)

// ---------- Sign-ups & hours ----------
enum class SignupStatus {
    CONFIRMED, COMPLETED, CANCELLED, UNKNOWN;

    companion object {
        fun fromApi(value: String?): SignupStatus =
            values().firstOrNull { it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
    }
}

data class Signup(
    val id: String,
    val listingId: String,
    val volunteerId: String?,
    val volunteerName: String?,   // filled on the organisation's view
    val status: SignupStatus,
    val hoursCompleted: Double?,
    val listing: Listing?         // filled on the volunteer's Commitments view
)

data class HoursEntry(val listingTitle: String, val hours: Double, val completedAt: Instant?)

data class HoursSummary(val totalHours: Double, val entries: List<HoursEntry>)

// ---------- User ----------
data class UserProfile(
    val id: String,
    val role: String?,
    val name: String,
    val bio: String?,
    val description: String?,
    val interests: List<String>,
    val skills: List<String>,
    val preferredLanguage: String?,
    val notificationsEnabled: Boolean
)