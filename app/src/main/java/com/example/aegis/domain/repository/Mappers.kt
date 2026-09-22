package com.example.aegis.data.repository

import com.example.aegis.data.remote.dto.CreateListingRequest
import com.example.aegis.data.remote.dto.HoursLogEntryDto
import com.example.aegis.data.remote.dto.HoursLogResponse
import com.example.aegis.data.remote.dto.ListingDto
import com.example.aegis.data.remote.dto.ProfileDto
import com.example.aegis.data.remote.dto.SignupDto
import com.example.aegis.data.remote.dto.UpdateListingRequest
import com.example.aegis.domain.model.HoursEntry
import com.example.aegis.domain.model.HoursSummary
import com.example.aegis.domain.model.Listing
import com.example.aegis.domain.model.ListingDraft
import com.example.aegis.domain.model.ListingType
import com.example.aegis.domain.model.Signup
import com.example.aegis.domain.model.SignupStatus
import com.example.aegis.domain.model.UserProfile
import java.time.Instant
import java.time.OffsetDateTime

// DTO and domain mapping. Keeps JSON quirks (nullable lists, ISO strings) out of the UI layer.

internal fun String?.toInstantOrNull(): Instant? =
    this?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }

internal fun ListingDto.toDomain() = Listing(
    id = id,
    organizationId = organizationId,
    organizationName = organizationName,
    title = title,
    description = description.orEmpty(),
    causeCategories = causeCategories.orEmpty(),
    skillsRequired = skillsRequired.orEmpty(),
    type = ListingType.fromApi(type),
    dateTime = dateTime.toInstantOrNull(),
    spacesTotal = spacesTotal,
    spacesRemaining = spacesRemaining,
    signupCount = signupCount,
    area = area.orEmpty(),
    minHours = minHours ?: 0.0,
    images = images.orEmpty()
)

internal fun SignupDto.toDomain() = Signup(
    id = id,
    listingId = listingId,
    volunteerId = volunteerId ?: volunteer?.id,
    volunteerName = volunteer?.name,
    status = SignupStatus.fromApi(status),
    hoursCompleted = hoursCompleted,
    listing = listing?.toDomain()
)

internal fun HoursLogEntryDto.toDomain() = HoursEntry(
    listingTitle = listingTitle.orEmpty(),
    hours = hoursCompleted,
    completedAt = completedAt.toInstantOrNull()
)

internal fun HoursLogResponse.toDomain() = HoursSummary(
    totalHours = totalHours,
    entries = entries.map { it.toDomain() }
)

internal fun ProfileDto.toDomain() = UserProfile(
    id = id,
    role = role,
    name = name.orEmpty(),
    bio = bio,
    description = description,
    interests = interests.orEmpty(),
    skills = skills.orEmpty(),
    preferredLanguage = preferredLanguage,
    notificationsEnabled = notificationsEnabled ?: true
)

internal fun ListingDraft.toCreateRequest() = CreateListingRequest(
    title = title,
    description = description,
    causeCategories = causeCategories,
    skillsRequired = skillsRequired,
    type = type.apiValue,
    dateTime = dateTime?.toString(),
    spacesTotal = spacesTotal,
    area = area,
    minHours = minHours,
    images = images
)

internal fun ListingDraft.toUpdateRequest() = UpdateListingRequest(
    title = title,
    description = description,
    causeCategories = causeCategories,
    skillsRequired = skillsRequired,
    type = type.apiValue,
    dateTime = dateTime?.toString(),
    spacesTotal = spacesTotal,
    area = area,
    minHours = minHours,
    images = images
)