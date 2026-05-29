package com.eflglobal.visitorsapp.data.local.mapper

import com.eflglobal.visitorsapp.data.local.entity.PersonEntity
import com.eflglobal.visitorsapp.data.local.entity.SyncStatus
import com.eflglobal.visitorsapp.domain.model.Person

fun PersonEntity.toDomain(): Person {
    return Person(
        personId = personId,
        remoteId = remoteId,
        firstName = firstName,
        lastName = lastName,
        documentNumber = documentNumber,
        documentType = documentType,
        profilePhotoPath = profilePhotoPath,
        documentFrontPath = documentFrontPath,
        documentBackPath = documentBackPath,
        company = company,
        email = email,
        phoneNumber = phoneNumber,
        createdAt = createdAt,
        isSynced = isSynced,
        lastSyncAt = lastSyncAt
    )
}

fun Person.toEntity(): PersonEntity {
    return PersonEntity(
        personId = personId,
        firstName = firstName,
        remoteId = remoteId,
        // A populated remoteId means the visitor already exists in the backend
        // (e.g. inserted from a cross-station QR lookup), so the row must not be
        // re-POSTed by the SyncWorker. Local-only persons keep remoteId == null
        // and therefore stay pending until pushed.
        syncStatus = if (remoteId != null) SyncStatus.SYNCED else SyncStatus.PENDING,
        lastName = lastName,
        documentNumber = documentNumber,
        documentType = documentType,
        profilePhotoPath = profilePhotoPath,
        documentFrontPath = documentFrontPath,
        documentBackPath = documentBackPath,
        company = company,
        email = email,
        phoneNumber = phoneNumber,
        createdAt = createdAt,
        isSynced = isSynced,
        lastSyncAt = lastSyncAt
    )
}
