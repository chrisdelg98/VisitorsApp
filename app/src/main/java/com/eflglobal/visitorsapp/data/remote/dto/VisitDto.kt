package com.eflglobal.visitorsapp.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Single image attached to a visit as returned by `GET /v1/visits/{id}`.
 *
 * `url` is an authenticated endpoint (not a signed/public URL). To download
 * the bytes, hit it with the standard OkHttp client — the `X-API-Key`
 * interceptor adds the credentials automatically. Cross-station reads are
 * allowed (same tenant); writes are not.
 */
@JsonClass(generateAdapter = true)
data class VisitImageDto(
    @Json(name = "type") val type: String,   // personal_photo | doc_front | doc_back
    @Json(name = "url") val url: String
)

/**
 * Optional nested station block returned by `GET /v1/visits/{id}`, used to
 * tell the operator where a cross-station re-entry visit originated from.
 */
@JsonClass(generateAdapter = true)
data class VisitStationRefDto(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "code") val code: String? = null
)

/** Remote representation of a single visit. */
@JsonClass(generateAdapter = true)
data class VisitDto(
    @Json(name = "id") val id: String,
    @Json(name = "visitor_id") val visitorId: String,
    @Json(name = "visitor") val visitor: VisitorDto? = null,
    @Json(name = "visitor_type") val visitorType: String,
    @Json(name = "visit_reason") val visitReason: String,
    @Json(name = "visit_reason_custom") val visitReasonCustom: String? = null,
    @Json(name = "visiting_person") val visitingPerson: String,
    @Json(name = "notes") val notes: String? = null,
    @Json(name = "status") val status: String,             // ACTIVE | COMPLETED
    @Json(name = "check_in") val checkIn: String,          // ISO-8601 with offset
    @Json(name = "check_out") val checkOut: String? = null,
    @Json(name = "original_visit_id") val originalVisitId: String? = null,
    @Json(name = "reentry_from_station_id") val reentryFromStationId: String? = null,
    @Json(name = "station") val station: VisitStationRefDto? = null,
    @Json(name = "images") val images: List<VisitImageDto>? = null
)

/**
 * Body for `POST /v1/visits`.
 *
 * Notes:
 *  - `station_id` is **never** sent — the backend infers it from `X-API-Key`.
 *  - `qr_code` is generated client-side from the visit UUID and printed on the
 *    badge; the backend neither stores nor returns it (per integration contract).
 *  - [originalVisitId] / [reentryFromStationId] are populated only when this
 *    POST represents a cross-station re-entry (Phase 6).
 */
@JsonClass(generateAdapter = true)
data class CreateVisitBody(
    @Json(name = "visitor_id") val visitorId: String,
    @Json(name = "visitor_type") val visitorType: String,
    @Json(name = "visit_reason") val visitReason: String,
    @Json(name = "visit_reason_custom") val visitReasonCustom: String? = null,
    @Json(name = "visiting_person") val visitingPerson: String,
    @Json(name = "notes") val notes: String? = null,
    @Json(name = "original_visit_id") val originalVisitId: String? = null,
    @Json(name = "reentry_from_station_id") val reentryFromStationId: String? = null
)

