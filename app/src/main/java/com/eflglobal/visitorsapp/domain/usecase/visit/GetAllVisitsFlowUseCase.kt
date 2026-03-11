package com.eflglobal.visitorsapp.domain.usecase.visit

import com.eflglobal.visitorsapp.domain.model.Visit
import com.eflglobal.visitorsapp.domain.repository.VisitRepository
import kotlinx.coroutines.flow.Flow

/**
 * Use Case para obtener todas las visitas como Flow.
 * Para dashboard en tiempo real con actualización automática.
 */
class GetAllVisitsFlowUseCase(
    private val visitRepository: VisitRepository
) {
    operator fun invoke(): Flow<List<Visit>> {
        return visitRepository.getAllVisitsFlow()
    }
}

