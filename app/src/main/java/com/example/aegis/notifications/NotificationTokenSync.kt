package com.example.aegis.notifications

import android.util.Log
import com.example.aegis.data.ServiceLocator
import com.example.aegis.data.remote.dto.FcmTokenRequest
import com.example.aegis.data.remote.safeApiCall
import com.example.aegis.domain.model.ApiResult
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

/**
 * Tells the API which phone belongs to the signed-in account (POST /users/me/fcm-token), so the
 * backend knows where to send pushes. Best effort: a failure is logged, never shown to the user.
 */
object NotificationTokenSync {
    private const val TAG = "FcmTokenSync"

    /** Fetches this phone's current FCM token and registers it. Call once someone is signed in. */
    suspend fun sync() {
        val token = try {
            FirebaseMessaging.getInstance().token.await()
        } catch (e: Exception) {
            Log.w(TAG, "No FCM token yet: ${e.message}")
            return
        }
        send(token)
    }

    suspend fun send(token: String) {
        when (val result = safeApiCall(TAG) { ServiceLocator.api().registerFcmToken(FcmTokenRequest(token)) }) {
            is ApiResult.Success -> Log.d(TAG, "FCM token registered with the API")
            is ApiResult.Failure -> Log.w(TAG, "FCM token not registered: ${result.error.message}")
        }
    }
}