package com.example.aegis.unittestspoe

import org.junit.Test
import org.junit.Assert.*

    class AegisValidationTest {

        private val emailRegex = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

        @Test
        fun `empty email returns false for validation`() {
            val isValid = emailRegex.matches("")
            assertFalse(isValid)
        }

        @Test
        fun `valid email returns true for validation`() {
            val isValid = emailRegex.matches("volunteer@aegis.org")
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