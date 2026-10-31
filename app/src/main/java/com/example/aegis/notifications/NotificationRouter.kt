package com.example.aegis.notifications

import android.content.Intent
import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.navigation.NavController
import androidx.navigation.navOptions
import com.example.aegis.R
import com.example.aegis.ui.main.OrgSignupsFragment

/**
 * Turns a tapped notification into "open the right screen".
 *
 * MainActivity hands it the launching intent (handle) and then calls deliver() whenever a home screen
 * is showing. A notification that arrives before sign-in has finished (cold start) simply waits
 * until the user reaches Home, so it never navigates over the Splash or Login screens.
 *
 * Push types come from the backend (src/notifications.js):
 *   new_signup       -> organization: that listing's sign-ups screen
 *   signup_completed -> volunteer: the Hours screen
 *   new_listing      -> volunteer: Home (already the landing screen)
 */
class NotificationRouter {

    private var pending: Bundle? = null

    /** Remember the notification that launched (or re-opened) the app, if there is one. */
    fun handle(intent: Intent?) {
        val extras = intent?.extras ?: return
        val type = extras.getString(KEY_TYPE) ?: return
        if (type !in KNOWN_TYPES) return
        pending = Bundle(extras)
        intent.removeExtra(KEY_TYPE) // so a rotation or a later onNewIntent can't replay it
    }

    /** Opens the target screen if one is waiting and a home screen is showing. */
    fun deliver(navController: NavController, isOrgContext: Boolean) {
        val extras = pending ?: return
        val current = navController.currentDestination?.id
        if (current != R.id.mainFragment && current != R.id.orgHomeFragment) return // not signed in yet: keep waiting
        pending = null

        when (extras.getString(KEY_TYPE)) {
            TYPE_NEW_SIGNUP -> if (isOrgContext) {
                val listingId = extras.getString(KEY_LISTING_ID) ?: return
                navController.navigate(
                    R.id.orgSignupsFragment,
                    bundleOf(
                        OrgSignupsFragment.ARG_LISTING_ID to listingId,
                        OrgSignupsFragment.ARG_LISTING_TITLE to extras.getString(KEY_LISTING_TITLE).orEmpty(),
                        OrgSignupsFragment.ARG_MIN_HOURS to (extras.getString(KEY_MIN_HOURS)?.toDoubleOrNull() ?: 0.0)
                    )
                )
            }
            TYPE_SIGNUP_COMPLETED -> if (!isOrgContext) {
                navController.navigate(
                    R.id.hoursFragment,
                    null,
                    navOptions {
                        launchSingleTop = true
                        popUpTo(R.id.mainFragment) { inclusive = false }
                    }
                )
            }
            else -> Unit // new_listing: Home is already showing
        }
    }

    private companion object {
        const val KEY_TYPE = "type"
        const val KEY_LISTING_ID = "listingId"
        const val KEY_LISTING_TITLE = "listingTitle"
        const val KEY_MIN_HOURS = "minHours"

        const val TYPE_NEW_SIGNUP = "new_signup"
        const val TYPE_SIGNUP_COMPLETED = "signup_completed"
        const val TYPE_NEW_LISTING = "new_listing"
        val KNOWN_TYPES = setOf(TYPE_NEW_SIGNUP, TYPE_SIGNUP_COMPLETED, TYPE_NEW_LISTING)
    }
}