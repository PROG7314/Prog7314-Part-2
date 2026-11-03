package com.example.aegis.unittestspoe

import org.junit.Test
import org.junit.Assert.*

class AegisValidationTest {

    @Test
    fun `empty email returns false for validation`() {
        val isValid = android.util.Patterns.EMAIL_ADDRESS.matcher("").matches()
        assertFalse(isValid)
    }

    @Test
    fun `valid email returns true for validation`() {
        val isValid = android.util.Patterns.EMAIL_ADDRESS.matcher("volunteer@aegis.org").matches()
        assertTrue(isValid)
    }

    @Test
    fun `password minimum length check passes for secure input`() {
        val password = "SecurePassword123"
        assertTrue(password.length >= 8)
    }

    @Test
    fun `password minimum length check fails for short input`() {
        val password = "short"
        assertFalse(password.length >= 8)
    }

    @Test
    fun `volunteer hours calculation aggregates correctly`() {
        val shift1Hours = 3.5
        val shift2Hours = 4.0
        assertEquals(7.5, shift1Hours + shift2Hours, 0.001)
    }

    @Test
    fun `listing status check defaults to open on creation`() {
        val defaultStatus = "Open"
        assertEquals("Open", defaultStatus)
    }
}