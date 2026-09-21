//package com.example.aegis
//
//import android.app.Application
//import com.google.firebase.BuildConfig
//import com.google.firebase.database.core.TokenProvider
//
//class AegisApp : Application() {
//    override fun onCreate() {
//        super.onCreate()
//        ServiceLocator.init(
//            baseUrl = BuildConfig.API_BASE_URL,
//            tokenProvider = object : TokenProvider {
//                // Todo Member 2:
//                //  return the JWT from the EncryptedSharedPreferences store (Table 14: sessionToken).
//                override fun getToken(): String? = null
//            },
//            debug = BuildConfig.DEBUG
//        )
//    }
//}