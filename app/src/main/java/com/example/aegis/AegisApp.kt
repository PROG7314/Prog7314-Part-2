package com.example.aegis

import android.app.Application
import com.example.aegis.core.Appearance
import com.example.aegis.core.Strings
import com.example.aegis.data.ServiceLocator
import com.example.aegis.data.local.UserPreferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import com.example.aegis.data.local.TokenStore
import com.example.aegis.data.remote.TokenProvider
import com.example.aegis.notifications.AegisMessagingService


class AegisApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Strings.init(this)
        ServiceLocator.init(
            context = this,
            baseUrl = BuildConfig.API_BASE_URL,

            tokenProvider = object : TokenProvider {
                override fun getToken(): String? = TokenStore.get(this@AegisApp)
            },

            debug = BuildConfig.DEBUG
        )

        // Re-apply the saved theme and language before any screen draws, so choices survive a restart.
        val prefs = UserPreferencesDataStore(this)
        runBlocking {
            Appearance.applyDarkMode(prefs.chosenDarkModeFlow.first())
            prefs.chosenLanguageFlow.first()?.let { Appearance.applyLanguage(it) }
        }
        // Offline mode: watch the network and sync queued changes when it comes back.
        ServiceLocator.startOffline()
        // Notification channels must exist before the first push arrives.
        AegisMessagingService.createChannel(this)
    }
}