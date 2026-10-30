package com.example.aegis.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID

/**
 * Turns a picked photo into a URL the API can store (the API only keeps URLs).
 * Signed-in users upload to Firebase Storage. Debug builds with no Firebase user (the demo login)
 * upload to the local demo server instead.
 */
object ImageUploader {
    private const val MAX_SIDE = 1280
    private const val JPEG_QUALITY = 85

    suspend fun upload(context: Context, uri: Uri, folder: String): String = withContext(Dispatchers.IO) {
        val bytes = compress(context, uri)
        val user = FirebaseAuth.getInstance().currentUser
        when {
            user != null -> {
                val ref = FirebaseStorage.getInstance().reference.child("$folder/${user.uid}/${UUID.randomUUID()}.jpg")
                ref.putBytes(bytes).await()
                ref.downloadUrl.await().toString()
            }
            else -> error("Not signed in")
        }
    }

    // Shrinks big camera photos so uploads stay quick and small.
    private fun compress(context: Context, uri: Uri): ByteArray {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > MAX_SIDE * 2 || bounds.outHeight / sample > MAX_SIDE * 2) sample *= 2
        val bitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("Could not read the photo")
        val scale = MAX_SIDE.toFloat() / maxOf(bitmap.width, bitmap.height)
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        } else bitmap
        return ByteArrayOutputStream().also { scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }.toByteArray()
    }
}
