package com.example.aegis.core.util

object InputValidator {

    fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    fun isNotEmpty(input: String): Boolean {
        return input.trim().isNotEmpty()
    }

    fun isValidPassword(password: String): Boolean {
        return password.length >= 8
    }
}