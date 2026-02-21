package com.eflglobal.visitorsapp.domain.usecase.visit

import com.eflglobal.visitorsapp.domain.model.Visit
import com.eflglobal.visitorsapp.domain.repository.StationRepository
import com.eflglobal.visitorsapp.domain.repository.VisitRepository
import java.util.UUID

/**
 * Use Case para crear una nueva visita.
 */
class CreateVisitUseCase(
    private val visitRepository: VisitRepository,
    private val stationRepository: StationRepository
) {
    suspend operator fun invoke(
        personId: String,
        visitingPersonName: String,
        visitorType: String
    ): Result<Visit> {
        // Obtener estación activa
        val stationId = stationRepository.getActiveStationId()

        // Generar ID de visita
        val visitId = UUID.randomUUID().toString()

        // Generar QR code único
        val qrCode = visitRepository.generateQRCode(visitId)

        // Crear visita
        val visit = Visit(
            visitId = visitId,
            personId = personId,
            stationId = stationId,
            visitingPersonName = visitingPersonName,
            visitorType = visitorType,
            entryDate = System.currentTimeMillis(),
            exitDate = null,
            qrCodeValue = qrCode,
            isSynced = false,
            lastSyncAt = null
        )

        return visitRepository.createVisit(visit)
    }
}

