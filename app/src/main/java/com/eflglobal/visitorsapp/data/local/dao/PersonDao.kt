package com.eflglobal.visitorsapp.data.local.dao

import androidx.room.*
import com.eflglobal.visitorsapp.data.local.entity.PersonEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonDao {

    // ===== INSERT =====
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPerson(person: PersonEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPersons(persons: List<PersonEntity>)

    // ===== UPDATE =====
    @Update
    suspend fun updatePerson(person: PersonEntity)

    @Query("UPDATE persons SET isSynced = :isSynced, lastSyncAt = :syncTime WHERE personId = :personId")
    suspend fun updateSyncStatus(personId: String, isSynced: Boolean, syncTime: Long)

    // ── Phase 3: per-row sync tracking against the backend ───────────────────

    /** FIFO list of persons waiting to be uploaded by the SyncWorker. */
    @Query("SELECT * FROM persons WHERE syncStatus = 'pending' ORDER BY createdAt ASC")
    suspend fun getPendingPersons(): List<PersonEntity>

    @Query("SELECT COUNT(*) FROM persons WHERE syncStatus = 'pending' OR syncStatus = 'failed'")
    suspend fun getUnsyncedPersonsCount(): Int

    /** Marks a person as successfully synced and stores the backend-assigned UUID. */
    @Query("""
        UPDATE persons
           SET remoteId = :remoteId,
               syncStatus = 'synced',
               lastSyncAt = :syncedAt,
               isSynced = 1,
               lastSyncError = NULL
         WHERE personId = :personId
    """)
    suspend fun markPersonSynced(personId: String, remoteId: String, syncedAt: Long)

    /** Records a non-retryable failure (4xx) so it surfaces in the admin panel. */
    @Query("""
        UPDATE persons
           SET syncStatus = 'failed',
               syncAttempts = syncAttempts + 1,
               lastSyncError = :error
         WHERE personId = :personId
    """)
    suspend fun markPersonFailed(personId: String, error: String)

    /** Bumps the attempt counter for a transient failure (network / 5xx / 429). */
    @Query("""
        UPDATE persons
           SET syncAttempts = syncAttempts + 1,
               lastSyncError = :error
         WHERE personId = :personId
    """)
    suspend fun bumpPersonAttempt(personId: String, error: String)

    // ===== DELETE =====
    @Delete
    suspend fun deletePerson(person: PersonEntity)

    @Query("DELETE FROM persons WHERE personId = :personId")
    suspend fun deletePersonById(personId: String)

    // ===== QUERY - Individual =====
    @Query("SELECT * FROM persons WHERE personId = :personId")
    suspend fun getPersonById(personId: String): PersonEntity?

    @Query("SELECT * FROM persons WHERE personId = :personId")
    fun getPersonByIdFlow(personId: String): Flow<PersonEntity?>

    @Query("SELECT * FROM persons WHERE documentNumber = :documentNumber LIMIT 1")
    suspend fun getPersonByDocumentNumber(documentNumber: String): PersonEntity?

    // ===== QUERY - Búsqueda =====
    /**
     * Busca personas que hayan visitado en los últimos 3 meses.
     * Devuelve 1 registro por persona, el más reciente.
     */
    @Query("""
        SELECT DISTINCT p.* FROM persons p
        INNER JOIN visits v ON p.personId = v.personId
        WHERE (p.firstName  LIKE '%' || :query || '%'
            OR p.lastName      LIKE '%' || :query || '%'
            OR p.documentNumber LIKE '%' || :query || '%'
            OR p.company        LIKE '%' || :query || '%')
        AND v.entryDate >= :threeMonthsAgoTimestamp
        GROUP BY p.personId
        ORDER BY MAX(v.entryDate) DESC
    """)
    suspend fun searchPersons(query: String, threeMonthsAgoTimestamp: Long): List<PersonEntity>

    @Query("""
        SELECT DISTINCT p.* FROM persons p
        INNER JOIN visits v ON p.personId = v.personId
        WHERE (p.firstName  LIKE '%' || :query || '%'
            OR p.lastName      LIKE '%' || :query || '%'
            OR p.documentNumber LIKE '%' || :query || '%')
        AND v.entryDate >= :threeMonthsAgoTimestamp
        GROUP BY p.personId
        ORDER BY MAX(v.entryDate) DESC
    """)
    fun searchPersonsFlow(query: String, threeMonthsAgoTimestamp: Long): Flow<List<PersonEntity>>

    // ===== QUERY - Listas =====
    @Query("SELECT * FROM persons ORDER BY createdAt DESC")
    suspend fun getAllPersons(): List<PersonEntity>

    @Query("SELECT * FROM persons ORDER BY createdAt DESC")
    fun getAllPersonsFlow(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM persons ORDER BY lastName ASC, firstName ASC LIMIT :limit")
    suspend fun getRecentPersons(limit: Int = 50): List<PersonEntity>

    // ===== QUERY - Sincronización =====
    @Query("SELECT * FROM persons WHERE isSynced = 0 ORDER BY createdAt ASC")
    suspend fun getUnsyncedPersons(): List<PersonEntity>

    @Query("SELECT COUNT(*) FROM persons WHERE isSynced = 0")
    suspend fun getUnsyncedCount(): Int

    @Query("SELECT COUNT(*) FROM persons WHERE isSynced = 0")
    fun getUnsyncedCountFlow(): Flow<Int>

    // ===== QUERY - Estadísticas =====
    @Query("SELECT COUNT(*) FROM persons")
    suspend fun getTotalPersonsCount(): Int

    @Query("SELECT COUNT(*) FROM persons")
    fun getTotalPersonsCountFlow(): Flow<Int>

    // ===== UTILITY =====
    @Query("DELETE FROM persons")
    suspend fun deleteAllPersons()
}

