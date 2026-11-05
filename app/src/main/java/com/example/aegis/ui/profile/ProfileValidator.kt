package com.example.aegis.ui.profile

/** Client-side checks that mirror backend/src/validators.js, so errors show before a round trip. */
object ProfileValidator {
    const val NAME_MAX = 100
    const val BIO_MAX = 500
    const val DESCRIPTION_MAX = 1000

    enum class Problem { NAME_REQUIRED, NAME_TOO_LONG, ABOUT_TOO_LONG }

    fun aboutMax(isOrg: Boolean) = if (isOrg) DESCRIPTION_MAX else BIO_MAX

    fun validate(name: String, about: String, isOrg: Boolean): Problem? = when {
        name.isBlank() -> Problem.NAME_REQUIRED
        name.trim().length > NAME_MAX -> Problem.NAME_TOO_LONG
        about.trim().length > aboutMax(isOrg) -> Problem.ABOUT_TOO_LONG
        else -> null
    }
}