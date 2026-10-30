package com.example.aegis.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.aegis.MainActivity
import com.example.aegis.R
import com.example.aegis.data.local.TokenStore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receives Firebase Cloud Messaging pushes.
 *
 * - App in the FOREGROUND: onMessageReceived runs and we build the notification ourselves
 *   (own icon, own channel, a PendingIntent that opens the right screen).
 * - App in the BACKGROUND / closed: Android shows the notification itself (using the default icon and
 *   channel set in the manifest). Tapping it opens MainActivity with the push's data as intent extras,
 *   and MainActivity routes it exactly the same way (see NotificationRouter).
 */
class AegisMessagingService : FirebaseMessagingService() {

    /** Firebase rotated the device token: tell the API, but only if someone is signed in. */
    override fun onNewToken(token: String) {
        if (TokenStore.get(applicationContext) == null) return
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { NotificationTokenSync.send(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"] ?: return
        val body = message.notification?.body ?: message.data["body"].orEmpty()
        show(applicationContext, title, body, message.data, message.messageId)
    }

    companion object {
        /** Create once on app start (AegisApp). Safe to call repeatedly. The id must match the backend's CHANNEL_ID. */
        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel = NotificationChannel(
                context.getString(R.string.notification_channel_id),
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = context.getString(R.string.notification_channel_description) }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        @SuppressLint("MissingPermission") // the permission is checked right at the top
        private fun show(context: Context, title: String, body: String, data: Map<String, String>, messageId: String?) {
            // Android 13+ needs the runtime permission; without it (or with notifications switched off) do nothing.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

            // The push's data rides along as extras, so MainActivity knows which screen to open.
            val open = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                data.forEach { (key, value) -> putExtra(key, value) }
            }
            // A different request code per message, so two pushes don't overwrite each other's extras.
            val requestCode = (messageId ?: System.nanoTime().toString()).hashCode()
            val pending = PendingIntent.getActivity(
                context, requestCode, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val notification = NotificationCompat.Builder(context, context.getString(R.string.notification_channel_id))
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pending)
                .build()
            NotificationManagerCompat.from(context).notify(requestCode, notification)
        }
    }
}