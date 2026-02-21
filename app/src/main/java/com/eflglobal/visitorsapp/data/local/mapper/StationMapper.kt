package com.eflglobal.visitorsapp.data.local.mapper

import com.eflglobal.visitorsapp.data.local.entity.StationEntity
import com.eflglobal.visitorsapp.domain.model.Station

fun StationEntity.toDomain(): Station {
    return Station(
        stationId = stationId,
        pin = pin,
        stationName = stationName,
        countryCode = countryCode,
        createdAt = createdAt,
        isActive = isActive
    )
}

fun Station.toEntity(
    activatedAt: Long? = null,
    isSynced: Boolean = false,
    lastSyncAt: Long? = null
): StationEntity {
    return StationEntity(
        stationId = stationId,
        pin = pin,
        stationName = stationName,
        countryCode = countryCode,
        createdAt = createdAt,
        isActive = isActive,
        activatedAt = activatedAt,
        isSynced = isSynced,
        lastSyncAt = lastSyncAt
    )
}


