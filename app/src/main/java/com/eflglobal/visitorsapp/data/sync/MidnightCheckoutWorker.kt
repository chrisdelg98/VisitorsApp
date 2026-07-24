package com.eflglobal.visitorsapp.data.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.eflglobal.visitorsapp.data.local.AppDatabase
import java.util.Calendar

/**
 * Daily "close-out" pass. Visitors who never logged out would otherwise stay
 * `active` forever — this worker runs shortly after midnight and closes every
 * visit still open from a *previous* day, stamping the checkout at that day's
 * end so the record reads as closed on the day it happened (not at sync time).
 *
 * Local-first: the row is closed and flipped back to `pending` so the normal
 * [SyncWorker] pushes the checkout to the backend whenever connectivity is
 * available. We deliberately do NOT constrain the worker on network — the
 * close must happen at midnight regardless; the resync follows when online.
 *
 * Scheduled by [MidnightCheckoutScheduler]. Failures are swallowed — a missed
 * pass is retried on the next daily cycle.
 */
class MidnightCheckoutWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val ctx = applicationContext
            val visitDao = AppDatabase.getInstance(ctx).visitDao()

            val startOfToday = startOfTodayMillis()
            val stale = visitDao.getActiveVisitsBefore(startOfToday)
            if (stale.isEmpty()) return Result.success()

            // One millisecond before today began = 23:59:59.999 of the prior day.
            val exit = startOfToday - 1
            for (visit in stale) {
                visitDao.updateExitDate(visit.visitId, exit)
                // Re-queue so the checkout PATCH reaches the backend.
                visitDao.markVisitPendingResync(visit.visitId)
            }

            Log.i(TAG, "Auto-closed ${stale.size} stale active visit(s) at midnight.")
            SyncScheduler.enqueueNow(ctx)
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "Midnight auto-checkout failed (will retry next cycle): ${e.message}")
            Result.success()
        }
    }

    private fun startOfTodayMillis(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    companion object {
        private const val TAG = "MidnightCheckout"
    }
}
