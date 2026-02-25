package com.eflglobal.visitorsapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "persons",
    indices = [
        Index(value = ["documentNumber"])   // no longer unique — nullable values allowed
    ]
)
data class PersonEntity(
    @PrimaryKey
    val personId: String,           // UUID

    /** Given name(s) — e.g. "María José" */
    val firstName: String,

    /** Family name(s) / surname(s) — e.g. "González Ramírez" */
    val lastName: String,

    /**
     * Document number — optional.
     * Null when the document could not be scanned or is a foreign document
     * with no recognizable number pattern.
     */
    val documentNumber: String?,

    val documentType: String,       // DUI/ID, Pasaporte, Otro

    // Local image paths
    val profilePhotoPath: String?,
    val documentFrontPath: String?,
    val documentBackPath: String?,

    // Contact information
    val company: String?,
    val email: String,
    val phoneNumber: String,

    // Metadata
    val createdAt: Long,

    // Sync
    val isSynced: Boolean,
    val lastSyncAt: Long?
)

