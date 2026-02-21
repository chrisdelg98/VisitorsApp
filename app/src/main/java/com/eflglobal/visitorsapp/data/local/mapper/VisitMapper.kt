package com.eflglobal.visitorsapp.data.local.mapper

import com.eflglobal.visitorsapp.data.local.entity.VisitEntity
import com.eflglobal.visitorsapp.domain.model.Visit

fun VisitEntity.toDomain(): Visit {
    return Visit(
        visitId = visitId,
        personId = personId,
        stationId = stationId,
        visitingPersonName = visitingPersonName,
        visitorType = visitorType,
        entryDate = entryDate,
        exitDate = exitDate,
        qrCodeValue = qrCodeValue,
        isSynced = isSynced,
        lastSyncAt = lastSyncAt
    )
}

fun Visit.toEntity(
    createdAt: Long = System.currentTimeMillis()
): VisitEntity {
    return VisitEntity(
        visitId = visitId,
        personId = personId,
        stationId = stationId,
        visitingPersonName = visitingPersonName,
        visitorType = visitorType,
        entryDate = entryDate,
        exitDate = exitDate,
        qrCodeValue = qrCodeValue,
        createdAt = createdAt,
        isSynced = isSynced,
        lastSyncAt = lastSyncAt
    )
}


