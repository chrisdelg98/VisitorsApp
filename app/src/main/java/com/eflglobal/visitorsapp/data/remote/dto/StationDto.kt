package com.eflglobal.visitorsapp.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Body sent to `POST /v1/auth/validate-station`. */
@JsonClass(generateAdapter = true)
data class ValidateStationBody(
    val code: String
)

/**
 * Station info returned by `POST /v1/auth/validate-station`.
 *
 * The `api_key` MUST be stored in [com.eflglobal.visitorsapp.data.remote.SecureStore]
 * immediately and is never sent again in a body — only as the `X-API-Key`
 * header (injected by the auth interceptor).
 */
@JsonClass(generateAdapter = true)
data class StationDto(
    @Json(name = "station_id") val stationId: String,
    @Json(name = "name") val name: String,
    @Json(name = "code") val code: String,
    @Json(name = "api_key") val apiKey: String
)

