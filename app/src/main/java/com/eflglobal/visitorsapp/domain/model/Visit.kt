package com.eflglobal.visitorsapp.domain.model

data class Visit(
    val visitId: String,
    val personId: String,
    val stationId: String?,
    val visitingPersonName: String,

    /**
     * Who the visitor IS — "Yo soy un:" category.
     * One of: VISITOR, CONTRACTOR, VENDOR, DELIVERY, DRIVER, TEMPORARY_STAFF, OTHER
     */
    val visitorType: String,

    /**
     * WHY they are visiting — purpose/reason of the visit.
     * One of: VISITOR, DRIVER, CONTRACTOR, TEMPORARY_STAFF, DELIVERY, VENDOR, OTHER.
     */
    val visitReason: String,

    /**
     * Free-text description — populated only when [visitReason] == "OTHER".
     */
    val visitReasonCustom: String?,

    val entryDate: Long,
    val exitDate: Long?,
    val qrCodeValue: String,

    /**
     * Immutable audit snapshots — photos captured at this specific visit.
     * Stored in visits/{visitId}/ folder, never overwritten by future visits.
     */
    val visitProfilePhotoPath: String? = null,
    val visitDocumentFrontPath: String? = null,
    val visitDocumentBackPath: String? = null,

    val isSynced: Boolean,
    val lastSyncAt: Long?,

    // ── Continue Visit fields ─────────────────────────────────────────────

    /** Number of same-station re-entries on the same day. */
    val reentryCount: Int = 0,

    /** Timestamp of the last re-entry at the same station. */
    val lastReentryAt: Long? = null,

    /**
     * For cross-station continuations: the visitId of the original visit
     * at the previous station. Null for regular and same-station re-entry visits.
     */
    val originalVisitId: String? = null
)

/** Domain model for the visit_reasons lookup table. */
data class VisitReason(
    val reasonKey: String,
    val labelEs: String,
    val labelEn: String,
    val sortOrder: Int,
    val isActive: Boolean = true
) {
    fun label(lang: String) = if (lang == "es") labelEs else labelEn
}

/** All standard visit reason keys as constants for safe comparisons. */
object VisitReasonKeys {
    const val MEETING           = "MEETING"
    const val INTERVIEW         = "INTERVIEW"
    const val DELIVERY          = "DELIVERY"
    const val PICKUP            = "PICKUP"
    const val MAINTENANCE       = "MAINTENANCE"
    const val TRAINING          = "TRAINING"
    const val AUDIT             = "AUDIT"
    const val TECHNICAL_SERVICE = "TECHNICAL_SERVICE"
    const val ONSITE_WORK       = "ONSITE_WORK"
    const val OTHER             = "OTHER"

    // Legacy keys — kept for backward compatibility with existing visit records
    const val VISITOR         = "VISITOR"
    const val DRIVER          = "DRIVER"
    const val CONTRACTOR      = "CONTRACTOR"
    const val TEMPORARY_STAFF = "TEMPORARY_STAFF"
    const val VENDOR          = "VENDOR"
}
