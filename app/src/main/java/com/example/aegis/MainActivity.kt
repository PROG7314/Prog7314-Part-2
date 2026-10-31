package com.example.aegis

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.navOptions
import com.example.aegis.data.local.TokenStore
import com.example.aegis.notifications.NotificationRouter
import com.example.aegis.notifications.NotificationTokenSync
import com.example.aegis.ui.offline.OfflineBanner
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    // Which "home" the bottom nav's Home/Profile tabs return to - set whenever a home
    // destination is reached. Listings/Hours only make sense for volunteers.
    private var isOrgContext = false

    // Android 15+ draws apps edge-to-edge, so screens would sit underneath the status bar.
    // Tab screens are padded down by its height; full-bleed screens (splash, login, the green
    // profile header) and the chat (which handles its own insets) are left alone.
    private var statusBarHeight = 0
    private val fullBleedDestinations = setOf(
        R.id.splashFragment, R.id.loginFragment, R.id.roleSelectionFragment,
        R.id.profileFragment, R.id.chatFragment
    )
    private lateinit var navController: NavController

    // Opens the right screen when a push notification is tapped.
    private val notificationRouter = NotificationRouter()

    // The session we last registered a push token for, so an account switch registers the new account too.
    private var lastSyncedSession: String? = null

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* granted or not, nothing else to do */ }

    private val tabDestinations = setOf(
        R.id.mainFragment, R.id.commitmentsFragment, R.id.hoursFragment,
        R.id.orgHomeFragment, R.id.profileFragment, R.id.messagesFragment
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigationView)
        val navHost = findViewById<View>(R.id.nav_host_fragment)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { _, insets ->
            statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            applyTopInset(navHost, navController.currentDestination?.id)
            insets
        }

        // Launched by tapping a notification? Only look on a fresh start, not after a rotation.
        if (savedInstanceState == null) notificationRouter.handle(intent)

        bottomNav.setOnItemSelectedListener { item ->
            val home = if (isOrgContext) R.id.orgHomeFragment else R.id.mainFragment
            when (item.itemId) {
                R.id.nav_home -> {
                    navController.navigate(home, null, tabNavOptions(home))
                    true
                }
                R.id.nav_listings -> {
                    navController.navigate(R.id.commitmentsFragment, null, tabNavOptions(home))
                    true
                }
                R.id.nav_hours -> {
                    navController.navigate(R.id.hoursFragment, null, tabNavOptions(home))
                    true
                }
                R.id.nav_messages -> {
                    navController.navigate(R.id.messagesFragment, null, tabNavOptions(home))
                    true
                }
                R.id.nav_profile -> {
                    navController.navigate(R.id.profileFragment, null, tabNavOptions(home))
                    true
                }
                else -> {
                    Toast.makeText(this, getString(R.string.coming_soon, item.title), Toast.LENGTH_SHORT).show()
                    false
                }
            }
        }


        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.mainFragment -> isOrgContext = false
                R.id.orgHomeFragment -> isOrgContext = true
            }

            applyTopInset(navHost, destination.id)
            bottomNav.visibility = if (destination.id in tabDestinations) View.VISIBLE else View.GONE
            // Listings/Hours are volunteer-only concepts - an organization already sees its
            // own listings on Home, and doesn't log volunteer hours.
            bottomNav.menu.findItem(R.id.nav_listings)?.isVisible = !isOrgContext
            bottomNav.menu.findItem(R.id.nav_hours)?.isVisible = !isOrgContext

            when (destination.id) {
                R.id.mainFragment, R.id.orgHomeFragment -> bottomNav.menu.findItem(R.id.nav_home)?.isChecked = true
                R.id.commitmentsFragment -> bottomNav.menu.findItem(R.id.nav_listings)?.isChecked = true
                R.id.hoursFragment -> bottomNav.menu.findItem(R.id.nav_hours)?.isChecked = true
                R.id.messagesFragment -> bottomNav.menu.findItem(R.id.nav_messages)?.isChecked = true
                R.id.profileFragment -> bottomNav.menu.findItem(R.id.nav_profile)?.isChecked = true
            }

            // A home screen means the user is signed in: register for pushes and open any waiting notification.
            // Posted, because navigating from inside this listener would re-enter the NavController.
            if (destination.id == R.id.mainFragment || destination.id == R.id.orgHomeFragment) {
                bottomNav.post { onHomeReached() }
            }
        }

        OfflineBanner.bind(this, findViewById(R.id.offlineBanner))
    }

    /** The app was already open and the user tapped a notification (launchMode="singleTop"). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        notificationRouter.handle(intent)
        notificationRouter.deliver(navController, isOrgContext)
    }

    private fun onHomeReached() {
        if (isFinishing || isDestroyed) return
        syncPushToken()
        askForNotificationPermissionOnce()
        notificationRouter.deliver(navController, isOrgContext)
    }

    /** Registers this phone for pushes whenever a different session (login) is seen. */
    private fun syncPushToken() {
        val session = TokenStore.get(this) ?: return
        if (session == lastSyncedSession) return
        lastSyncedSession = session
        lifecycleScope.launch { NotificationTokenSync.sync() }
    }

    /** Android 13+: explain first, then ask - once. Older versions need no permission. */
    private fun askForNotificationPermissionOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        val prefs = getSharedPreferences("aegis_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("notification_prompt_shown", false)) return
        prefs.edit().putBoolean("notification_prompt_shown", true).apply()

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.notif_permission_title)
            .setMessage(R.string.notif_permission_message)
            .setPositiveButton(R.string.notif_permission_allow) { _, _ ->
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            .setNegativeButton(R.string.notif_permission_not_now, null)
            .show()
    }

    // Keeps the current role's home as the base of the tab back stack: reselecting a tab
    // won't stack duplicates, and other tabs sit on top of Home rather than replacing it.
    private fun tabNavOptions(home: Int) = navOptions {
        launchSingleTop = true
        popUpTo(home) { inclusive = false }
    }

    private fun applyTopInset(navHost: View, destinationId: Int?) {
        val top = if (destinationId != null && destinationId !in fullBleedDestinations) statusBarHeight else 0
        navHost.setPadding(0, top, 0, 0)
    }
}
