package com.eflglobal.visitorsapp.domain.model

/**
 * ═══════════════════════════════════════════════════════════════════════════════
 * DocumentTemplate — a single published OCR template from the backend catalog.
 *
 * Fetched via `GET /api/v1/ocr/templates`, cached in Room, and consumed by the
 * OCR pipeline to (a) identify which document is being scanned and (b) know
 * where each field lives on that document.
 *
 * This is the parsed DOMAIN representation. On the wire it arrives as
 * [com.eflglobal.visitorsapp.data.remote.dto.OcrTemplateDto]; in Room it is
 * stored as [com.eflglobal.visitorsapp.data.local.entity.DocumentTemplateEntity]
 * (with [signature] and [fields] kept as JSON).
 * ═══════════════════════════════════════════════════════════════════════════════
 */
data class DocumentTemplate(
    val id: String,
    val documentTypeId: String?,
    val countryId: String?,
    val code: String?,
    val name: String?,
    val documentKind: String?,
    val subdivision: String?,
    val version: Int,
    /** "front" | "back". */
    val side: String,
    /** "anchor" | "zone" | "mrz" | "pdf417". `pdf417` = read the barcode/QR on this side. */
    val extractionMethod: String,
    val signature: TemplateSignature,
    val fields: List<TemplateField>
) {
    val isBackSide: Boolean get() = side.equals("back", ignoreCase = true)
    val isFrontSide: Boolean get() = side.equals("front", ignoreCase = true)

    /** True when this side should be read through the barcode/QR reader. */
    val isBarcodeMethod: Boolean get() =
        extractionMethod.equals("pdf417", ignoreCase = true) || signature.hasPdf417
}

/**
 * Signature used to IDENTIFY a template from raw OCR text + structural signals.
 */
data class TemplateSignature(
    val keywords: List<String> = emptyList(),
    val idRegex: String? = null,
    val aspectRatio: Float? = null,
    val hasMrz: Boolean = false,
    val hasPdf417: Boolean = false
) {
    /** Lazily-compiled id regex; null when absent or malformed. */
    val compiledIdRegex: Regex? by lazy {
        idRegex?.takeIf { it.isNotBlank() }?.let {
            runCatching { Regex(it) }.getOrNull()
        }
    }
}

/**
 * A single field to extract from the document.
 *
 * @param field   Canonical field name ("first_name", "last_name", "document_number", …).
 * @param method  "anchor" | "zone". (mrz / pdf417 are driven by the template's extractionMethod.)
 * @param anchor  Anchor descriptor when [method] == "anchor".
 * @param region  Normalised 0..1 region when [method] == "zone".
 * @param validation  Optional value validators feeding the confidence score.
 */
data class TemplateField(
    val field: String,
    val method: String,
    val anchor: TemplateAnchor? = null,
    val region: TemplateRegion? = null,
    val validation: TemplateValidation? = null
)

/** @param position "right" | "below" | "left" | "above" | "same_line". */
data class TemplateAnchor(
    val label: String,
    val position: String
)

/** Normalised 0..1 rectangle over the (rectified) document image. */
data class TemplateRegion(
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float
) {
    fun contains(nx: Float, ny: Float): Boolean =
        nx in x..(x + w) && ny in y..(y + h)
}

data class TemplateValidation(
    val regex: String? = null,
    val length: Int? = null,
    val charset: String? = null
) {
    private val compiledRegex: Regex? by lazy {
        regex?.takeIf { it.isNotBlank() }?.let { runCatching { Regex(it) }.getOrNull() }
    }

    /**
     * Returns a 0..1 quality score for [value] against these rules.
     * 1.0 = passes everything present; lower when a rule fails.
     * Null/blank value → 0f.
     */
    fun score(value: String?): Float {
        val v = value?.trim().orEmpty()
        if (v.isBlank()) return 0f
        var checks = 0
        var passed = 0

        compiledRegex?.let {
            checks++
            if (it.matches(v)) passed++
        }
        length?.let {
            checks++
            if (v.length == it) passed++
        }
        charset?.takeIf { it.isNotBlank() }?.let { cs ->
            checks++
            if (v.all { cs.contains(it) }) passed++
        }

        // No validators declared → neutral pass.
        if (checks == 0) return 1f
        return passed.toFloat() / checks
    }
}
