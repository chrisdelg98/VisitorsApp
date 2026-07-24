package com.eflglobal.visitorsapp.data.local.dao

import androidx.room.*
import com.eflglobal.visitorsapp.data.local.entity.VisitEntity
import kotlinx.coroutines.flow.Flow

/** Simple projection for the reason-count aggregate query. */
data class ReasonCount(
    val visitReason: String,
    val cnt: Int
)

/** Projection for the admin-panel sync-status list (pending + failed rows). */
data class UnsyncedVisitDto(
    val visitId: String,
    val syncStatus: String,
    val syncAttempts: Int,
    val lastSyncError: String?,
    val createdAt: Long,
    val firstName: String?,
    val lastName: String?
)

/** Visit with person information for display */
data class VisitWithPersonDto(
    val visitId: String,
    val personId: String,
    val stationId: String?,
    val visitingPersonName: String,
    val visitorType: String,
    val visitReason: String,
    val visitReasonCustom: String?,
    val entryDate: Long,
    val exitDate: Long?,
    val qrCodeValue: String,
    val isSynced: Boolean,
    val lastSyncAt: Long?,
    // Person reference data
    val personFirstName: String,
    val personLastName: String,
    val personCompany: String?,
    val personProfilePhotoPath: String?,
    val personDocumentFrontPath: String?,
    val personDocumentBackPath: String?,
    // Visit-specific audit snapshots (nullable — null for pre-v5 records)
    val visitProfilePhotoPath: String?,
    val visitDocumentFrontPath: String?,
    val visitDocumentBackPath: String?
)

@Dao
interface VisitDao {

