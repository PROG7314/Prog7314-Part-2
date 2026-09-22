package com.example.aegis

import android.app.Application
import com.example.aegis.data.ServiceLocator
import com.example.aegis.data.local.TokenStore
import com.example.aegis.data.remote.TokenProvider


class AegisApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(
            baseUrl = BuildConfig.API_BASE_URL,

                tokenProvider = object : TokenProvider {
                    override fun getToken(): String? = TokenStore.get(this@AegisApp)
                },

            debug = BuildConfig.DEBUG
        )
    }
}