package com.eflglobal.visitorsapp.data.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.eflglobal.visitorsapp.data.local.AppDatabase
import com.eflglobal.visitorsapp.data.local.dao.PersonDao
import com.eflglobal.visitorsapp.data.local.dao.VisitDao
import com.eflglobal.visitorsapp.data.local.entity.PersonEntity
import com.eflglobal.visitorsapp.data.local.entity.SyncStatus
import com.eflglobal.visitorsapp.data.local.entity.VisitEntity
import com.eflglobal.visitorsapp.core.utils.EmailValidator
import com.eflglobal.visitorsapp.data.remote.ApiClient
import com.eflglobal.visitorsapp.data.remote.ApiErrorCode
import com.eflglobal.visitorsapp.data.remote.ApiException
import com.eflglobal.visitorsapp.data.remote.SecureStore
import com.eflglobal.visitorsapp.data.remote.VisitorsApi
import com.eflglobal.visitorsapp.data.remote.dto.CheckoutBody
import com.eflglobal.visitorsapp.data.remote.dto.CreateVisitBody
import com.eflglobal.visitorsapp.data.remote.dto.CreateVisitorBody
import com.eflglobal.visitorsapp.data.remote.dto.ReentryBody
import com.eflglobal.visitorsapp.data.remote.formatIso8601
import java.io.File

/**
 * Pushes locally-created rows up to the backend FIFO.
 *
 * The worker runs **single-threaded** by design — one HTTP roundtrip at a
 * time, oldest pending row first. That keeps ordering deterministic and
 * avoids hammering the backend from devices with bursty connectivity.
 *
 * Per-visit pipeline:
 *
 *   1.  Ensure the associated [PersonEntity] has a `remoteId`; if missing,
 *       `POST /v1/visitors` and store it.
 *   2.  If the visit has no `remoteId`, `POST /v1/visits` and store it.
 *   3.  For each image path on the row whose `*SyncedAt` is null, upload it
 *       and stamp the timestamp.
 *   4.  If the visit has an `exitDate` and `checkoutSyncedAt` is null,
 *       `PATCH /v1/visits/{remoteId}/checkout`.
 *   5.  Mark the row [SyncStatus.SYNCED] only when 1-4 all completed without
 *       any pending image left.
 *
 * Error policy:
 *   - Transient ([ApiException.isTransient]) → [Result.retry] with WorkManager
 *     exponential backoff. The row stays `pending`.
 *   - Auth failure ([ApiException.isAuthFailure]) → wipe [SecureStore] and
 *     [Result.failure] so the next app open lands on StationSetup.
 *   - Anything else → mark the row [SyncStatus.FAILED]; admin panel surfaces
 *     it for manual handling. The worker keeps going with the next row.
 */
class SyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val tag = "SyncWorker"
    private val ctx = appContext.applicationContext

    private val api: VisitorsApi by lazy { ApiClient.get(ctx) }
    private val db: AppDatabase by lazy { AppDatabase.getInstance(ctx) }
    private val personDao: PersonDao by lazy { db.personDao() }
    private val visitDao: VisitDao by lazy { db.visitDao() }

    override suspend fun doWork(): Result {
        if (!SecureStore.hasStation(ctx)) {
            // No api_key — nothing to sync until the device gets activated.
            return Result.success()
        }

        val pending: List<VisitEntity> = visitDao.getPendingVisits()
        if (pending.isEmpty()) {
            // Nothing to do, but also flush any orphan persons (rare).
            return flushOrphanPersons()
        }

        var retryRequested = false

        for (visit in pending) {
            try {
                pushVisit(visit)
            } catch (auth: AuthWipeException) {
                Log.w(tag, "Auth wipe triggered, aborting sync run.", auth)
                return Result.failure()
            } catch (e: ApiException) {
                if (e.isTransient) {
                    Log.w(tag, "Transient failure on visit ${visit.visitId}, retrying later: ${e.code}")
                    visitDao.bumpVisitAttempt(visit.visitId, "${e.code}: ${e.message}")
                    retryRequested = true
                    // Skip remaining rows on network outage — they'll come back next run.
                    if (e.code == ApiErrorCode.NETWORK_UNAVAILABLE) break
                } else {
                    Log.e(tag, "Permanent failure on visit ${visit.visitId}: ${e.code} ${e.message}")
                    visitDao.markVisitFailed(visit.visitId, "${e.code}: ${e.message}")
                }
            } catch (e: Exception) {
                Log.e(tag, "Unexpected error on visit ${visit.visitId}", e)
                visitDao.bumpVisitAttempt(visit.visitId, e.message ?: "unknown")
                retryRequested = true
            }
        }

        flushOrphanPersons()
        return if (retryRequested) Result.retry() else Result.success()
    }

    // ── Visit pipeline ────────────────────────────────────────────────────

    private suspend fun pushVisit(visit: VisitEntity) {
        // 1. Visitor — make sure it has a remoteId before we POST the visit.
        val person = personDao.getPersonById(visit.personId)
            ?: throw IllegalStateException("Visit ${visit.visitId} references missing person ${visit.personId}")

        val visitorRemoteId = ensurePersonSynced(person)

        // 2. Visit row itself.
        val visitRemoteId = visit.remoteId ?: run {
            val created = apiCall {
                api.createVisit(
                    CreateVisitBody(
                        visitorId          = visitorRemoteId,
                        visitorType        = visit.visitorType,
                        visitReason        = visit.visitReason,
                        visitReasonCustom  = visit.visitReasonCustom,
                        visitingPerson     = visit.visitingPersonName,
                        notes              = null,
                        originalVisitId    = visit.originalVisitId,
                        reentryFromStationId = visit.reentryFromStationId,
                        // Send the real entry time so the portal shows when the
                        // visitor actually arrived, not when the row was synced.
                        checkIn            = formatIso8601(visit.entryDate)
                    )
                )
            }
            visitDao.markVisitSynced(visit.visitId, created.id, System.currentTimeMillis())
            created.id
        }

        // 3. Images. We re-read the row because step 2 may have updated it.
        val refreshed = visitDao.getVisitById(visit.visitId) ?: return
        uploadPendingImages(refreshed, visitRemoteId)

        // 4. Re-entry. If the visit was reopened locally after being synced,
        // tell the backend before pushing the second checkout — otherwise
        // a re-checkout would target an already-completed visit. We re-read
        // again because uploadPendingImages may have mutated the row.
        val afterImages = visitDao.getVisitById(visit.visitId) ?: return
        if (needsReentryPush(afterImages)) {
            val lastReentryAt = afterImages.lastReentryAt!!
            try {
                apiCall {
                    api.reentry(
                        visitRemoteId,
                        ReentryBody(
                            reentryCount  = afterImages.reentryCount,
                            lastReentryAt = formatIso8601(lastReentryAt)
                        )
                    )
                }
                visitDao.markReentrySynced(afterImages.visitId, lastReentryAt)
            } catch (e: ApiException) {
                // Tablets in the field may run against a backend that hasn't
                // shipped /reentry yet — treat 404 as transient so the row
                // stays pending instead of getting parked as permanently
                // failed.
                if (e.httpStatus == 404) {
                    throw ApiException(
                        code = ApiErrorCode.NETWORK_UNAVAILABLE,
                        message = "reentry endpoint not available yet",
                        httpStatus = 404,
                        cause = e
                    )
                }
                throw e
            }
        }

        // 5. Checkout (only if the visit was closed locally and the latest
        // close hasn't been pushed yet). Send the device-local exit time so
        // the backend records the real moment, not the sync moment.
        val beforeCheckout = visitDao.getVisitById(visit.visitId) ?: return
        if (beforeCheckout.exitDate != null && beforeCheckout.checkoutSyncedAt == null) {
            apiCall {
                api.checkout(
                    visitRemoteId,
                    CheckoutBody(checkOut = formatIso8601(beforeCheckout.exitDate))
                )
            }
            visitDao.markCheckoutSynced(beforeCheckout.visitId, System.currentTimeMillis())
        }

        // 6. Final state — only mark synced if every image is up, the
        // re-entry (if any) was pushed and the checkout (if any) was pushed.
        val finalState = visitDao.getVisitById(visit.visitId) ?: return
        if (allImagesUploaded(finalState)) {
            visitDao.markVisitSynced(finalState.visitId, visitRemoteId, System.currentTimeMillis())
        }
    }

    /**
     * True when the visit has been reopened locally and the backend hasn't
     * been informed yet. Only applies to visits that already exist remotely
     * (no remoteId → the initial POST /visits will register them as active,
     * so a /reentry call would be redundant).
     */
    private fun needsReentryPush(visit: VisitEntity): Boolean {
        if (visit.remoteId == null) return false
        val lastReentryAt = visit.lastReentryAt ?: return false
        val syncedAt = visit.reentrySyncedAt
        return syncedAt == null || syncedAt < lastReentryAt
    }

    /**
     * Ensures [person] exists in the backend. Returns the remote id either
     * from the cached column or from a fresh `POST /v1/visitors`.
     */
    private suspend fun ensurePersonSynced(person: PersonEntity): String {
        person.remoteId?.let { return it }

        val body = CreateVisitorBody(
            firstName      = person.firstName,
            lastName       = person.lastName,
            documentType   = mapDocumentType(person.documentType),
            documentNumber = person.documentNumber?.takeIf { it.isNotBlank() },
            // Email is optional. Only ship it when it's actually a valid address —
            // otherwise the backend answers 422 and the row is parked as failed
            // forever. A malformed value is simply dropped so the visit syncs.
            email          = person.email.takeIf { EmailValidator.isValid(it) },
            phone          = person.phoneNumber.takeIf { it.isNotBlank() },
            company        = person.company?.takeIf { it.isNotBlank() }
        )

        val created = apiCall { api.createVisitor(body) }
        personDao.markPersonSynced(person.personId, created.id, System.currentTimeMillis())
        return created.id
    }

    private suspend fun uploadPendingImages(visit: VisitEntity, visitRemoteId: String) {
        // Each tuple: (image type, local path on disk, "already synced?" flag)
        val targets = listOf(
            Triple("personal_photo", visit.visitProfilePhotoPath,  visit.personalPhotoSyncedAt != null),
            Triple("doc_front",      visit.visitDocumentFrontPath, visit.docFrontSyncedAt != null),
            Triple("doc_back",       visit.visitDocumentBackPath,  visit.docBackSyncedAt != null)
        )

        for ((type, path, alreadySynced) in targets) {
            if (alreadySynced) continue
            if (path.isNullOrBlank()) continue
            val file = File(path)
            if (!file.exists() || file.length() == 0L) {
                Log.w(tag, "Skipping $type for visit ${visit.visitId}: file missing on disk ($path)")
                continue
            }
            val (typePart, imagePart) = ImageUploadPayload.build(type, file)
            apiCallUnit { api.uploadVisitImage(visitRemoteId, typePart, imagePart) }
            visitDao.markVisitImageSynced(visit.visitId, type, System.currentTimeMillis())
        }
    }

    /** True when every image present locally has a non-null `*SyncedAt`. */
    private fun allImagesUploaded(visit: VisitEntity): Boolean {
        if (!visit.visitProfilePhotoPath.isNullOrBlank() && visit.personalPhotoSyncedAt == null) return false
        if (!visit.visitDocumentFrontPath.isNullOrBlank() && visit.docFrontSyncedAt == null) return false
        if (!visit.visitDocumentBackPath.isNullOrBlank() && visit.docBackSyncedAt == null) return false
        // If the visit was closed, the checkout must have been pushed too.
        if (visit.exitDate != null && visit.checkoutSyncedAt == null) return false
        // A pending re-entry means the backend still believes the visit is
        // completed — don't claim the row is synced until /reentry lands.
        if (needsReentryPush(visit)) return false
        return true
    }

    /** Best-effort safety net — uploads persons without a pending visit. */
    private suspend fun flushOrphanPersons(): Result {
        val orphans = personDao.getPendingPersons()
        if (orphans.isEmpty()) return Result.success()
        var retry = false
        for (p in orphans) {
            try {
                ensurePersonSynced(p)
            } catch (auth: AuthWipeException) {
                return Result.failure()
            } catch (e: ApiException) {
                if (e.isTransient) {
                    personDao.bumpPersonAttempt(p.personId, "${e.code}: ${e.message}")
                    retry = true
                    if (e.code == ApiErrorCode.NETWORK_UNAVAILABLE) break
                } else {
                    personDao.markPersonFailed(p.personId, "${e.code}: ${e.message}")
                }
            } catch (e: Exception) {
                personDao.bumpPersonAttempt(p.personId, e.message ?: "unknown")
                retry = true
            }
        }
        return if (retry) Result.retry() else Result.success()
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /**
     * The local UI labels documents as "DUI/ID", "Pasaporte", "Otro"; the
     * backend speaks the canonical contract identifiers below. Anything
     * unrecognised falls through to `OTHER` to keep uploads moving.
     */
    private fun mapDocumentType(local: String?): String = when (local?.trim()?.uppercase()) {
        "DUI", "ID", "DUI/ID"   -> "DUI"
        "PASSPORT", "PASAPORTE" -> "PASSPORT"
        "LICENSE", "LICENCIA"   -> "LICENSE"
        else                    -> "OTHER"
    }

    /**
     * Thin wrapper around the top-level [com.eflglobal.visitorsapp.data.remote.safeCall]
     * that converts an auth failure into our internal sentinel after wiping
     * the SecureStore. Everything else bubbles up unchanged.
     */
    private suspend fun <T : Any> apiCall(
        block: suspend () -> com.eflglobal.visitorsapp.data.remote.dto.ApiResponse<T>
    ): T = try {
        com.eflglobal.visitorsapp.data.remote.safeCall(block)
    } catch (e: ApiException) {
        if (e.isAuthFailure) {
            SecureStore.clearStation(ctx)
            throw AuthWipeException(e)
        }
        throw e
    }

    /** [apiCall] for void endpoints (ApiResponse<Unit>). */
    private suspend fun apiCallUnit(
        block: suspend () -> com.eflglobal.visitorsapp.data.remote.dto.ApiResponse<Unit>
    ) = try {
        com.eflglobal.visitorsapp.data.remote.safeCallUnit(block)
    } catch (e: ApiException) {
        if (e.isAuthFailure) {
            SecureStore.clearStation(ctx)
            throw AuthWipeException(e)
        }
        throw e
    }

    /** Internal sentinel — never escapes the worker. */
    private class AuthWipeException(cause: ApiException) : RuntimeException(cause)
}






