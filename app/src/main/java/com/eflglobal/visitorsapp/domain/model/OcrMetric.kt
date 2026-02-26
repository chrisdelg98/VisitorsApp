package com.eflglobal.visitorsapp.domain.model

/**
 * Domain model for a single OCR processing event metric.
 * Stored in Room to enable data-driven tuning of thresholds.
 */
data class OcrMetric(
    val id: Long = 0,
    val timestamp: Long,

    // Classification
    val detectedCountry: String,
    val detectedDocType: String,
    val classificationConfidence: Float,

    // Per-field confidence
    val firstNameConfidence: Float,
    val lastNameConfidence: Float,
    val docNumberConfidence: Float,

    // Per-field source
    val firstNameSource: String,   // FieldSource.name
    val lastNameSource: String,
    val docNumberSource: String,

    // Outcome
    val firstNameAutoFilled: Boolean,
    val lastNameAutoFilled: Boolean,
    val docNumberAutoFilled: Boolean,

    /** True if user manually edited the auto-filled first name */
    val firstNameCorrected: Boolean = false,
    /** True if user manually edited the auto-filled last name */
    val lastNameCorrected: Boolean = false,
    /** True if user manually edited the auto-filled document number */
    val docNumberCorrected: Boolean = false,

    // Raw OCR quality signals
    val ocrCharCount: Int,
    val ocrLineCount: Int,
    val hasMrz: Boolean
)

