package com.example.aegis.data.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

/** Turns a photo the user picked into a public https URL the API will accept (it only takes https). */
interface ImageUploader {
    /** Throws if the photo can't be read or the upload fails. */
    suspend fun upload(uri: Uri): String
}

/**
 * Cloudinary settings. Both values are safe to keep in the app: an UNSIGNED upload preset can only
 * upload into one folder and has no delete rights. Never put the Cloudinary API key or secret here.
 */
object CloudinaryConfig {
    const val CLOUD_NAME = "dsmkcaof"
    const val UPLOAD_PRESET = "aegis_unsigned"
}

/**
 * Uploads to Cloudinary's free tier (Firebase Storage now needs a paid plan). Photos are shrunk first
 * (longest side 1600 px, JPEG 85%) and turned upright; the reply's secure_url is an https link.
 */
class CloudinaryImageUploader(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
) : ImageUploader {

    override suspend fun upload(uri: Uri): String = withContext(Dispatchers.IO) {
        val bytes = compressToJpeg(uri)
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("upload_preset", CloudinaryConfig.UPLOAD_PRESET)
            .addFormDataPart("file", "listing.jpg", bytes.toRequestBody("image/jpeg".toMediaType()))
            .build()
        val request = Request.Builder()
            .url("https://api.cloudinary.com/v1_1/${CloudinaryConfig.CLOUD_NAME}/image/upload")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("Upload failed (${response.code}): $text")
            JSONObject(text).getString("secure_url")
        }
    }

    private fun compressToJpeg(uri: Uri): ByteArray {
        val resolver = context.contentResolver

        // 1. Read just the size, to pick a decode scale that doesn't load a 12 MP photo into memory.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // decodeStream returns null when it only reads the size, so only check that the file opened.
        val opened = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds); true }
            ?: false
        if (!opened || bounds.outWidth <= 0 || bounds.outHeight <= 0) error("Couldn't open the photo")
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE * 2) sample *= 2

        // 2. Decode, then shrink to the final size and turn it upright.
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("Couldn't read the photo")
        val rotation = resolver.openInputStream(uri)?.use { exifRotation(it) } ?: 0

        val scale = min(1f, MAX_SIDE.toFloat() / max(decoded.width, decoded.height))
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postRotate(rotation.toFloat())
        }
        val result = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)

        val bytes = ByteArrayOutputStream().use { out ->
            result.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            out.toByteArray()
        }
        if (result !== decoded) decoded.recycle()
        result.recycle()
        return bytes
    }

    private fun exifRotation(stream: InputStream): Int =
        try {
            when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (e: Exception) {
            0 // no EXIF (a screenshot, say): leave the photo as it is
        }

    private companion object {
        const val MAX_SIDE = 1600
        const val JPEG_QUALITY = 85
    }
}