package com.eflglobal.visitorsapp.domain.usecase.visit

import com.eflglobal.visitorsapp.domain.repository.VisitRepository

/**
 * Use Case para finalizar una visita mediante QR code.
 */
class EndVisitByQRUseCase(
    private val visitRepository: VisitRepository
) {
    suspend operator fun invoke(qrCode: String): Result<Unit> {
        val exitDate = System.currentTimeMillis()
        return visitRepository.endVisitByQRCode(qrCode, exitDate)
    }
}

