package com.eflglobal.visitorsapp.domain.model

/**
 * A single field extracted from an identity document with its confidence score.
 *
 * @param value       The extracted string value, or null if extraction failed.
 * @param confidence  0.0–1.0 probabilistic confidence. Below threshold → leave blank for manual entry.
 * @param source      Which engine produced this value.
 * @param scoreBreakdown  Debug map of scoring contributions (label proximity, regex, format, etc.)
 */
data class ExtractedField(
    val value: String?,
    val confidence: Float,
    val source: FieldSource,
    val scoreBreakdown: Map<String, Float> = emptyMap()
) {
    /** True when confidence meets the minimum threshold for auto-fill (0.60). */
    val isAutoFillable: Boolean get() = value != null && confidence >= AUTOFILL_THRESHOLD

    /** True when confidence is high enough to trust without user review (0.80). */
    val isHighConfidence: Boolean get() = value != null && confidence >= HIGH_CONFIDENCE_THRESHOLD

    companion object {
        const val AUTOFILL_THRESHOLD      = 0.60f
        const val HIGH_CONFIDENCE_THRESHOLD = 0.80f

        val EMPTY = ExtractedField(value = null, confidence = 0f, source = FieldSource.NONE)
    }
}

enum class FieldSource {
    MRZ,           // ISO 9303 Machine Readable Zone
    LABEL_OCR,     // Label-keyed next-line extraction
    ENTITY,        // ML Kit Entity Extraction
    HEURISTIC,     // All-caps / pattern heuristic
    NONE
}

