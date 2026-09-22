package com.example.aegis

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.navOptions
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    // Which "home" the bottom nav's Home/Profile tabs return to - set whenever a home
    // destination is reached. Listings/Hours only make sense for volunteers.
    private var isOrgContext = false

    private val tabDestinations = setOf(
        R.id.mainFragment, R.id.commitmentsFragment, R.id.hoursFragment,
        R.id.orgHomeFragment, R.id.profileFragment
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigationView)

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
                R.id.nav_profile -> {
                    navController.navigate(R.id.profileFragment, null, tabNavOptions(home))
                    true
                }
                else -> {
                    Toast.makeText(this, "${item.title} is coming soon", Toast.LENGTH_SHORT).show()
                    false
                }
            }
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.mainFragment -> isOrgContext = false
                R.id.orgHomeFragment -> isOrgContext = true
            }

            bottomNav.visibility = if (destination.id in tabDestinations) View.VISIBLE else View.GONE
            // Listings/Hours are volunteer-only concepts - an organization already sees its
            // own listings on Home, and doesn't log volunteer hours.
            bottomNav.menu.findItem(R.id.nav_listings)?.isVisible = !isOrgContext
            bottomNav.menu.findItem(R.id.nav_hours)?.isVisible = !isOrgContext

            when (destination.id) {
                R.id.mainFragment, R.id.orgHomeFragment -> bottomNav.menu.findItem(R.id.nav_home)?.isChecked = true
                R.id.commitmentsFragment -> bottomNav.menu.findItem(R.id.nav_listings)?.isChecked = true
                R.id.hoursFragment -> bottomNav.menu.findItem(R.id.nav_hours)?.isChecked = true
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
}
