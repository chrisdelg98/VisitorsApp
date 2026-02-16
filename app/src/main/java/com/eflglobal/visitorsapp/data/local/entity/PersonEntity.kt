package com.eflglobal.visitorsapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "persons",
    indices = [
        Index(value = ["documentNumber"], unique = true)
    ]
)
data class PersonEntity(
    @PrimaryKey
    val personId: String, // UUID

    val fullName: String,

    val documentNumber: String,

    val documentType: String, // DUI/ID, Pasaporte, Otro

    // Paths locales de imágenes
    val profilePhotoPath: String?,
    val documentFrontPath: String?,
    val documentBackPath: String?,

    // Información de contacto
    val company: String?,
    val email: String,
    val phoneNumber: String,

    // Metadata
    val createdAt: Long, // Timestamp en milisegundos

    // Sincronización
    val isSynced: Boolean,
    val lastSyncAt: Long?
)


