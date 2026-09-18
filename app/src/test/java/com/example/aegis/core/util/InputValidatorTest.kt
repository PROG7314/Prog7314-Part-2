package com.example.aegis.core.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputValidatorTest {

    @Test
    fun isValidEmail_validEmailFormat_returnsTrue() {
        val email = "volunteer@example.com"
        val result = InputValidator.isValidEmail(email)
        assertTrue(result)
    }

    @Test
    fun isValidEmail_invalidEmailFormat_returnsFalse() {
        val email = "invalid-email-string"
        val result = InputValidator.isValidEmail(email)
        assertFalse(result)
    }

    @Test
    fun isNotEmpty_nonEmptyString_returnsTrue() {
        val text = "Aegis Volunteer"
        val result = InputValidator.isNotEmpty(text)
        assertTrue(result)
    }

    @Test
    fun isNotEmpty_emptyString_returnsFalse() {
        val text = ""
        val result = InputValidator.isNotEmpty(text)
        assertFalse(result)
    }

    @Test
    fun isValidPassword_validPassword_returnsTrue() {
        val password = "Password123!"
        val result = InputValidator.isValidPassword(password)
        assertTrue(result)
    }

    @Test
    fun isValidPassword_shortPassword_returnsFalse() {
        val password = "123"
        val result = InputValidator.isValidPassword(password)
        assertFalse(result)
    }
}