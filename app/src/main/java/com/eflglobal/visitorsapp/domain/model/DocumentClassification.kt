package com.eflglobal.visitorsapp.domain.model

/**
 * Result of classifying a scanned identity document before field extraction.
 *
 * @param country       ISO-3166 alpha-2 or descriptive name ("SV", "HN", "GT", "US", "UNKNOWN")
 * @param documentType  "DUI" | "PASSPORT" | "DRIVER_LICENSE" | "ID_CARD" | "UNKNOWN"
 * @param confidence    0.0–1.0. Below 0.5 → skip automatic extraction.
 * @param signals       Human-readable list of what contributed to this classification (for logs/metrics).
 */
data class DocumentClassification(
    val country: String,
    val documentType: String,
    val confidence: Float,
    val signals: List<String> = emptyList()
) {
    val isReliable: Boolean get() = confidence >= 0.50f
    val isHighConfidence: Boolean get() = confidence >= 0.75f

    companion object {
        val UNKNOWN = DocumentClassification(
            country = "UNKNOWN",
            documentType = "UNKNOWN",
            confidence = 0f
        )
    }
}

