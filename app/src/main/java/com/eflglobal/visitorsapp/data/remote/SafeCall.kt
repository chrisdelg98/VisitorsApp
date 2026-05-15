package com.eflglobal.visitorsapp.data.remote

import com.eflglobal.visitorsapp.data.remote.dto.ApiResponse
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import retrofit2.HttpException
import java.io.IOException

/**
 * Single funnel for every API call.
 *
 *  - Successful envelope (`success == true`) → returns `data` (non-null).
 *  - Envelope with `success == false` → throws [ApiException] with the
 *    backend's tagged `code`.
 *  - HTTP error (4xx / 5xx) → parses the envelope body if present, otherwise
 *    synthesises a code from the HTTP status.
 *  - [IOException] (no network, timeout, DNS, TLS) → throws
 *    [ApiException] with [ApiErrorCode.NETWORK_UNAVAILABLE].
 *
 * The caller can therefore wrap the call in `runCatching` and pattern-match
 * on `ApiException.code` without inspecting Retrofit / OkHttp types.
 */
suspend fun <T : Any> safeCall(
    block: suspend () -> ApiResponse<T>
): T {
    val envelope: ApiResponse<T> = try {
        block()
    } catch (e: HttpException) {
        throw e.toApiException()
    } catch (e: IOException) {
        throw ApiException(
            code = ApiErrorCode.NETWORK_UNAVAILABLE,
            message = "No connection to the server.",
            cause = e
        )
    } catch (e: ApiException) {
        throw e
    } catch (e: Exception) {
        throw ApiException(
            code = ApiErrorCode.UNKNOWN,
            message = e.message ?: "Unexpected error",
            cause = e
        )
    }

    if (envelope.success) {
        return envelope.data
            ?: throw ApiException(
                code = ApiErrorCode.UNKNOWN,
                message = "API returned success without data."
            )
    }

    throw ApiException(
        code = envelope.code ?: ApiErrorCode.UNKNOWN,
        message = envelope.message ?: "Request failed.",
        fieldErrors = envelope.errors
    )
}

/**
 * Maps a Retrofit [HttpException] (the body is an unsuccessful envelope
 * surfaced as an HTTP 4xx/5xx) into our [ApiException]. Tries to parse the
 * error body as `ApiResponse<Unit>` so we preserve the backend's tagged
 * `code`; falls back to status-based synthetic codes.
 */
private fun HttpException.toApiException(): ApiException {
    val status = code()
    val raw = runCatching { response()?.errorBody()?.string() }.getOrNull()
    val parsed = raw?.let { runCatching { errorEnvelopeAdapter.fromJson(it) }.getOrNull() }

    val code = parsed?.code
        ?: when (status) {
            401 -> ApiErrorCode.API_KEY_INVALID
            422 -> ApiErrorCode.VALIDATION_ERROR
            429 -> ApiErrorCode.RATE_LIMIT_EXCEEDED
            in 500..599 -> ApiErrorCode.UNKNOWN
            else -> ApiErrorCode.UNKNOWN
        }

    val msg = parsed?.message
        ?: when (status) {
            401 -> "Unauthorized."
            403 -> "Forbidden."
            404 -> "Not found."
            422 -> "Validation failed."
            429 -> "Too many requests."
            in 500..599 -> "Server error ($status)."
            else -> "Request failed ($status)."
        }

    return ApiException(
        code = code,
        message = msg,
        httpStatus = status,
        fieldErrors = parsed?.errors
    )
}

// Shared adapter for parsing error-body envelopes. `Unit` because we don't
// care about `data` on the failure path.
private val errorEnvelopeAdapter: JsonAdapter<ApiResponse<Unit>> by lazy {
    val moshi = Moshi.Builder().build()
    val type = Types.newParameterizedType(ApiResponse::class.java, Unit::class.javaObjectType)
    moshi.adapter<ApiResponse<Unit>>(type)
}

