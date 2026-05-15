package com.eflglobal.visitorsapp.domain.usecase.visit

import com.eflglobal.visitorsapp.data.remote.dto.VisitDto
import com.eflglobal.visitorsapp.data.sync.RemoteVisitLookup
import com.eflglobal.visitorsapp.domain.model.Person
import com.eflglobal.visitorsapp.domain.model.Visit
import com.eflglobal.visitorsapp.domain.repository.PersonRepository
import com.eflglobal.visitorsapp.domain.repository.StationRepository
import com.eflglobal.visitorsapp.domain.repository.VisitRepository

/**
 * All the context needed to display the Continue Visit confirmation modal.
 */
data class VisitLookupResult(
    val visit: Visit,
    val personId: String,
    val personFullName: String,
    val personFirstName: String,
    val personLastName: String,
    /** Photo path from the most recent visit snapshot (or person profile fallback). */
    val profilePhotoPath: String?,
    val isSameStation: Boolean,
    /** Friendly name of the station where the QR was originally created. */
    val sourceStationName: String?,
    /** Friendly name of the current (receiving) station. */
    val currentStationName: String?,
    val currentStationId: String?,
    /** True when the visit already has an exitDate set. */
    val hasAlreadyEnded: Boolean,
    /** Seconds before the modal auto-confirms (5 = same station, 10 = different). */
    val autoConfirmSeconds: Int,
    /** True when the visit was fetched from the backend (cross-station re-entry). */
    val isFromRemote: Boolean = false,
    /** Remote stationId of the source visit; populated only for cross-station hits. */
    val sourceStationId: String? = null
)

/**
 * Use Case that handles the full Continue Visit lifecycle:
 *
 * 1. [lookupVisit] — resolves a QR code to a [VisitLookupResult] with all UI
 *    context. First checks the local Room; if nothing is found and a
 *    [RemoteVisitLookup] is wired in, falls back to `GET /v1/visits/{id}` so
 *    QRs printed by another station of the same tenant can be honoured.
 * 2. [confirmReentry] — same-station re-entry: increments counter and reopens visit.
 * 3. [confirmContinuation] — cross-station: creates a new linked visit record.
 */
