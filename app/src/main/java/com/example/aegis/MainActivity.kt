package com.example.aegis

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.navOptions
import com.google.android.material.bottomnavigation.BottomNavigationView

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

    private val tabDestinations = setOf(
        R.id.mainFragment, R.id.commitmentsFragment, R.id.hoursFragment,
        R.id.orgHomeFragment, R.id.profileFragment, R.id.messagesFragment
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigationView)
        val navHost = findViewById<View>(R.id.nav_host_fragment)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { _, insets ->
            statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            applyTopInset(navHost, navController.currentDestination?.id)
            insets
        }

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
        }
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
