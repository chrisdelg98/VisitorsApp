package com.eflglobal.visitorsapp.domain.usecase.station

import com.eflglobal.visitorsapp.domain.repository.StationRepository

/**
 * Use Case para verificar si existe una estación activa.
 */
class HasActiveStationUseCase(
    private val stationRepository: StationRepository
) {
    suspend operator fun invoke(): Boolean {
        return stationRepository.hasActiveStation()
    }
}

