package com.eflglobal.visitorsapp.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Body for `POST /api/v1/ocr/failed-documents`.
 *
 * PRIVACY: we send the structured [ocrBlocks] and (when present) [barcodeRaw]
 * — never the image, and [ocrText] only when explicitly masked. The backend
 * uses this to author new templates / debug unknown barcode formats.
 */
@JsonClass(generateAdapter = true)
data class FailedDocumentBody(
    @Json(name = "detected_type") val detectedType: String? = null,
    @Json(name = "detected_confidence") val detectedConfidence: Float? = null,
    @Json(name = "ocr_blocks") val ocrBlocks: List<OcrBlockDto> = emptyList(),
    @Json(name = "ocr_text") val ocrText: String? = null,
    @Json(name = "barcode_raw") val barcodeRaw: String? = null,
    @Json(name = "app_version") val appVersion: String? = null
)

/** A single OCR block: its text plus a NORMALISED (0..1) bounding box. */
@JsonClass(generateAdapter = true)
data class OcrBlockDto(
    val text: String,
    val box: BoxDto
)

@JsonClass(generateAdapter = true)
data class BoxDto(
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float
)

/** `data` payload of a successful `POST /api/v1/ocr/failed-documents` (201). */
@JsonClass(generateAdapter = true)
data class FailedDocumentResponse(
    val id: String? = null
)
