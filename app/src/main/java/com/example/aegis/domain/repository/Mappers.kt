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
import com.example.aegis.domain.model.ListingTranslation
import com.example.aegis.domain.model.ListingType
import com.example.aegis.data.remote.dto.ListingTranslationDto
import com.example.aegis.domain.model.Signup
import com.example.aegis.domain.model.SignupStatus
import com.example.aegis.domain.model.UserProfile
import java.time.Instant
import java.time.OffsetDateTime
import com.example.aegis.data.remote.dto.ConversationDto
import com.example.aegis.data.remote.dto.MessageDto
import com.example.aegis.domain.model.ChatMessage
import com.example.aegis.domain.model.Conversation

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
    images = images.orEmpty(),
    translations = translations.orEmpty().mapValues { (_, t) -> ListingTranslation(t.title.orEmpty(), t.description.orEmpty()) }
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
    email = email,
    profileImageUrl = profileImageUrl,
    bannerImageUrl = bannerImageUrl,
    bio = bio,
    description = description,
    interests = interests.orEmpty(),
    skills = skills.orEmpty(),
    preferredLanguage = preferredLanguage,
    notificationsEnabled = notificationsEnabled ?: true,
    darkMode = darkMode ?: false
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
    images = images,
    translations = translations.toDto()
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
    images = images,
    translations = translations.toDto()
)

private fun Map<String, ListingTranslation>.toDto() =
    mapValues { (_, t) -> ListingTranslationDto(t.title, t.description) }

internal fun ConversationDto.toDomain() = Conversation(
    id = id,
    listingId = listingId,
    listingTitle = listingTitle.orEmpty(),
    otherPartyId = otherParty.id,
    otherPartyName = otherParty.name,
    lastMessage = lastMessage,
    lastMessageAt = lastMessageAt.toInstantOrNull(),
    unreadCount = unreadCount ?: 0
)

internal fun MessageDto.toDomain() = ChatMessage(
    id = id,
    senderId = senderId,
    text = text,
    sentAt = sentAt.toInstantOrNull()
)
