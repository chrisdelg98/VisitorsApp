package com.eflglobal.visitorsapp.domain.usecase.visit

import com.eflglobal.visitorsapp.domain.model.Visit
import com.eflglobal.visitorsapp.domain.repository.VisitRepository

/**
 * Use Case para obtener las visitas del día actual.
 */
class GetTodayVisitsUseCase(
    private val visitRepository: VisitRepository
) {
    suspend operator fun invoke(): List<Visit> {
        return visitRepository.getTodayVisits()
    }
}

