package com.eflglobal.visitorsapp.data.local.mapper

import com.eflglobal.visitorsapp.data.local.entity.VisitEntity
import com.eflglobal.visitorsapp.data.local.entity.VisitReasonEntity
import com.eflglobal.visitorsapp.domain.model.Visit
import com.eflglobal.visitorsapp.domain.model.VisitReason

fun VisitEntity.toDomain(): Visit = Visit(
    visitId                = visitId,
    personId               = personId,
    stationId              = stationId,
    visitingPersonName     = visitingPersonName,
    visitorType            = visitorType,
    visitReason            = visitReason,
    visitReasonCustom      = visitReasonCustom,
    entryDate              = entryDate,
    exitDate               = exitDate,
    qrCodeValue            = qrCodeValue,
    visitProfilePhotoPath  = visitProfilePhotoPath,
    visitDocumentFrontPath = visitDocumentFrontPath,
    visitDocumentBackPath  = visitDocumentBackPath,
    isSynced               = isSynced,
    lastSyncAt             = lastSyncAt,
    reentryCount           = reentryCount,
    lastReentryAt          = lastReentryAt,
    originalVisitId        = originalVisitId
)

fun Visit.toEntity(createdAt: Long = System.currentTimeMillis()): VisitEntity = VisitEntity(
    visitId                = visitId,
    personId               = personId,
    stationId              = stationId,
    visitingPersonName     = visitingPersonName,
    visitorType            = visitorType,
    visitReason            = visitReason,
    visitReasonCustom      = visitReasonCustom,
    entryDate              = entryDate,
    exitDate               = exitDate,
    qrCodeValue            = qrCodeValue,
    visitProfilePhotoPath  = visitProfilePhotoPath,
    visitDocumentFrontPath = visitDocumentFrontPath,
    visitDocumentBackPath  = visitDocumentBackPath,
    createdAt              = createdAt,
    isSynced               = isSynced,
    lastSyncAt             = lastSyncAt,
    reentryCount           = reentryCount,
    lastReentryAt          = lastReentryAt,
    originalVisitId        = originalVisitId
)

fun VisitReasonEntity.toDomain(): VisitReason = VisitReason(
    reasonKey = reasonKey,
    labelEs   = labelEs,
    labelEn   = labelEn,
    sortOrder = sortOrder,
    isActive  = isActive
)
