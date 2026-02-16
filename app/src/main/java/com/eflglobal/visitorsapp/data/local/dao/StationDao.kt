package com.eflglobal.visitorsapp.data.local.dao

import androidx.room.*
import com.eflglobal.visitorsapp.data.local.entity.StationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StationDao {

    // ===== INSERT =====
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStation(station: StationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStations(stations: List<StationEntity>)

    // ===== UPDATE =====
    @Update
    suspend fun updateStation(station: StationEntity)

    @Query("UPDATE stations SET isActive = 0")
    suspend fun deactivateAllStations()

    @Query("UPDATE stations SET isActive = 1, activatedAt = :activatedAt WHERE stationId = :stationId")
    suspend fun activateStation(stationId: String, activatedAt: Long)

    @Query("UPDATE stations SET isSynced = :isSynced, lastSyncAt = :syncTime WHERE stationId = :stationId")
    suspend fun updateSyncStatus(stationId: String, isSynced: Boolean, syncTime: Long)

    // ===== DELETE =====
    @Delete
    suspend fun deleteStation(station: StationEntity)

    @Query("DELETE FROM stations WHERE stationId = :stationId")
    suspend fun deleteStationById(stationId: String)

    // ===== QUERY - Individual =====
    @Query("SELECT * FROM stations WHERE stationId = :stationId")
    suspend fun getStationById(stationId: String): StationEntity?

    @Query("SELECT * FROM stations WHERE stationId = :stationId")
    fun getStationByIdFlow(stationId: String): Flow<StationEntity?>

    @Query("SELECT * FROM stations WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveStation(): StationEntity?

    @Query("SELECT * FROM stations WHERE isActive = 1 LIMIT 1")
    fun getActiveStationFlow(): Flow<StationEntity?>

    // ===== QUERY - Validación =====
    @Query("SELECT * FROM stations WHERE pin = :pin AND isActive = 1 LIMIT 1")
    suspend fun getStationByPin(pin: String): StationEntity?

    @Query("SELECT COUNT(*) FROM stations WHERE isActive = 1")
    suspend fun getActiveStationCount(): Int

    // ===== QUERY - Listas =====
    @Query("SELECT * FROM stations ORDER BY createdAt DESC")
    suspend fun getAllStations(): List<StationEntity>

    @Query("SELECT * FROM stations ORDER BY createdAt DESC")
    fun getAllStationsFlow(): Flow<List<StationEntity>>

    @Query("SELECT * FROM stations WHERE isActive = 1")
    suspend fun getActiveStations(): List<StationEntity>

    // ===== QUERY - Sincronización =====
    @Query("SELECT * FROM stations WHERE isSynced = 0 ORDER BY createdAt ASC")
    suspend fun getUnsyncedStations(): List<StationEntity>

    @Query("SELECT COUNT(*) FROM stations WHERE isSynced = 0")
    suspend fun getUnsyncedCount(): Int

    @Query("SELECT COUNT(*) FROM stations WHERE isSynced = 0")
    fun getUnsyncedCountFlow(): Flow<Int>

    // ===== QUERY - Estadísticas =====
    @Query("SELECT COUNT(*) FROM stations")
    suspend fun getTotalStationsCount(): Int

    @Query("SELECT COUNT(*) FROM stations")
    fun getTotalStationsCountFlow(): Flow<Int>

    // ===== UTILITY =====
    @Query("DELETE FROM stations")
    suspend fun deleteAllStations()

    // Transacción para activar una estación desactivando las demás
    @Transaction
    suspend fun setActiveStation(stationId: String, activatedAt: Long) {
        deactivateAllStations()
        activateStation(stationId, activatedAt)
    }
}