    // ===== INSERT =====
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVisit(visit: VisitEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVisits(visits: List<VisitEntity>)

    // ===== UPDATE =====
    @Update
    suspend fun updateVisit(visit: VisitEntity)

    @Query("UPDATE visits SET exitDate = :exitDate WHERE visitId = :visitId")
    suspend fun updateExitDate(visitId: String, exitDate: Long)

    @Query("UPDATE visits SET isSynced = :isSynced, lastSyncAt = :syncTime WHERE visitId = :visitId")
    suspend fun updateSyncStatus(visitId: String, isSynced: Boolean, syncTime: Long)

    /**
     * Same-station re-entry: increments reentryCount, records timestamp,
     * clears exitDate so the visit becomes active again, and also clears
     * checkoutSyncedAt so the SyncWorker re-PATCHes the final checkout once
     * the visitor leaves for real.
     */
    @Query("UPDATE visits SET reentryCount = reentryCount + 1, lastReentryAt = :reentryAt, exitDate = NULL, checkoutSyncedAt = NULL WHERE visitId = :visitId")
    suspend fun registerReentry(visitId: String, reentryAt: Long)

    // ===== DELETE =====
    @Delete
    suspend fun deleteVisit(visit: VisitEntity)

    @Query("DELETE FROM visits WHERE visitId = :visitId")
    suspend fun deleteVisitById(visitId: String)

    // ===== QUERY - Individual =====
    @Query("SELECT * FROM visits WHERE visitId = :visitId")
    suspend fun getVisitById(visitId: String): VisitEntity?

    @Query("SELECT * FROM visits WHERE visitId = :visitId")
    fun getVisitByIdFlow(visitId: String): Flow<VisitEntity?>

    @Query("SELECT * FROM visits WHERE qrCodeValue = :qrCode LIMIT 1")
    suspend fun getVisitByQRCode(qrCode: String): VisitEntity?

    // ===== QUERY - Por Persona =====
    @Query("SELECT * FROM visits WHERE personId = :personId ORDER BY entryDate DESC")
    suspend fun getVisitsByPersonId(personId: String): List<VisitEntity>

    @Query("SELECT * FROM visits WHERE personId = :personId ORDER BY entryDate DESC")
    fun getVisitsByPersonIdFlow(personId: String): Flow<List<VisitEntity>>

    @Query("SELECT * FROM visits WHERE personId = :personId ORDER BY entryDate DESC LIMIT 1")
    suspend fun getLastVisitByPersonId(personId: String): VisitEntity?

    // ===== QUERY - Visitas Activas (sin salida) =====
    @Query("SELECT * FROM visits WHERE exitDate IS NULL ORDER BY entryDate DESC")
    suspend fun getActiveVisits(): List<VisitEntity>

    @Query("SELECT * FROM visits WHERE exitDate IS NULL ORDER BY entryDate DESC")
    fun getActiveVisitsFlow(): Flow<List<VisitEntity>>

    @Query("SELECT COUNT(*) FROM visits WHERE exitDate IS NULL")
    suspend fun getActiveVisitsCount(): Int

    @Query("SELECT COUNT(*) FROM visits WHERE exitDate IS NULL")
    fun getActiveVisitsCountFlow(): Flow<Int>

    @Query("""
        SELECT v.* FROM visits v
        INNER JOIN persons p ON v.personId = p.personId
        WHERE v.exitDate IS NULL 
        AND (
            p.firstName  LIKE '%' || :query || '%'
            OR p.lastName   LIKE '%' || :query || '%'
            OR v.visitingPersonName LIKE '%' || :query || '%'
        )
        ORDER BY v.entryDate DESC
    """)
    suspend fun searchActiveVisits(query: String): List<VisitEntity>

    // ===== QUERY - Por razón de visita =====
    @Query("SELECT * FROM visits WHERE visitReason = :reason ORDER BY entryDate DESC")
    suspend fun getVisitsByReason(reason: String): List<VisitEntity>

    @Query("SELECT * FROM visits WHERE visitReason = :reason ORDER BY entryDate DESC")
    fun getVisitsByReasonFlow(reason: String): Flow<List<VisitEntity>>

    /** Returns all OTHER visits including their custom description. */
    @Query("SELECT * FROM visits WHERE visitReason = 'OTHER' ORDER BY entryDate DESC")
    suspend fun getOtherVisits(): List<VisitEntity>

    /** Count visits grouped by reason (for statistics / dashboard). */
    @Query("SELECT visitReason, COUNT(*) as cnt FROM visits GROUP BY visitReason")
    suspend fun countVisitsByReason(): List<ReasonCount>

    /** Active visits filtered by reason. */
    @Query("""
        SELECT * FROM visits 
        WHERE exitDate IS NULL AND visitReason = :reason 
        ORDER BY entryDate DESC
    """)
    suspend fun getActiveVisitsByReason(reason: String): List<VisitEntity>

    // ===== QUERY - Por Fecha =====

    @Query("""
        SELECT * FROM visits 
        WHERE entryDate >= :startDate AND entryDate <= :endDate
        ORDER BY entryDate DESC
    """)
    suspend fun getVisitsByDateRange(startDate: Long, endDate: Long): List<VisitEntity>

    @Query("""
        SELECT * FROM visits 
        WHERE entryDate >= :startDate AND entryDate <= :endDate
        ORDER BY entryDate DESC
    """)
    fun getVisitsByDateRangeFlow(startDate: Long, endDate: Long): Flow<List<VisitEntity>>

    // Visitas del día actual
    @Query("""
        SELECT * FROM visits 
        WHERE entryDate >= :startOfDay AND entryDate <= :endOfDay
        ORDER BY entryDate DESC
    """)
    suspend fun getTodayVisits(startOfDay: Long, endOfDay: Long): List<VisitEntity>

    @Query("""
        SELECT * FROM visits 
        WHERE entryDate >= :startOfDay AND entryDate <= :endOfDay
        ORDER BY entryDate DESC
    """)
    fun getTodayVisitsFlow(startOfDay: Long, endOfDay: Long): Flow<List<VisitEntity>>

    // Visitas activas del día (para checkout manual)
    @Query("""
        SELECT * FROM visits 
        WHERE entryDate >= :startOfDay 
        AND entryDate <= :endOfDay
        AND exitDate IS NULL
        ORDER BY entryDate DESC
    """)
    suspend fun getTodayActiveVisits(startOfDay: Long, endOfDay: Long): List<VisitEntity>

    // ===== QUERY - Por Estación =====
    @Query("SELECT * FROM visits WHERE stationId = :stationId ORDER BY entryDate DESC")
    suspend fun getVisitsByStationId(stationId: String): List<VisitEntity>

    @Query("""
        SELECT * FROM visits 
        WHERE stationId = :stationId 
        AND entryDate >= :startDate 
        AND entryDate <= :endDate
        ORDER BY entryDate DESC
    """)
    suspend fun getVisitsByStationAndDateRange(
        stationId: String,
        startDate: Long,
        endDate: Long
    ): List<VisitEntity>

    // ===== QUERY - Por Estación con JOIN =====
    @Query("""
        SELECT 
            v.visitId, v.personId, v.stationId, v.visitingPersonName,
            v.visitorType, v.visitReason, v.visitReasonCustom,
            v.entryDate, v.exitDate, v.qrCodeValue, v.isSynced, v.lastSyncAt,
            v.visitProfilePhotoPath  AS visitProfilePhotoPath,
            v.visitDocumentFrontPath AS visitDocumentFrontPath,
            v.visitDocumentBackPath  AS visitDocumentBackPath,
            p.firstName  AS personFirstName,
            p.lastName   AS personLastName,
            p.company    AS personCompany,
            p.profilePhotoPath   AS personProfilePhotoPath,
            p.documentFrontPath  AS personDocumentFrontPath,
            p.documentBackPath   AS personDocumentBackPath
        FROM visits v
        INNER JOIN persons p ON v.personId = p.personId
        WHERE v.stationId = :stationId 
        ORDER BY v.entryDate DESC
    """)
    suspend fun getVisitsWithPersonInfoByStationId(stationId: String): List<VisitWithPersonDto>

    // ===== QUERY - Todas las visitas =====
    @Query("SELECT * FROM visits ORDER BY entryDate DESC")
    suspend fun getAllVisits(): List<VisitEntity>

    @Query("SELECT * FROM visits ORDER BY entryDate DESC")
    fun getAllVisitsFlow(): Flow<List<VisitEntity>>

    @Query("SELECT * FROM visits ORDER BY entryDate DESC LIMIT :limit")
    suspend fun getRecentVisits(limit: Int = 100): List<VisitEntity>

    // ===== QUERY - Sincronización =====
    @Query("SELECT * FROM visits WHERE isSynced = 0 ORDER BY createdAt ASC")
    suspend fun getUnsyncedVisits(): List<VisitEntity>

    @Query("SELECT COUNT(*) FROM visits WHERE isSynced = 0")
    suspend fun getUnsyncedCount(): Int

    @Query("SELECT COUNT(*) FROM visits WHERE isSynced = 0")
    fun getUnsyncedCountFlow(): Flow<Int>

    // ── Phase 3: per-row sync tracking against the backend ───────────────────

    /**
     * FIFO list of visits the SyncWorker should attempt to upload.
     *
     * Includes `failed` rows (not just `pending`): a row parked as `failed`
     * after a server outage or a since-corrected validation error would
     * otherwise never be retried — it would sit in the badge forever. Re-driving
     * them each pass is cheap; genuinely-bad rows simply fail again and stay put.
     */
    @Query("SELECT * FROM visits WHERE syncStatus IN ('pending', 'failed') ORDER BY createdAt ASC")
    suspend fun getPendingVisits(): List<VisitEntity>

    /**
     * Detailed view of everything not yet synced, for the admin panel. Surfaces
     * the per-row error so reception can see *why* a row is stuck, not just how
     * many are. Joined to persons so the row is identifiable by name.
     */
    @Query("""
        SELECT v.visitId       AS visitId,
               v.syncStatus     AS syncStatus,
               v.syncAttempts   AS syncAttempts,
               v.lastSyncError  AS lastSyncError,
               v.createdAt      AS createdAt,
               p.firstName      AS firstName,
               p.lastName       AS lastName
          FROM visits v
          LEFT JOIN persons p ON v.personId = p.personId
         WHERE v.syncStatus IN ('pending', 'failed')
         ORDER BY v.createdAt ASC
    """)
    fun getUnsyncedVisitDetailsFlow(): Flow<List<UnsyncedVisitDto>>

    @Query("SELECT COUNT(*) FROM visits WHERE syncStatus = 'pending' OR syncStatus = 'failed'")
    suspend fun getPendingVisitsCount(): Int

    @Query("SELECT COUNT(*) FROM visits WHERE syncStatus = 'pending' OR syncStatus = 'failed'")
    fun getPendingVisitsCountFlow(): Flow<Int>

    /** Marks a visit row as synced and stores the backend-assigned UUID. */
    @Query("""
        UPDATE visits
           SET remoteId = :remoteId,
               syncStatus = 'synced',
               lastSyncAt = :syncedAt,
               isSynced = 1,
               lastSyncError = NULL
         WHERE visitId = :visitId
    """)
    suspend fun markVisitSynced(visitId: String, remoteId: String, syncedAt: Long)

    /** Permanent failure (4xx). Surfaces in admin panel for manual handling. */
    @Query("""
        UPDATE visits
           SET syncStatus = 'failed',
               syncAttempts = syncAttempts + 1,
               lastSyncError = :error
         WHERE visitId = :visitId
    """)
    suspend fun markVisitFailed(visitId: String, error: String)

    /** Transient failure — keeps row as pending for the next worker run. */
    @Query("""
        UPDATE visits
           SET syncAttempts = syncAttempts + 1,
               lastSyncError = :error
         WHERE visitId = :visitId
    """)
    suspend fun bumpVisitAttempt(visitId: String, error: String)

    /**
     * Marks a single image upload as confirmed. `imageType` is one of
     * `personal_photo`, `doc_front`, `doc_back`. Implemented with CASE so we
     * can keep a single typed entry-point instead of three near-identical DAO
     * methods.
     */
    @Query("""
        UPDATE visits
           SET personalPhotoSyncedAt = CASE WHEN :imageType = 'personal_photo' THEN :syncedAt ELSE personalPhotoSyncedAt END,
               docFrontSyncedAt      = CASE WHEN :imageType = 'doc_front'      THEN :syncedAt ELSE docFrontSyncedAt END,
               docBackSyncedAt       = CASE WHEN :imageType = 'doc_back'       THEN :syncedAt ELSE docBackSyncedAt END
         WHERE visitId = :visitId
    """)
    suspend fun markVisitImageSynced(visitId: String, imageType: String, syncedAt: Long)

    /** Marks the checkout PATCH as confirmed by the backend. */
    @Query("UPDATE visits SET checkoutSyncedAt = :syncedAt WHERE visitId = :visitId")
    suspend fun markCheckoutSynced(visitId: String, syncedAt: Long)

    /**
     * Marks the PATCH /reentry as confirmed. [syncedAt] should be the
     * `lastReentryAt` that was pushed so a concurrent re-entry on the device
     * (which would bump `lastReentryAt` further) still triggers a resync.
     */
    @Query("UPDATE visits SET reentrySyncedAt = :syncedAt WHERE visitId = :visitId")
    suspend fun markReentrySynced(visitId: String, syncedAt: Long)

    /**
     * Returns a previously-synced visit back to the `pending` queue so the
     * SyncWorker re-evaluates it. Used after a local checkout or a same-station
     * re-entry: the row already has a `remoteId`, but the new local state
     * (exitDate / reentryCount) still needs to be propagated to the backend.
     */
    @Query("UPDATE visits SET syncStatus = 'pending', lastSyncError = NULL WHERE visitId = :visitId")
    suspend fun markVisitPendingResync(visitId: String)

    // ── Server → local reconciliation ────────────────────────────────────────

    /**
     * Locally-active visits that are already `synced` and carry a backend
     * `remoteId`. These are the candidates the "pull" reconciliation checks
     * against the server's active list: if one is no longer active on the
     * server, reception closed it from the portal and we mirror that locally.
     * Restricting to `synced` rows means a local checkout still waiting to
     * upload is never clobbered.
     */
    @Query("SELECT * FROM visits WHERE exitDate IS NULL AND remoteId IS NOT NULL AND syncStatus = 'synced'")
    suspend fun getActiveSyncedVisits(): List<VisitEntity>

    /** Lookup by backend id — used to map a server visit back to its local row. */
    @Query("SELECT * FROM visits WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getVisitByRemoteId(remoteId: String): VisitEntity?

    /**
     * Applies a checkout that happened on the SERVER (reception closed the
     * visit from the portal). Closes the row locally and marks it fully synced
     * so the SyncWorker does NOT try to PATCH the checkout back — the backend
     * is already the source of truth here.
     */
    @Query("""
        UPDATE visits
           SET exitDate = :exitDate,
               checkoutSyncedAt = :syncedAt,
               syncStatus = 'synced',
               isSynced = 1,
               lastSyncError = NULL
         WHERE visitId = :visitId
    """)
    suspend fun applyRemoteCheckout(visitId: String, exitDate: Long, syncedAt: Long)

    /**
     * Re-opens a row the SERVER now reports as active again. Kept `synced`
     * so the change is reflected locally without being pushed back.
     */
    @Query("""
        UPDATE visits
           SET exitDate = NULL,
               checkoutSyncedAt = NULL,
               syncStatus = 'synced',
               isSynced = 1
         WHERE visitId = :visitId
    """)
    suspend fun applyRemoteReopen(visitId: String)

    /**
     * Still-open visits whose entry is before [beforeTimestamp]. Used by the
     * midnight auto-checkout to close visitors who never logged out on a
     * previous day.
     */
    @Query("SELECT * FROM visits WHERE exitDate IS NULL AND entryDate < :beforeTimestamp")
    suspend fun getActiveVisitsBefore(beforeTimestamp: Long): List<VisitEntity>

    // ===== QUERY - Estadísticas =====
    @Query("SELECT COUNT(*) FROM visits")
    suspend fun getTotalVisitsCount(): Int

    @Query("SELECT COUNT(*) FROM visits")
    fun getTotalVisitsCountFlow(): Flow<Int>

    @Query("""
        SELECT COUNT(*) FROM visits 
        WHERE entryDate >= :startDate AND entryDate <= :endDate
    """)
    suspend fun getVisitsCountByDateRange(startDate: Long, endDate: Long): Int

    @Query("SELECT COUNT(DISTINCT personId) FROM visits")
    suspend fun getUniqueVisitorsCount(): Int

    // ===== UTILITY =====
    @Query("DELETE FROM visits")
    suspend fun deleteAllVisits()

    @Query("DELETE FROM visits WHERE exitDate IS NOT NULL AND entryDate < :beforeDate")
    suspend fun deleteOldCompletedVisits(beforeDate: Long)

    // ===== PHASE 8 — RETENTION =====
    /** Drop fully-synced visits older than [beforeTimestamp] (entry date). */
    @Query("DELETE FROM visits WHERE syncStatus = 'synced' AND entryDate < :beforeTimestamp")
    suspend fun deleteSyncedOlderThan(beforeTimestamp: Long): Int

    /** Drop visits still pending/failed beyond the retention window. */
    @Query("DELETE FROM visits WHERE syncStatus IN ('pending', 'failed') AND entryDate < :beforeTimestamp")
    suspend fun deletePendingOlderThan(beforeTimestamp: Long): Int

    /** Active local visit ids — used to detect orphan image folders. */
    @Query("SELECT visitId FROM visits")
    suspend fun getAllVisitIds(): List<String>
}
