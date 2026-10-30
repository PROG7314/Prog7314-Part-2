package com.example.aegis

import android.app.Application
import com.example.aegis.data.ServiceLocator
import com.example.aegis.data.local.TokenStore
import com.example.aegis.data.remote.TokenProvider
import com.example.aegis.notifications.AegisMessagingService


class AegisApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(
            context = this,
            baseUrl = BuildConfig.API_BASE_URL,

            tokenProvider = object : TokenProvider {
                override fun getToken(): String? = TokenStore.get(this@AegisApp)
            },

            debug = BuildConfig.DEBUG
        )
        // Offline mode: watch the network and sync queued changes when it comes back.
        ServiceLocator.startOffline()
        // Notification channels must exist before the first push arrives.
        AegisMessagingService.createChannel(this)
    }
}