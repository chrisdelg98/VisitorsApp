package com.eflglobal.visitorsapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "persons",
    indices = [
        Index(value = ["documentNumber"]),  // no longer unique — nullable values allowed
        Index(value = ["syncStatus"])       // SyncWorker FIFO scan
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

    // ── Legacy local-only sync flags (kept for backwards compat) ──────────
    val isSynced: Boolean,
    val lastSyncAt: Long?,

    // ── Phase 3: per-row sync tracking against the backend ────────────────

    /** UUID assigned by the backend after a successful POST /v1/visitors. */
    val remoteId: String? = null,

    /** One of [SyncStatus]. New rows nace `pending`. */
    val syncStatus: String = SyncStatus.PENDING,

    /** Number of upload attempts made by the SyncWorker. */
    val syncAttempts: Int = 0,

    /** Last error returned by the backend (or network) for diagnostics. */
    val lastSyncError: String? = null
)

