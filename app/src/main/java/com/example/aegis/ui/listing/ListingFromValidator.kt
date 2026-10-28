package com.example.aegis.ui.listing

import com.example.aegis.domain.model.ListingDraft
import com.example.aegis.domain.model.ListingType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/*
 * The create-listing form's rules, kept out of the Fragment so they can be unit tested without a
 * phone. The limits match the backend's validators (title 120, description 2000, area 200,
 * minHours 0-1000, spaces 1-10000), so a form that passes here won't come back as a 400.
 */

enum class FormField { TITLE, DESCRIPTION, AREA, DATE_TIME, SPACES, HOURS }

/** The raw values on screen. Numbers are still text, exactly as typed. */
data class ListingFormInput(
    val title: String,
    val description: String,
    val area: String,
    val minHoursText: String,
    val type: ListingType,
    val category: String,
    val skills: List<String>,
    val date: LocalDate?,
    val time: LocalTime?,
    val spacesText: String
)

sealed interface FormResult {
    /** A draft ready to send, except that its images are filled in after the photos are uploaded. */
    data class Valid(val draft: ListingDraft) : FormResult

    /** The first problem found, top of the form first. */
    data class Invalid(val field: FormField, val message: String) : FormResult
}

object ListingFormValidator {

    const val MAX_TITLE = 120
    const val MAX_DESCRIPTION = 2000
    const val MAX_AREA = 200
    const val MAX_HOURS = 1000.0
    const val MAX_SPACES = 10_000

    fun validate(
        input: ListingFormInput,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
        /** Editing: the listing's saved date/time. Leaving it unchanged is allowed even if it has passed. */
        originalDateTime: Instant? = null
    ): FormResult {
        val title = input.title.trim()
        val description = input.description.trim()
        val area = input.area.trim()

        if (title.isEmpty()) return invalid(FormField.TITLE, "Title is required")
        if (title.length > MAX_TITLE) return invalid(FormField.TITLE, "Title must be $MAX_TITLE characters or fewer")
        if (description.isEmpty()) return invalid(FormField.DESCRIPTION, "Description is required")
        if (description.length > MAX_DESCRIPTION) {
            return invalid(FormField.DESCRIPTION, "Description must be $MAX_DESCRIPTION characters or fewer")
        }
        if (area.isEmpty()) return invalid(FormField.AREA, "Area is required")
        if (area.length > MAX_AREA) return invalid(FormField.AREA, "Area must be $MAX_AREA characters or fewer")

        var dateTime: Instant? = null
        var spacesTotal: Int? = null
        if (input.type == ListingType.SCHEDULED) {
            if (input.date == null || input.time == null) {
                return invalid(FormField.DATE_TIME, "Pick both a date and a time")
            }
            dateTime = LocalDateTime.of(input.date, input.time).atZone(zone).toInstant()
            // A listing in the past counts as ended, so volunteers would never see it.
            val unchanged = originalDateTime != null && dateTime == originalDateTime.truncatedTo(ChronoUnit.MINUTES)
            if (!dateTime.isAfter(now) && !unchanged) {
                return invalid(FormField.DATE_TIME, "Pick a date and time in the future")
            }

            val spacesText = input.spacesText.trim()
            if (spacesText.isEmpty()) return invalid(FormField.SPACES, "Enter how many volunteers you need")
            val spaces = spacesText.toIntOrNull()
            if (spaces == null || spaces < 1) {
                return invalid(FormField.SPACES, "Enter a whole number of spaces (1 or more)")
            }
            if (spaces > MAX_SPACES) return invalid(FormField.SPACES, "At most $MAX_SPACES spaces")
            spacesTotal = spaces
        }

        // Some keyboards type a comma as the decimal point ("2,5").
        val minHours = input.minHoursText.trim().replace(',', '.').toDoubleOrNull()
        if (minHours == null || !minHours.isFinite()) {
            return invalid(FormField.HOURS, "Enter the minimum hours (e.g. 2)")
        }
        if (minHours < 0) return invalid(FormField.HOURS, "Hours can't be negative")
        if (minHours > MAX_HOURS) return invalid(FormField.HOURS, "Hours must be ${MAX_HOURS.toInt()} or less")

        return FormResult.Valid(
            ListingDraft(
                title = title,
                description = description,
                causeCategories = listOf(input.category),
                skillsRequired = input.skills,
                type = input.type,
                dateTime = dateTime,      // Ongoing listings have neither a date nor a cap
                spacesTotal = spacesTotal,
                area = area,
                minHours = minHours,
                images = emptyList()
            )
        )
    }

    private fun invalid(field: FormField, message: String) = FormResult.Invalid(field, message)
}