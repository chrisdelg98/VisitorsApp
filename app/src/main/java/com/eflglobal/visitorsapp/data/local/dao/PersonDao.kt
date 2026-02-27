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
    @Query("""
        SELECT * FROM persons
        WHERE firstName  LIKE '%' || :query || '%'
        OR lastName      LIKE '%' || :query || '%'
        OR documentNumber LIKE '%' || :query || '%'
        OR company        LIKE '%' || :query || '%'
        ORDER BY lastName ASC, firstName ASC
    """)
    suspend fun searchPersons(query: String): List<PersonEntity>

    @Query("""
        SELECT * FROM persons
        WHERE firstName  LIKE '%' || :query || '%'
        OR lastName      LIKE '%' || :query || '%'
        OR documentNumber LIKE '%' || :query || '%'
        ORDER BY lastName ASC, firstName ASC
    """)
    fun searchPersonsFlow(query: String): Flow<List<PersonEntity>>

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

