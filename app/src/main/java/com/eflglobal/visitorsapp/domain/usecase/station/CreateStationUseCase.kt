package com.eflglobal.visitorsapp.domain.usecase.station

import com.eflglobal.visitorsapp.domain.model.Station
import com.eflglobal.visitorsapp.domain.repository.StationRepository

/**
 * Use Case para crear y activar una estación.
 */
class CreateStationUseCase(
    private val stationRepository: StationRepository
) {
    suspend operator fun invoke(
        pin: String,
        stationName: String,
        countryCode: String
    ): Result<Station> {
        return stationRepository.createAndActivateStation(pin, stationName, countryCode)
    }
}

