package com.example.aegis

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.navOptions
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    // Profile is still being built by another team member.
    private val tabDestinations = setOf(R.id.mainFragment, R.id.commitmentsFragment, R.id.hoursFragment)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigationView)

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    navController.navigate(R.id.mainFragment, null, tabNavOptions())
                    true
                }
                R.id.nav_listings -> {
                    navController.navigate(R.id.commitmentsFragment, null, tabNavOptions())
                    true
                }
                R.id.nav_hours -> {
                    navController.navigate(R.id.hoursFragment, null, tabNavOptions())
                    true
                }
                else -> {
                    Toast.makeText(this, "${item.title} is coming soon", Toast.LENGTH_SHORT).show()
                    false
                }
            }
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            bottomNav.visibility = if (destination.id in tabDestinations) View.VISIBLE else View.GONE
            when (destination.id) {
                R.id.mainFragment -> bottomNav.menu.findItem(R.id.nav_home)?.isChecked = true
                R.id.commitmentsFragment -> bottomNav.menu.findItem(R.id.nav_listings)?.isChecked = true
                R.id.hoursFragment -> bottomNav.menu.findItem(R.id.nav_hours)?.isChecked = true
            }
        }
    }

    // Keeps mainFragment as the base of the tab back stack: reselecting a tab won't stack
    // duplicates, and switching to Hours sits on top of Home rather than replacing it.
    private fun tabNavOptions() = navOptions {
        launchSingleTop = true
        popUpTo(R.id.mainFragment) { inclusive = false }
    }
}
