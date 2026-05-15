package com.eflglobal.visitorsapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "visits",
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["personId"],
            childColumns = ["personId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = StationEntity::class,
            parentColumns = ["stationId"],
            childColumns = ["stationId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["personId"]),
        Index(value = ["stationId"]),
        Index(value = ["qrCodeValue"], unique = true),
        Index(value = ["entryDate"]),
        Index(value = ["visitorType"]),
        Index(value = ["visitReason"]),
        Index(value = ["syncStatus", "createdAt"])  // SyncWorker FIFO scan
    ]
)
data class VisitEntity(
    @PrimaryKey
    val visitId: String,            // UUID

    val personId: String,           // FK → PersonEntity

    val stationId: String?,         // FK → StationEntity (nullable if station deleted)

    /** Name of the host / person being visited. */
    val visitingPersonName: String,

    /**
     * Who the visitor IS — category of person.
     * One of: VISITOR, CONTRACTOR, VENDOR, DELIVERY, DRIVER, TEMPORARY_STAFF, OTHER
     * Shown in DocumentScanScreen as "Yo soy un:" / "I am a:"
     */
    val visitorType: String,

    /**
     * WHY they are visiting — purpose/reason of the visit.
     * One of: VISITOR, DRIVER, CONTRACTOR, TEMPORARY_STAFF, DELIVERY, VENDOR, OTHER.
     * Shown in PersonDataScreen as "Motivo de la visita" / "Reason for Visit"
     */
    val visitReason: String,

    /**
     * Free-text description — only populated when [visitReason] == "OTHER".
     * Null for all standard categories.
     */
    val visitReasonCustom: String?,

    // Timestamps
    val entryDate: Long,
    val exitDate: Long?,

    /** Unique QR code value for this visit. */
    val qrCodeValue: String,

    /**
     * Immutable audit snapshots — photos captured at this specific visit.
     * Stored in visits/{visitId}/ and NEVER overwritten.
     * These are the authoritative photos for audit/compliance queries.
     */
    val visitProfilePhotoPath: String?,
    val visitDocumentFrontPath: String?,
    val visitDocumentBackPath: String?,

    // Metadata
    val createdAt: Long,

    // Sync
    val isSynced: Boolean,
    val lastSyncAt: Long?,

    // ── Continue Visit fields ─────────────────────────────────────────────

    /**
     * Number of times the visitor re-entered on the same day at the same station.
     * Incremented by the "Continue Visit" fast re-entry flow.
     */
    val reentryCount: Int = 0,

    /** Timestamp of the most recent re-entry (same-station fast re-entry). */
    val lastReentryAt: Long? = null,

    /**
     * For cross-station continuations: the visitId of the original/source visit.
     * Null for first-time visits and same-station reentries.
     */
    val originalVisitId: String? = null,

    // ── Phase 3: cross-station re-entry metadata (used in Phase 6) ────────

    /** Remote stationId of the source visit when this row is a re-entry. */
    val reentryFromStationId: String? = null,

    /** Cached display name of [reentryFromStationId] for the admin panel. */
    val reentryFromStationName: String? = null,

    // ── Phase 3: per-row sync tracking against the backend ────────────────

    /** UUID assigned by the backend after a successful POST /v1/visits. */
    val remoteId: String? = null,

    /** One of [SyncStatus]. New rows nace `pending`. */
    val syncStatus: String = SyncStatus.PENDING,

    /** Number of upload attempts made by the SyncWorker. */
    val syncAttempts: Int = 0,

    /** Last error returned by the backend (or network) for diagnostics. */
    val lastSyncError: String? = null,

    /** Timestamp at which `personal_photo` was confirmed uploaded; null = pending. */
    val personalPhotoSyncedAt: Long? = null,

    /** Timestamp at which `doc_front` was confirmed uploaded; null = pending. */
    val docFrontSyncedAt: Long? = null,

    /** Timestamp at which `doc_back` was confirmed uploaded; null = pending. */
    val docBackSyncedAt: Long? = null,

    /** Timestamp at which the checkout PATCH was confirmed; null = pending. */
    val checkoutSyncedAt: Long? = null
)

