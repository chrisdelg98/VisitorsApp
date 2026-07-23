package com.eflglobal.visitorsapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.eflglobal.visitorsapp.data.local.entity.DocumentTemplateEntity

@Dao
interface DocumentTemplateDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(templates: List<DocumentTemplateEntity>)

    @Query("DELETE FROM document_templates")
    suspend fun deleteAll()

    /**
     * Atomically swaps the entire local catalog for [templates] — the endpoint
     * returns a complete snapshot, so we wipe-then-insert in one transaction to
     * avoid ever exposing a half-written catalog to the pipeline.
     */
    @Transaction
    suspend fun replaceAll(templates: List<DocumentTemplateEntity>) {
        deleteAll()
        if (templates.isNotEmpty()) insertAll(templates)
    }

    @Query("SELECT * FROM document_templates")
    suspend fun getAll(): List<DocumentTemplateEntity>

    @Query("SELECT * FROM document_templates WHERE side = :side")
    suspend fun getBySide(side: String): List<DocumentTemplateEntity>

    @Query("SELECT * FROM document_templates WHERE countryId = :countryId")
    suspend fun getByCountry(countryId: String): List<DocumentTemplateEntity>

    @Query("SELECT COUNT(*) FROM document_templates")
    suspend fun count(): Int
}
