package com.eflglobal.visitorsapp.domain.usecase.visit

import com.eflglobal.visitorsapp.domain.model.Visit
import com.eflglobal.visitorsapp.domain.repository.VisitRepository

/**
 * Use Case para obtener visitas de una estación en un rango de fechas.
 * Útil para reportes y estadísticas filtradas por período.
 */
class GetVisitsByStationAndDateRangeUseCase(
    private val visitRepository: VisitRepository
) {
    suspend operator fun invoke(
        stationId: String,
        startDate: Long,
        endDate: Long
    ): List<Visit> {
        return visitRepository.getVisitsByStationAndDateRange(stationId, startDate, endDate)
    }
}

