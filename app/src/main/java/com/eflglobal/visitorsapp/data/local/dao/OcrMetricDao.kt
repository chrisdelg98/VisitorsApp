package com.eflglobal.visitorsapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.eflglobal.visitorsapp.data.local.entity.OcrMetricEntity

@Dao
interface OcrMetricDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(metric: OcrMetricEntity): Long

    /** Total scans logged. */
    @Query("SELECT COUNT(*) FROM ocr_metrics")
    suspend fun count(): Int

    /** Average first-name confidence across all scans. */
    @Query("SELECT AVG(firstNameConfidence) FROM ocr_metrics")
    suspend fun avgFirstNameConfidence(): Float?

    /** Average last-name confidence across all scans. */
    @Query("SELECT AVG(lastNameConfidence) FROM ocr_metrics")
    suspend fun avgLastNameConfidence(): Float?

    /** Count of scans where user corrected the auto-filled first name. */
    @Query("SELECT COUNT(*) FROM ocr_metrics WHERE firstNameCorrected = 1")
    suspend fun firstNameCorrectionCount(): Int

    /** Count of scans where user corrected the auto-filled last name. */
    @Query("SELECT COUNT(*) FROM ocr_metrics WHERE lastNameCorrected = 1")
    suspend fun lastNameCorrectionCount(): Int

    /** Per-country breakdown — how many scans per detected country. */
    @Query("SELECT detectedCountry, COUNT(*) as cnt FROM ocr_metrics GROUP BY detectedCountry ORDER BY cnt DESC")
    suspend fun countryBreakdown(): List<CountryCount>

    /** Most recent N metrics for debugging. */
    @Query("SELECT * FROM ocr_metrics ORDER BY timestamp DESC LIMIT :limit")
    suspend fun recentMetrics(limit: Int = 50): List<OcrMetricEntity>

    /** Delete metrics older than [beforeTimestamp] to manage storage. */
    @Query("DELETE FROM ocr_metrics WHERE timestamp < :beforeTimestamp")
    suspend fun deleteOlderThan(beforeTimestamp: Long)

    data class CountryCount(val detectedCountry: String, val cnt: Int)
}

