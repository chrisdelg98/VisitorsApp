package com.eflglobal.visitorsapp.domain.usecase.visit

import com.eflglobal.visitorsapp.domain.model.Visit
import com.eflglobal.visitorsapp.domain.repository.VisitRepository

/**
 * Use Case para buscar visitas activas.
 */
class SearchActiveVisitsUseCase(
    private val visitRepository: VisitRepository
) {
    suspend operator fun invoke(query: String): List<Visit> {
        if (query.length < 3) {
            return emptyList()
        }
        return visitRepository.searchActiveVisits(query)
    }
}

