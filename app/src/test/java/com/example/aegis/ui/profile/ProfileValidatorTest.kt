package com.example.aegis.ui.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileValidatorTest {
    @Test fun blankNameIsRejected() =
        assertEquals(ProfileValidator.Problem.NAME_REQUIRED, ProfileValidator.validate("  ", "", false))

    @Test fun nameOverLimitIsRejected() =
        assertEquals(ProfileValidator.Problem.NAME_TOO_LONG, ProfileValidator.validate("a".repeat(101), "", false))

    @Test fun bioOverFiveHundredIsRejectedForVolunteers() =
        assertEquals(ProfileValidator.Problem.ABOUT_TOO_LONG, ProfileValidator.validate("Sam", "a".repeat(501), false))

    @Test fun sameTextIsFineAsAnOrganisationDescription() =
        assertNull(ProfileValidator.validate("Helping Hands", "a".repeat(501), true))

    @Test fun validProfilePasses() =
        assertNull(ProfileValidator.validate("Sam", "Hello", false))
}