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
        Index(value = ["entryDate"])
    ]
)
data class VisitEntity(
    @PrimaryKey
    val visitId: String, // UUID

    val personId: String, // FK a PersonEntity

    val stationId: String?, // FK a StationEntity (nullable por si se borra la estación)

    // Información de la visita
    val visitingPersonName: String, // A quién visita

    val visitorType: String, // Visitante, Contratista, Proveedor, Haciendo entrega

    // Fechas
    val entryDate: Long, // Timestamp de entrada en milisegundos
    val exitDate: Long?, // Timestamp de salida (nullable si aún no ha salido)

    // QR Code único para esta visita
    val qrCodeValue: String, // Valor único generado para el QR

    // Metadata
    val createdAt: Long, // Timestamp de creación del registro

    // Sincronización
    val isSynced: Boolean,
    val lastSyncAt: Long?
)


