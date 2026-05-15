package com.eflglobal.visitorsapp.data.remote.dto

import com.squareup.moshi.JsonClass

/**
 * Standard JSON envelope used by every Visitors API endpoint.
 *
 * Successful responses → `success = true` and `data` populated.
 * Failures → `success = false`, `message` filled with a human-readable text
 * and `code` set to one of the backend's tagged error codes (see
 * `docs/ANDROID_INTEGRATION.md`). Validation errors (HTTP 422) come without
 * `code` but with a populated `errors` map.
 */
@JsonClass(generateAdapter = true)
data class ApiResponse<T>(
    val success: Boolean = false,
    val data: T? = null,
    val message: String? = null,
    val code: String? = null,
    val errors: Map<String, List<String>>? = null
)