class ContinueVisitUseCase(
    private val visitRepository: VisitRepository,
    private val personRepository: PersonRepository,
    private val stationRepository: StationRepository,
    private val remoteLookup: RemoteVisitLookup? = null
) {

    /** Phase 1: look up visit by QR code and gather all context. */
    suspend fun lookupVisit(qrCode: String): Result<VisitLookupResult> {
        return try {
            // 1) Try local Room first — covers same-tablet QR and any visit
            //    that's already been mirrored locally.
            visitRepository.getVisitByQRCode(qrCode)?.let { local ->
                return Result.success(buildLocalResult(local))
            }

            // 2) Fall back to backend lookup (Phase 6). QR codes generated on
            //    the tablet are the visit UUID itself; the backend exposes
            //    GET /v1/visits/{id} cross-station for the same tenant.
            val remote = remoteLookup?.lookup(qrCode)
                ?: return Result.failure(Exception("visit_not_found"))

            buildRemoteResult(remote)
                ?: Result.failure(Exception("visit_not_today"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Phase 2a: same-station re-entry — increments counter and reopens visit. */
    suspend fun confirmReentry(visitId: String): Result<Unit> =
        visitRepository.registerReentry(visitId)

    /** Phase 2b: cross-station continuation — creates a new linked visit record. */
    suspend fun confirmContinuation(lookup: VisitLookupResult): Result<Visit> =
        visitRepository.createContinuationVisit(
            originalVisit          = lookup.visit,
            currentStationId       = lookup.currentStationId,
            reentryFromStationId   = lookup.sourceStationId,
            reentryFromStationName = lookup.sourceStationName
        )

    // ── Builders ─────────────────────────────────────────────────────────

    private suspend fun buildLocalResult(visit: Visit): VisitLookupResult {
        if (!isFromToday(visit.entryDate)) {
            throw Exception("visit_not_today")
        }
        val person = personRepository.getPersonById(visit.personId)
            ?: throw Exception("person_not_found")
        val currentStation   = stationRepository.getActiveStation()
        val currentStationId = currentStation?.stationId
        val isSameStation    = visit.stationId != null &&
                visit.stationId == currentStationId
        val profilePhotoPath = visit.visitProfilePhotoPath ?: person.profilePhotoPath
        return VisitLookupResult(
            visit              = visit,
            personId           = person.personId,
            personFullName     = person.fullName,
            personFirstName    = person.firstName,
            personLastName     = person.lastName,
            profilePhotoPath   = profilePhotoPath,
            isSameStation      = isSameStation,
            sourceStationName  = null, // not exposed locally yet
            currentStationName = currentStation?.stationName,
            currentStationId   = currentStationId,
            hasAlreadyEnded    = visit.exitDate != null,
            autoConfirmSeconds = if (isSameStation) 5 else 10,
            isFromRemote       = false,
            sourceStationId    = visit.stationId
        )
    }

    /**
     * Builds a result from a backend payload, persisting the visitor locally
     * and caching the three images so the confirmation modal can render them.
     * Returns `null` when the remote visit is not from today.
     */
    private suspend fun buildRemoteResult(dto: VisitDto): Result<VisitLookupResult> {
        val checkInMillis = parseIso8601(dto.checkIn)
            ?: return Result.failure(Exception("visit_not_today"))
        if (!isFromToday(checkInMillis)) {
            return Result.failure(Exception("visit_not_today"))
        }

        // Persist (or refresh) the visitor row locally so the new visit can
        // reference it via foreign key. We mirror the backend UUID as the
        // local personId to keep mappings 1-to-1.
        val visitor = dto.visitor
        val personId = visitor?.id ?: dto.visitorId
        val personFirstName = visitor?.firstName ?: "?"
        val personLastName  = visitor?.lastName ?: ""
        val person = personRepository.getPersonById(personId)
            ?: insertPersonFromRemote(personId, visitor).also {
                personRepository.createPerson(it)
            }

        // Cache images on disk so the modal (and later the admin panel) have
        // something to render without depending on connectivity.
        val cached = remoteLookup?.cacheImages(dto.id, dto.id) ?: emptyMap()

        val currentStation   = stationRepository.getActiveStation()
        val currentStationId = currentStation?.stationId
        val isSameStation    = dto.station?.id != null && dto.station.id == currentStationId

        // Build a Visit domain object from the remote DTO. Note: this object
        // is not inserted as a visit row — it only feeds the modal and is
        // later passed to createContinuationVisit, which derives a new row.
        val visit = Visit(
            visitId                = dto.id,
            personId               = person.personId,
            stationId              = dto.station?.id,
            visitingPersonName     = dto.visitingPerson,
            visitorType            = dto.visitorType,
            visitReason            = dto.visitReason,
            visitReasonCustom      = dto.visitReasonCustom,
            entryDate              = checkInMillis,
            exitDate               = dto.checkOut?.let { parseIso8601(it) },
            qrCodeValue            = dto.id,                      // QR == visit UUID
            visitProfilePhotoPath  = cached["personal_photo"],
            visitDocumentFrontPath = cached["doc_front"],
            visitDocumentBackPath  = cached["doc_back"],
            isSynced               = true,
            lastSyncAt             = System.currentTimeMillis(),
            originalVisitId        = dto.originalVisitId
        )

        return Result.success(
            VisitLookupResult(
                visit              = visit,
                personId           = person.personId,
                personFullName     = person.fullName,
                personFirstName    = personFirstName,
                personLastName     = personLastName,
                profilePhotoPath   = cached["personal_photo"],
                isSameStation      = isSameStation,
                sourceStationName  = dto.station?.name,
                currentStationName = currentStation?.stationName,
                currentStationId   = currentStationId,
                hasAlreadyEnded    = visit.exitDate != null,
                autoConfirmSeconds = if (isSameStation) 5 else 10,
                isFromRemote       = true,
                sourceStationId    = dto.station?.id
            )
        )
    }

    private fun insertPersonFromRemote(
        personId: String,
        v: com.eflglobal.visitorsapp.data.remote.dto.VisitorDto?
    ): Person = Person(
        personId        = personId,
        firstName       = v?.firstName ?: "?",
        lastName        = v?.lastName ?: "",
        documentNumber  = v?.documentNumber,
        documentType    = v?.documentType ?: "OTHER",
        profilePhotoPath  = null,
        documentFrontPath = null,
        documentBackPath  = null,
        company         = v?.company,
        email           = v?.email ?: "",
        phoneNumber     = v?.phone ?: "",
        createdAt       = System.currentTimeMillis(),
        isSynced        = true,
        lastSyncAt      = System.currentTimeMillis()
    )

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun isFromToday(timestamp: Long): Boolean {
        val calendar = java.util.Calendar.getInstance()
        val todayYear = calendar.get(java.util.Calendar.YEAR)
        val todayDay  = calendar.get(java.util.Calendar.DAY_OF_YEAR)
        calendar.timeInMillis = timestamp
        return calendar.get(java.util.Calendar.YEAR)        == todayYear &&
               calendar.get(java.util.Calendar.DAY_OF_YEAR) == todayDay
    }

    /**
     * Best-effort ISO-8601 parser. The backend returns timestamps with an
     * offset (e.g. `2026-05-15T08:30:00-06:00`). API 26+ ships
     * [java.time.OffsetDateTime] which handles them natively.
     */
    private fun parseIso8601(value: String): Long? = try {
        java.time.OffsetDateTime.parse(value).toInstant().toEpochMilli()
    } catch (_: Exception) {
        null
    }
}


