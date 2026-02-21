package com.eflglobal.visitorsapp.domain.usecase.station

import com.eflglobal.visitorsapp.domain.model.Station
import com.eflglobal.visitorsapp.domain.repository.StationRepository

/**
 * Use Case para obtener la estación activa.
 */
class GetActiveStationUseCase(
    private val stationRepository: StationRepository
) {
    suspend operator fun invoke(): Station? {
        return stationRepository.getActiveStation()
    }
}

