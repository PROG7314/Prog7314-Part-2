package com.example.aegis.data.local

import com.example.aegis.data.local.db.listingFromSnapshotJson
import com.example.aegis.data.local.db.toSnapshotJson
import com.example.aegis.data.repository.listingQueryKey
import com.example.aegis.domain.model.Listing
import com.example.aegis.domain.model.ListingType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class OfflineCacheTest {

    private val listing = Listing(
        id = "l1",
        organizationId = "org1",
        organizationName = "Kids Home",
        title = "Read to Kids",
        description = "Saturday reading club",
        causeCategories = listOf("Children & Youth"),
        skillsRequired = listOf("Tutoring"),
        type = ListingType.SCHEDULED,
        dateTime = Instant.parse("2026-11-07T08:00:00Z"),
        spacesTotal = 5,
        spacesRemaining = 3,
        signupCount = 2,
        area = "Observatory",
        minHours = 2.5,
        images = listOf("https://example.com/a.jpg")
    )

    @Test
    fun listingSurvivesTheCacheRoundTrip() {
        assertEquals(listing, listingFromSnapshotJson(listing.toSnapshotJson()))
    }

    @Test
    fun ongoingListingWithNoDateRoundTrips() {
        val ongoing = listing.copy(type = ListingType.ONGOING, dateTime = null, spacesTotal = null, spacesRemaining = null)
        assertEquals(ongoing, listingFromSnapshotJson(ongoing.toSnapshotJson()))
    }

    @Test
    fun damagedOrMissingCacheRowsGiveNullInsteadOfCrashing() {
        assertNull(listingFromSnapshotJson(null))
        assertNull(listingFromSnapshotJson(""))
        assertNull(listingFromSnapshotJson("{not json"))
    }

    @Test
    fun queryKeyIgnoresOrderAndCaseOfTheFilters() {
        val a = listingQueryKey(listOf("B", "A"), listOf("Y", "X"), null, " Observatory ")
        val b = listingQueryKey(listOf("A", "B"), listOf("X", "Y"), null, "observatory")
        assertEquals(a, b)
    }

    @Test
    fun differentFiltersGetTheirOwnCacheSlot() {
        val all = listingQueryKey(emptyList(), emptyList(), null, null)
        val scheduled = listingQueryKey(emptyList(), emptyList(), ListingType.SCHEDULED, null)
        val matched = listingQueryKey(listOf("Elderly Care"), emptyList(), null, null)
        assertNotEquals(all, scheduled)
        assertNotEquals(all, matched)
    }
}