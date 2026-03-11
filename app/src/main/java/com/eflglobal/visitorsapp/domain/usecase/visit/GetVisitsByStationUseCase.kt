package com.eflglobal.visitorsapp.domain.usecase.visit

import com.eflglobal.visitorsapp.domain.model.Visit
import com.eflglobal.visitorsapp.domain.repository.VisitRepository

/**
 * Use Case para obtener todas las visitas de una estación.
 * Útil para panel de administración por estación.
 */
class GetVisitsByStationUseCase(
    private val visitRepository: VisitRepository
) {
    suspend operator fun invoke(stationId: String): List<Visit> {
        return visitRepository.getVisitsByStationId(stationId)
    }
}

