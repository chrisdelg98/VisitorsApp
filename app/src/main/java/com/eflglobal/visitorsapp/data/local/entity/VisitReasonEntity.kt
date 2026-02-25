package com.eflglobal.visitorsapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Lookup table for visit reason categories.
 *
 * Pre-seeded with the standard set on first launch.
 * The "OTHER" category is special: it stores the user-provided custom text
 * in [VisitEntity.visitReasonCustom], while this table only stores the category key.
 *
 * Standard keys (language-neutral, used for filtering):
 *   VISITOR, DRIVER, CONTRACTOR, TEMPORARY_STAFF, DELIVERY, VENDOR, OTHER
 */
@Entity(tableName = "visit_reasons")
data class VisitReasonEntity(
    @PrimaryKey
    val reasonKey: String,   // e.g. "VISITOR", "DELIVERY", "OTHER"
    val labelEs: String,     // Spanish display label
    val labelEn: String,     // English display label
    val sortOrder: Int,      // Display order in dropdowns / filters
    val isActive: Boolean = true
)

