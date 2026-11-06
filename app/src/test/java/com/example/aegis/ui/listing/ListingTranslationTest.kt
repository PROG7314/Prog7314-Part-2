package com.example.aegis.ui.listing

import com.example.aegis.domain.model.Listing
import com.example.aegis.domain.model.ListingTranslation
import com.example.aegis.domain.model.ListingType
import org.junit.Assert.assertEquals
import org.junit.Test

class ListingTranslationTest {

    private fun listing(translations: Map<String, ListingTranslation>) = Listing(
        id = "L1", organizationId = "o", organizationName = "Org",
        title = "Read to Kids", description = "Come read stories.",
        causeCategories = emptyList(), skillsRequired = emptyList(), type = ListingType.ONGOING,
        dateTime = null, spacesTotal = null, spacesRemaining = null, signupCount = null,
        area = "Athlone", minHours = 1.0, images = emptyList(), translations = translations
    )

    @Test
    fun usesTheTranslationForTheUsersLanguage() {
        val l = listing(mapOf("af" to ListingTranslation("Lees vir kinders", "Kom lees stories.")))
        assertEquals("Lees vir kinders", l.titleFor("af"))
        assertEquals("Kom lees stories.", l.descriptionFor("af"))
    }

    @Test
    fun fallsBackToTheOriginalWhenThereIsNoTranslation() {
        val l = listing(emptyMap())
        assertEquals("Read to Kids", l.titleFor("zu"))
        assertEquals("Come read stories.", l.descriptionFor("af"))
        assertEquals("Read to Kids", l.titleFor("en"))
    }

    @Test
    fun aBlankTranslatedFieldFallsBackPerField() {
        // Only the title was translated: the description still shows in English.
        val l = listing(mapOf("zu" to ListingTranslation("Funda nezingane", "")))
        assertEquals("Funda nezingane", l.titleFor("zu"))
        assertEquals("Come read stories.", l.descriptionFor("zu"))
    }
}
