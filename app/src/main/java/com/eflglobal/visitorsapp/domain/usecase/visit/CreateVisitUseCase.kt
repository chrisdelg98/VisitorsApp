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
        visitorType: String,
        visitReason: String,
        visitReasonCustom: String? = null,
        /** Pre-generated visitId — if null a new UUID is generated here. */
        visitId: String? = null,
        /** Audit snapshot photos taken at this specific visit. */
        visitProfilePhotoPath: String? = null,
        visitDocumentFrontPath: String? = null,
        visitDocumentBackPath: String? = null
    ): Result<Visit> {
        val stationId     = stationRepository.getActiveStationId()
        val resolvedVisitId = visitId ?: UUID.randomUUID().toString()
        val qrCode        = visitRepository.generateQRCode(resolvedVisitId)

        val sanitisedCustom = if (visitReason == VisitReasonKeys.OTHER)
            visitReasonCustom?.trim()?.takeIf { it.isNotBlank() }
        else null

        val visit = Visit(
            visitId                = resolvedVisitId,
            personId               = personId,
            stationId              = stationId,
            visitingPersonName     = visitingPersonName,
            visitorType            = visitorType,
            visitReason            = visitReason,
            visitReasonCustom      = sanitisedCustom,
            entryDate              = System.currentTimeMillis(),
            exitDate               = null,
            qrCodeValue            = qrCode,
            visitProfilePhotoPath  = visitProfilePhotoPath,
            visitDocumentFrontPath = visitDocumentFrontPath,
            visitDocumentBackPath  = visitDocumentBackPath,
            isSynced               = false,
            lastSyncAt             = null
        )

        return visitRepository.createVisit(visit)
    }
}
