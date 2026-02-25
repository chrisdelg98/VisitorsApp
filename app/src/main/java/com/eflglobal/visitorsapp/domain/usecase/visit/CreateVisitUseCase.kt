package com.eflglobal.visitorsapp.domain.usecase.visit

import com.eflglobal.visitorsapp.domain.model.Visit
import com.eflglobal.visitorsapp.domain.model.VisitReasonKeys
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
        visitorType: String,               // "Yo soy un:" — who the visitor IS
        visitReason: String,               // "Motivo de visita" — why they are visiting
        visitReasonCustom: String? = null  // free-text only when visitReason == "OTHER"
    ): Result<Visit> {
        val stationId = stationRepository.getActiveStationId()
        val visitId   = UUID.randomUUID().toString()
        val qrCode    = visitRepository.generateQRCode(visitId)

        val sanitisedCustom = if (visitReason == VisitReasonKeys.OTHER)
            visitReasonCustom?.trim()?.takeIf { it.isNotBlank() }
        else null

        val visit = Visit(
            visitId            = visitId,
            personId           = personId,
            stationId          = stationId,
            visitingPersonName = visitingPersonName,
            visitorType        = visitorType,
            visitReason        = visitReason,
            visitReasonCustom  = sanitisedCustom,
            entryDate          = System.currentTimeMillis(),
            exitDate           = null,
            qrCodeValue        = qrCode,
            isSynced           = false,
            lastSyncAt         = null
        )

        return visitRepository.createVisit(visit)
    }
}
