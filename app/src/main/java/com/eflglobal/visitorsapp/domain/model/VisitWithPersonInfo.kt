package com.eflglobal.visitorsapp.domain.model

/**
 * Visit with additional person information for display purposes.
 */
data class VisitWithPersonInfo(
    val visit: Visit,
    val personFirstName: String,
    val personLastName: String,
    val personCompany: String? = null
) {
    val fullPersonName: String
        get() = "$personFirstName $personLastName".trim()
}
