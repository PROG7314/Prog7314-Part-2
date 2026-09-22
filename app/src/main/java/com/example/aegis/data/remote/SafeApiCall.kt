package com.example.aegis.data.remote

import android.util.Log
import com.example.aegis.data.remote.dto.ErrorEnvelopeDto
import com.example.aegis.domain.model.ApiError
import com.example.aegis.domain.model.ApiResult
import com.google.gson.Gson
import com.google.gson.JsonParseException
import retrofit2.HttpException
import java.io.IOException

private val gson = Gson()

/**
 * Wraps a suspend API call so ViewModels never see exceptions - only ApiResult.Success / Failure.
 * Turns the spec's error shape { error: { code, message, field? } } into an ApiError.
 */
suspend fun <T> safeApiCall(tag: String, call: suspend () -> T): ApiResult<T> =
    try {
        val data = call()
        Log.d(tag, "API call succeeded")
        ApiResult.Success(data)
    } catch (e: HttpException) {
        val error = e.toApiError()
        Log.w(tag, "HTTP ${e.code()} [${error.code}]: ${error.message}")
        ApiResult.Failure(error)
    } catch (e: IOException) {
        // Covers no connectivity, DNS failure and timeouts.
        Log.w(tag, "Network error: ${e.message}")
        ApiResult.Failure(ApiError(ApiError.Kind.NETWORK, defaultMessage(ApiError.Kind.NETWORK)))
    } catch (e: JsonParseException) {
        Log.e(tag, "Unexpected response shape: ${e.message}")
        ApiResult.Failure(ApiError(ApiError.Kind.UNKNOWN, "The server sent a response we couldn't read."))
    }

private fun HttpException.toApiError(): ApiError {
    val httpCode = code()
    val kind = when (httpCode) {
        401 -> ApiError.Kind.UNAUTHORIZED
        403 -> ApiError.Kind.FORBIDDEN
        404 -> ApiError.Kind.NOT_FOUND
        409 -> ApiError.Kind.CONFLICT
        400, 422 -> ApiError.Kind.VALIDATION
        in 500..599 -> ApiError.Kind.SERVER
        else -> ApiError.Kind.UNKNOWN
    }

    val rawBody = try {
        response()?.errorBody()?.string()
    } catch (e: IOException) {
        null
    }
    // Error bodies can be non-JSON (e.g. a host's HTML 502 page), so parsing must never throw.
    val parsed = try {
        gson.fromJson(rawBody, ErrorEnvelopeDto::class.java)?.error
    } catch (e: JsonParseException) {
        null
    }

    return ApiError(
        kind = kind,
        message = parsed?.message?.takeIf { it.isNotBlank() } ?: defaultMessage(kind),
        httpCode = httpCode,
        code = parsed?.code,
        field = parsed?.field
    )
}

private fun defaultMessage(kind: ApiError.Kind): String = when (kind) {
    ApiError.Kind.NETWORK -> "Can't reach the server. Check your connection and try again."
    ApiError.Kind.UNAUTHORIZED -> "Your session has expired. Please log in again."
    ApiError.Kind.FORBIDDEN -> "You don't have permission to do that."
    ApiError.Kind.NOT_FOUND -> "We couldn't find that."
    ApiError.Kind.CONFLICT -> "That listing is full, or you're already signed up."
    ApiError.Kind.VALIDATION -> "Some of the details aren't valid. Please check and try again."
    ApiError.Kind.SERVER -> "The server had a problem. Please try again shortly."
    ApiError.Kind.UNKNOWN -> "Something went wrong. Please try again."
}