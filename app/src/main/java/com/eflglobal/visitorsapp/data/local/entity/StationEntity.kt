package com.eflglobal.visitorsapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "stations",
    indices = [
        Index(value = ["isActive"])
    ]
)
data class StationEntity(
    @PrimaryKey
    val stationId: String, // UUID

    val pin: String, // PIN de 8 dígitos (encriptado en producción)

    val stationName: String, // Nombre de la estación (ej: "EFL SV")

    val countryCode: String, // Código del país (ej: "SV")

    val isActive: Boolean, // Solo puede haber una estación activa

    // Metadata
    val createdAt: Long, // Timestamp de creación en milisegundos
    val activatedAt: Long?, // Timestamp de cuando se activó

    // Sincronización
    val isSynced: Boolean,
    val lastSyncAt: Long?
)


