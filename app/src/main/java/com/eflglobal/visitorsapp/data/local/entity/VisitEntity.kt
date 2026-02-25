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
        Index(value = ["visitReason"])
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

    // Metadata
    val createdAt: Long,

    // Sync
    val isSynced: Boolean,
    val lastSyncAt: Long?
)

