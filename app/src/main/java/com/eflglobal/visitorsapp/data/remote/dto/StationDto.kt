package com.eflglobal.visitorsapp.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Body sent to `POST /v1/auth/station`. */
@JsonClass(generateAdapter = true)
data class ValidateStationBody(
    val pin: String,
    @Json(name = "device_imei") val deviceImei: String? = null,
    @Json(name = "device_android_id") val deviceAndroidId: String? = null,
    @Json(name = "device_model") val deviceModel: String? = null,
    @Json(name = "device_ip") val deviceIp: String? = null
)

/**
 * `data` envelope returned by `POST /v1/auth/station`.
 *
 * The `api_key` MUST be stored in [com.eflglobal.visitorsapp.data.remote.SecureStore]
 * immediately and is never sent again in a body — only as the `X-API-Key`
 * header (injected by the auth interceptor).
 */
@JsonClass(generateAdapter = true)
data class StationDto(
    @Json(name = "api_key") val apiKey: String,
    val station: StationInfoDto
)

@JsonClass(generateAdapter = true)
data class StationInfoDto(
    val id: String,
    val code: String,
    val name: String,
    val location: String? = null
)

