package com.eflglobal.visitorsapp.domain.model

/**
 * Visit with additional person information for display purposes.
 */
data class VisitWithPersonInfo(
    val visit: Visit,
    val personFirstName: String,
    val personLastName: String,
    val personCompany: String? = null,
    /** Person's registration/current profile photo (for list views). */
    val personProfilePhotoPath: String? = null,
    /** Person's original registration document photos. */
    val personDocumentFrontPath: String? = null,
    val personDocumentBackPath: String? = null,
    /**
     * Visit-specific photo snapshots for audit — taken at this exact visit.
     * Prefer these over person photos when viewing a specific visit record.
     * Null for visits created before version 5 migration.
     */
    val visitProfilePhotoPath: String? = null,
    val visitDocumentFrontPath: String? = null,
    val visitDocumentBackPath: String? = null
) {
    val fullPersonName: String
        get() = "$personFirstName $personLastName".trim()
}
