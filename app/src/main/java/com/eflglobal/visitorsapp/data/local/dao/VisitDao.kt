package com.eflglobal.visitorsapp.data.local.dao

import androidx.room.*
import com.eflglobal.visitorsapp.data.local.entity.VisitEntity
import kotlinx.coroutines.flow.Flow

/** Simple projection for the reason-count aggregate query. */
data class ReasonCount(
    val visitReason: String,
    val cnt: Int
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
}

