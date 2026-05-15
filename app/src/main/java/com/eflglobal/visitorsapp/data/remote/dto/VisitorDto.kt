package com.eflglobal.visitorsapp.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Remote representation of a visitor (returned by the backend). */
@JsonClass(generateAdapter = true)
data class VisitorDto(
    @Json(name = "id") val id: String,
    @Json(name = "first_name") val firstName: String,
    @Json(name = "last_name") val lastName: String,
    @Json(name = "document_type") val documentType: String,
    @Json(name = "document_number") val documentNumber: String?,
    @Json(name = "email") val email: String?,
    @Json(name = "phone") val phone: String?,
    @Json(name = "company") val company: String?
)

/**
 * Body for `POST /v1/visitors` and `PUT /v1/visitors/{id}`.
 *
 * `station_id` is intentionally absent — the backend infers it from the
 * `X-API-Key` header via its `api.key` middleware.
 */
@JsonClass(generateAdapter = true)
data class CreateVisitorBody(
    @Json(name = "first_name") val firstName: String,
    @Json(name = "last_name") val lastName: String,
    @Json(name = "document_type") val documentType: String,        // DUI | PASSPORT | LICENSE | OTHER
    @Json(name = "document_number") val documentNumber: String? = null,
    @Json(name = "email") val email: String? = null,
    @Json(name = "phone") val phone: String? = null,
    @Json(name = "company") val company: String? = null
)

