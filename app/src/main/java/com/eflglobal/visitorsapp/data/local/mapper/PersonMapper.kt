package com.eflglobal.visitorsapp.data.local.mapper

import com.eflglobal.visitorsapp.data.local.entity.PersonEntity
import com.eflglobal.visitorsapp.domain.model.Person

fun PersonEntity.toDomain(): Person {
    return Person(
        personId = personId,
        fullName = fullName,
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
        fullName = fullName,
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

