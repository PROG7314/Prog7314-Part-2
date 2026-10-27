package com.example.aegis.data.remote.dto

/*
 * POST /sync/signups (backend README, section 6c).
 * The app sends what was queued offline; every change comes back with a result.
 */

/**
 * action: "signup" | "cancel".
 * id: the app's UUID for the sign-up (used for both actions).
 * listingId: only for "signup".
 * updatedAt: ISO-8601 time the user actually tapped.
 */
data class SyncChangeDto(
    val id: String,
    val action: String,
    val listingId: String? = null,
    val updatedAt: String
)

data class SyncRequest(val changes: List<SyncChangeDto>)

/**
 * status: "applied" | "duplicate" | "stale" | "rejected".
 * code / message: only when rejected (e.g. listing_full, listing_ended, not_found, already_completed).
 */
data class SyncResultDto(
    val id: String,
    val action: String,
    val status: String,
    val signup: SignupDto? = null,
    val code: String? = null,
    val message: String? = null
)

data class SyncResponse(
    val results: List<SyncResultDto> = emptyList(),
    val serverTime: String? = null
)