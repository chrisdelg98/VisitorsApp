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
    val isSynced: Boolean,
    val lastSyncAt: Long?
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
    const val VISITOR         = "VISITOR"
    const val DRIVER          = "DRIVER"
    const val CONTRACTOR      = "CONTRACTOR"
    const val TEMPORARY_STAFF = "TEMPORARY_STAFF"
    const val DELIVERY        = "DELIVERY"
    const val VENDOR          = "VENDOR"
    const val OTHER           = "OTHER"
}
