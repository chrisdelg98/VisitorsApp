package com.eflglobal.visitorsapp.data.local.dao

import androidx.room.*
import com.eflglobal.visitorsapp.data.local.entity.VisitReasonEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VisitReasonDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertReasons(reasons: List<VisitReasonEntity>)

    @Query("SELECT * FROM visit_reasons WHERE isActive = 1 ORDER BY sortOrder ASC")
    suspend fun getAllActiveReasons(): List<VisitReasonEntity>

    @Query("SELECT * FROM visit_reasons WHERE isActive = 1 ORDER BY sortOrder ASC")
    fun getAllActiveReasonsFlow(): Flow<List<VisitReasonEntity>>

    @Query("SELECT * FROM visit_reasons WHERE reasonKey = :key LIMIT 1")
    suspend fun getReasonByKey(key: String): VisitReasonEntity?

    @Query("SELECT COUNT(*) FROM visit_reasons")
    suspend fun count(): Int
}

