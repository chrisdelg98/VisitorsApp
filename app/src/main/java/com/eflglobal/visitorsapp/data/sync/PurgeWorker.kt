package com.eflglobal.visitorsapp.data.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.eflglobal.visitorsapp.data.local.AppDatabase
import java.io.File
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Phase 8 — retention worker.
 *
 *  - Drops fully-synced visits whose `entryDate` is older than today 00:00
 *    (synced data is already safe in the backend, the tablet does not need
 *    a permanent local copy).
 *  - Drops `pending`/`failed` visits older than 30 days as a hard ceiling
 *    against rows that can never sync (corrupt data, station revoked, …).
 *  - Cleans orphan files under `filesDir/visits/` whose visit id is no
 *    longer present in Room.
 *
 * Scheduled by [PurgeScheduler.schedule] right after app start. Failures
 * are swallowed — purge is best-effort and must never break the app.
 */
class PurgeWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val ctx = applicationContext
            val db = AppDatabase.getInstance(ctx)
            val visitDao = db.visitDao()

            val startOfToday = startOfTodayMillis()
            val thirtyDaysAgo = startOfToday - TimeUnit.DAYS.toMillis(RETENTION_DAYS)

            val syncedRemoved = visitDao.deleteSyncedOlderThan(startOfToday)
            val staleRemoved  = visitDao.deletePendingOlderThan(thirtyDaysAgo)

            Log.i(TAG, "Purge done — synced=$syncedRemoved staleOldPending=$staleRemoved")

            cleanOrphanFiles(ctx, visitDao.getAllVisitIds().toSet())

            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "Purge pass failed (will retry next cycle): ${e.message}")
            Result.success() // never fail the chain; we'll get the next run anyway.
        }
    }

    private fun cleanOrphanFiles(ctx: Context, activeIds: Set<String>) {
        val root = File(ctx.filesDir, "visits")
        if (!root.isDirectory) return

        val children = root.listFiles() ?: return
        var removed = 0
        for (child in children) {
            val id = if (child.isDirectory) {
                child.name // remote-image cache folder
            } else {
                child.nameWithoutExtension // QR png / one-off image
            }
            if (id !in activeIds) {
                if (child.deleteRecursively()) removed++
            }
        }
        if (removed > 0) Log.i(TAG, "Removed $removed orphan image entries from disk.")
    }

    private fun startOfTodayMillis(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    companion object {
        private const val TAG = "PurgeWorker"
        private const val RETENTION_DAYS = 30L
    }
}

