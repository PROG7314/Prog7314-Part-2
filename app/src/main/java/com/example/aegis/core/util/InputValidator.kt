package com.example.aegis.core.util

object InputValidator {

    // Standard Kotlin Regex that works in JVM Unit Tests and on Android devices
    private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$")

    fun isValidEmail(email: String): Boolean {
        return email.isNotEmpty() && EMAIL_REGEX.matches(email)
    }

    fun isNotEmpty(input: String): Boolean {
        return input.trim().isNotEmpty()
    }

    fun isValidPassword(password: String): Boolean {
        return password.length >= 8
    }
}