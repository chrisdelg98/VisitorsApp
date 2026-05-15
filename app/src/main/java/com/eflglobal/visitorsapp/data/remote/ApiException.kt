package com.eflglobal.visitorsapp.data.remote

/**
 * Single exception type surfaced by [safeCall].
 *
 * Carries the backend's tagged error [code] (see [ApiErrorCode]) plus the
 * HTTP status when available, so upper layers can react with `when (code)`
 * branches without having to parse strings.
 *
 * For HTTP 422 the [fieldErrors] map mirrors Laravel's `errors{}` block:
 *   `{"email": ["The email must be a valid email."], ...}`.
 */
class ApiException(
    val code: String,
    message: String,
    val httpStatus: Int? = null,
    val fieldErrors: Map<String, List<String>>? = null,
    cause: Throwable? = null
) : RuntimeException(message, cause) {

    /** Convenience: pulls the first field error message, or `null` if none. */
    fun firstFieldError(): String? = fieldErrors?.values?.firstOrNull()?.firstOrNull()

    /** True if the failure is transient and worth retrying (network / 5xx / 429). */
    val isTransient: Boolean
        get() = when (code) {
            ApiErrorCode.NETWORK_UNAVAILABLE,
            ApiErrorCode.RATE_LIMIT_EXCEEDED -> true
            ApiErrorCode.UNKNOWN -> (httpStatus ?: 0) in 500..599
            else -> false
        }

    /** True if the API key was rejected — caller should wipe SecureStore. */
    val isAuthFailure: Boolean
        get() = code == ApiErrorCode.API_KEY_MISSING || code == ApiErrorCode.API_KEY_INVALID
}

