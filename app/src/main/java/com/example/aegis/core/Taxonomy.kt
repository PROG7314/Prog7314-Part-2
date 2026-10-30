package com.example.aegis.core

import android.content.Context
import androidx.annotation.StringRes
import com.example.aegis.R

/**
 * Cause categories and skills. The English text is the value the API stores and validates, so it
 * must never be translated in data; only the label shown on screen is.
 */
object Taxonomy {

    private val labels: Map<String, Int> = mapOf(
        "Children & Youth" to R.string.cause_children_youth,
        "Elderly Care" to R.string.cause_elderly_care,
        "Soup Kitchens" to R.string.cause_soup_kitchens,
        "Animal Welfare" to R.string.cause_animal_welfare,
        "Homeless Shelters" to R.string.cause_homeless_shelters,
        "Tutoring & Reading" to R.string.cause_tutoring_reading,
        "Environment & Conservation" to R.string.cause_environment,
        "Health & Wellbeing" to R.string.cause_health,
        "Disaster Relief" to R.string.cause_disaster_relief,
        "Community Development" to R.string.cause_community,
        "Disability Support" to R.string.cause_disability,
        "Tutoring" to R.string.skill_tutoring,
        "Reading to Children" to R.string.skill_reading_children,
        "Manual Labour" to R.string.skill_manual_labour,
        "Cooking or Food Prep" to R.string.skill_cooking,
        "Admin & Organising" to R.string.skill_admin,
        "Fundraising" to R.string.skill_fundraising,
        "Marketing & Social Media" to R.string.skill_marketing,
        "Translation or Multilingual" to R.string.skill_translation,
        "First Aid" to R.string.skill_first_aid
    )

    /** The on-screen label for an API value; unknown values are shown as they came. */
    fun label(context: Context, apiValue: String): String =
        labels[apiValue]?.let(context::getString) ?: apiValue
}
