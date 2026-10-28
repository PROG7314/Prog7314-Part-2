package com.example.aegis.ui.listing

import com.example.aegis.domain.model.ListingType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

class ListingFormValidatorTest {

    private val now = Instant.parse("2026-10-27T10:00:00Z")
    private val zone = ZoneOffset.UTC

    private fun scheduled() = ListingFormInput(
        title = "Soup kitchen shift",
        description = "Help serve lunch",
        area = "Wynberg",
        minHoursText = "2",
        type = ListingType.SCHEDULED,
        category = "Soup Kitchens",
        skills = listOf("Cooking or Food Prep"),
        date = LocalDate.of(2026, 11, 5),
        time = LocalTime.of(9, 0),
        spacesText = "10"
    )

    private fun invalid(input: ListingFormInput): FormResult.Invalid =
        ListingFormValidator.validate(input, now, zone) as FormResult.Invalid

    @Test
    fun validScheduledListingBuildsDraft() {
        val result = ListingFormValidator.validate(scheduled(), now, zone) as FormResult.Valid
        assertEquals("Soup kitchen shift", result.draft.title)
        assertEquals(10, result.draft.spacesTotal)
        assertEquals(2.0, result.draft.minHours, 0.0)
        assertEquals(Instant.parse("2026-11-05T09:00:00Z"), result.draft.dateTime)
    }

    @Test
    fun ongoingListingHasNoDateOrSpaces() {
        val input = scheduled().copy(type = ListingType.ONGOING, date = null, time = null, spacesText = "")
        val result = ListingFormValidator.validate(input, now, zone) as FormResult.Valid
        assertNull(result.draft.dateTime)
        assertNull(result.draft.spacesTotal)
    }

    @Test
    fun blankTitleIsReportedFirst() {
        val r = invalid(scheduled().copy(title = "   ", description = ""))
        assertEquals(FormField.TITLE, r.field)
    }

    @Test
    fun overlongFieldsAreRejected() {
        assertEquals(FormField.TITLE, invalid(scheduled().copy(title = "a".repeat(121))).field)
        assertEquals(FormField.DESCRIPTION, invalid(scheduled().copy(description = "a".repeat(2001))).field)
        assertEquals(FormField.AREA, invalid(scheduled().copy(area = "a".repeat(201))).field)
    }

    @Test
    fun missingDateOrTimeIsRejected() {
        assertEquals(FormField.DATE_TIME, invalid(scheduled().copy(date = null)).field)
        assertEquals(FormField.DATE_TIME, invalid(scheduled().copy(time = null)).field)
    }

    @Test
    fun pastDateTimeIsRejected() {
        val r = invalid(scheduled().copy(date = LocalDate.of(2026, 10, 27), time = LocalTime.of(9, 0)))
        assertEquals(FormField.DATE_TIME, r.field)
    }

    @Test
    fun spacesMustBeAWholeNumberBetweenOneAndTenThousand() {
        assertEquals(FormField.SPACES, invalid(scheduled().copy(spacesText = "")).field)
        assertEquals(FormField.SPACES, invalid(scheduled().copy(spacesText = "0")).field)
        assertEquals(FormField.SPACES, invalid(scheduled().copy(spacesText = "2.5")).field)
        assertEquals(FormField.SPACES, invalid(scheduled().copy(spacesText = "10001")).field)
    }

    @Test
    fun hoursAcceptCommaDecimalAndRejectBadValues() {
        val ok = ListingFormValidator.validate(scheduled().copy(minHoursText = "2,5"), now, zone) as FormResult.Valid
        assertEquals(2.5, ok.draft.minHours, 0.0)
        assertEquals(FormField.HOURS, invalid(scheduled().copy(minHoursText = "")).field)
        assertEquals(FormField.HOURS, invalid(scheduled().copy(minHoursText = "-1")).field)
        assertEquals(FormField.HOURS, invalid(scheduled().copy(minHoursText = "1001")).field)
        assertTrue(invalid(scheduled().copy(minHoursText = "abc")).message.isNotBlank())
    }

    @Test
    fun editingKeepsAnUnchangedPastDateButStillRejectsANewPastOne() {
        val past = LocalDate.of(2026, 10, 20)
        val input = scheduled().copy(date = past, time = LocalTime.of(9, 0))
        val original = Instant.parse("2026-10-20T09:00:00Z")
        assertTrue(ListingFormValidator.validate(input, now, zone, original) is FormResult.Valid)

        val moved = input.copy(date = LocalDate.of(2026, 10, 21))
        val r = ListingFormValidator.validate(moved, now, zone, original) as FormResult.Invalid
        assertEquals(FormField.DATE_TIME, r.field)
    }
}