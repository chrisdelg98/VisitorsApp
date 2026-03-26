package com.eflglobal.visitorsapp.domain.usecase.visit

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
    val autoConfirmSeconds: Int
)

/**
 * Use Case that handles the full Continue Visit lifecycle:
 *
 * 1. [lookupVisit] — resolves a QR code to a [VisitLookupResult] with all UI context.
 * 2. [confirmReentry] — same-station re-entry: increments counter and reopens visit.
 * 3. [confirmContinuation] — cross-station: creates a new linked visit record.
 */
class ContinueVisitUseCase(
    private val visitRepository: VisitRepository,
    private val personRepository: PersonRepository,
    private val stationRepository: StationRepository
) {

    /** Phase 1: look up visit by QR code and gather all context. */
    suspend fun lookupVisit(qrCode: String): Result<VisitLookupResult> {
        return try {
            val visit = visitRepository.getVisitByQRCode(qrCode)
                ?: return Result.failure(Exception("visit_not_found"))

            // Reject visits that are not from today
            if (!isFromToday(visit.entryDate)) {
                return Result.failure(Exception("visit_not_today"))
            }

            val person = personRepository.getPersonById(visit.personId)
                ?: return Result.failure(Exception("person_not_found"))

            val currentStation  = stationRepository.getActiveStation()
            val currentStationId = currentStation?.stationId

            // Resolve source station name (best-effort — may be null if station was deleted)
            val sourceStationName  = null  // Station lookup by ID not exposed; future enhancement
            val currentStationName = currentStation?.stationName

            val isSameStation = visit.stationId != null &&
                    visit.stationId == currentStationId

            val autoConfirmSeconds = if (isSameStation) 5 else 10

            // Prefer visit-specific snapshot photo; fall back to person profile photo
            val profilePhotoPath = visit.visitProfilePhotoPath
                ?: person.profilePhotoPath

            Result.success(
                VisitLookupResult(
                    visit              = visit,
                    personId           = person.personId,
                    personFullName     = person.fullName,
                    personFirstName    = person.firstName,
                    personLastName     = person.lastName,
                    profilePhotoPath   = profilePhotoPath,
                    isSameStation      = isSameStation,
                    sourceStationName  = sourceStationName,
                    currentStationName = currentStationName,
                    currentStationId   = currentStationId,
                    hasAlreadyEnded    = visit.exitDate != null,
                    autoConfirmSeconds = autoConfirmSeconds
                )
            )
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
            originalVisit    = lookup.visit,
            currentStationId = lookup.currentStationId
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
}

