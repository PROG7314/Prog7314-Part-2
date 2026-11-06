package com.example.aegis.data.local.db

import com.example.aegis.domain.model.Listing
import com.example.aegis.domain.model.ListingTranslation
import com.example.aegis.domain.model.ListingType
import com.example.aegis.domain.model.Signup
import com.example.aegis.domain.model.SignupStatus
import com.google.gson.Gson
import java.time.Instant

/**
 * A Listing as plain JSON-friendly values. Listing itself holds a java.time.Instant, which Gson
 * can't be trusted to handle on every Android version, so the cache stores this instead.
 */
internal data class ListingSnapshot(
    val id: String,
    val organizationId: String?,
    val organizationName: String?,
    val title: String,
    val description: String,
    val causeCategories: List<String>,
    val skillsRequired: List<String>,
    val type: String,
    val dateTimeMillis: Long?,
    val spacesTotal: Int?,
    val spacesRemaining: Int?,
    val signupCount: Int?,
    val area: String,
    val minHours: Double,
    val images: List<String>,
    // Missing on rows cached before translations existed; Gson leaves it null.
    val translations: Map<String, ListingTranslation>? = null
)

private val gson = Gson()

fun Listing.toSnapshotJson(): String = gson.toJson(
    ListingSnapshot(
        id = id,
        organizationId = organizationId,
        organizationName = organizationName,
        title = title,
        description = description,
        causeCategories = causeCategories,
        skillsRequired = skillsRequired,
        type = type.apiValue,
        dateTimeMillis = dateTime?.toEpochMilli(),
        spacesTotal = spacesTotal,
        spacesRemaining = spacesRemaining,
        signupCount = signupCount,
        area = area,
        minHours = minHours,
        images = images,
        translations = translations
    )
)

/** Null if the text is missing or damaged - a bad cache row should never crash a screen. */
fun listingFromSnapshotJson(json: String?): Listing? {
    if (json.isNullOrBlank()) return null
    return try {
        val s = gson.fromJson(json, ListingSnapshot::class.java) ?: return null
        Listing(
            id = s.id,
            organizationId = s.organizationId,
            organizationName = s.organizationName,
            title = s.title,
            description = s.description,
            causeCategories = s.causeCategories,
            skillsRequired = s.skillsRequired,
            type = ListingType.fromApi(s.type),
            dateTime = s.dateTimeMillis?.let { Instant.ofEpochMilli(it) },
            spacesTotal = s.spacesTotal,
            spacesRemaining = s.spacesRemaining,
            signupCount = s.signupCount,
            area = s.area,
            minHours = s.minHours,
            images = s.images,
            translations = s.translations.orEmpty()
        )
    } catch (e: Exception) {
        // Gson throws JsonSyntaxException / NullPointerException-style errors on damaged rows.
        null
    }
}

// ----- Signup <-> cached row -----

fun Signup.toEntity(position: Int) = CachedCommitmentEntity(
    id = id,
    listingId = listingId,
    volunteerId = volunteerId,
    status = status.name,
    hoursCompleted = hoursCompleted,
    position = position,
    listingJson = listing?.toSnapshotJson()
)

fun CachedCommitmentEntity.toSignup() = Signup(
    id = id,
    listingId = listingId,
    volunteerId = volunteerId,
    volunteerName = null,
    status = SignupStatus.fromApi(status),
    hoursCompleted = hoursCompleted,
    listing = listingFromSnapshotJson(listingJson)
)