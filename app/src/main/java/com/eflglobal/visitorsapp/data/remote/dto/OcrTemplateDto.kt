package com.eflglobal.visitorsapp.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Top-level body of `GET /api/v1/ocr/templates`.
 *
 * This endpoint does NOT use the standard [ApiResponse] envelope — it carries
 * two extra top-level fields ([catalogVersion] and [count]) alongside `data`.
 * The catalog is a COMPLETE snapshot (not deltas): on HTTP 200 the local copy
 * must be fully replaced with [data] and [catalogVersion] stored as the new
 * ETag. A 304 (handled at the Retrofit `Response` level) means "no change".
 */
@JsonClass(generateAdapter = true)
data class OcrTemplatesResponse(
    val success: Boolean = false,
    val data: List<OcrTemplateDto> = emptyList(),
    @Json(name = "catalog_version") val catalogVersion: String? = null,
    val count: Int = 0
)

@JsonClass(generateAdapter = true)
data class OcrTemplateDto(
    val id: String,
    @Json(name = "document_type_id") val documentTypeId: String? = null,
    @Json(name = "country_id") val countryId: String? = null,
    val code: String? = null,
    val name: String? = null,
    @Json(name = "document_kind") val documentKind: String? = null,
    val subdivision: String? = null,
    val version: Int = 1,
    /** "front" | "back". */
    val side: String = "front",
    /** "anchor" | "zone" | "mrz" | "pdf417". */
    @Json(name = "extraction_method") val extractionMethod: String = "anchor",
    val signature: SignatureDto? = null,
    val fields: List<TemplateFieldDto> = emptyList(),
    @Json(name = "published_at") val publishedAt: String? = null,
    @Json(name = "updated_at") val updatedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class SignatureDto(
    val keywords: List<String> = emptyList(),
    @Json(name = "id_regex") val idRegex: String? = null,
    @Json(name = "aspect_ratio") val aspectRatio: Float? = null,
    @Json(name = "has_mrz") val hasMrz: Boolean = false,
    @Json(name = "has_pdf417") val hasPdf417: Boolean = false
)

@JsonClass(generateAdapter = true)
data class TemplateFieldDto(
    val field: String,
    val method: String = "anchor",
    val anchor: AnchorDto? = null,
    val region: RegionDto? = null,
    val validation: ValidationDto? = null
)

@JsonClass(generateAdapter = true)
data class AnchorDto(
    val label: String,
    val position: String
)

@JsonClass(generateAdapter = true)
data class RegionDto(
    val x: Float = 0f,
    val y: Float = 0f,
    val w: Float = 0f,
    val h: Float = 0f
)

@JsonClass(generateAdapter = true)
data class ValidationDto(
    val regex: String? = null,
    val length: Int? = null,
    val charset: String? = null
)
