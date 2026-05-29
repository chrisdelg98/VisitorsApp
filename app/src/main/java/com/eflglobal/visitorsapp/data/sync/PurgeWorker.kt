package com.eflglobal.visitorsapp.data.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.eflglobal.visitorsapp.data.local.AppDatabase
import com.eflglobal.visitorsapp.domain.usecase.visit.ResolveVisitorPhotoUseCase
import java.io.File
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Phase 8 — retention worker.
 *
 *  - Drops fully-synced visits whose `entryDate` is older than the retention
 *    window (10 days). Synced data is safe in the backend; recurrent search
 *    goes through the API so the tablet only needs a short local cache.
 *  - Drops `pending`/`failed` visits older than 30 days as a hard ceiling
 *    against rows that can never sync (corrupt data, station revoked, …).
 *  - Cleans orphan files under `filesDir/visits/` whose visit id is no
 *    longer present in Room.
 *  - Trims the `photo_cache/` directory (downloaded profile photos) of files
 *    older than the retention window.
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
            val retentionCutoff = startOfToday - TimeUnit.DAYS.toMillis(RETENTION_VISITS_DAYS)
            val staleCutoff     = startOfToday - TimeUnit.DAYS.toMillis(STALE_PENDING_DAYS)

            val syncedRemoved = visitDao.deleteSyncedOlderThan(retentionCutoff)
            val staleRemoved  = visitDao.deletePendingOlderThan(staleCutoff)

            Log.i(TAG, "Purge done — synced=$syncedRemoved staleOldPending=$staleRemoved")

            cleanOrphanFiles(ctx, visitDao.getAllVisitIds().toSet())
            cleanPhotoCache(ctx, retentionCutoff)

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

    /**
     * Removes downloaded profile photos older than [cutoffMillis]. These live
     * in a dedicated dir so the orphan sweep above never touches them; here we
     * apply a simple age-based retention so the cache cannot grow unbounded.
     */
    private fun cleanPhotoCache(ctx: Context, cutoffMillis: Long) {
        val dir = File(ctx.filesDir, ResolveVisitorPhotoUseCase.CACHE_DIR)
        if (!dir.isDirectory) return

        val children = dir.listFiles() ?: return
        var removed = 0
        for (child in children) {
            if (child.isFile && child.lastModified() < cutoffMillis) {
                if (child.delete()) removed++
            }
        }
        if (removed > 0) Log.i(TAG, "Removed $removed stale cached photos from disk.")
    }

    private fun startOfTodayMillis(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    companion object {
        private const val TAG = "PurgeWorker"
        /** Keep synced visits (and cached photos) for this many days locally. */
        private const val RETENTION_VISITS_DAYS = 10L
        /** Hard ceiling for visits that can never sync. */
        private const val STALE_PENDING_DAYS = 30L
    }
}

