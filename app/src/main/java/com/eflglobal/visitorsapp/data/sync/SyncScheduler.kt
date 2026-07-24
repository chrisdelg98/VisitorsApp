package com.eflglobal.visitorsapp.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit

/**
 * Public entry-point to schedule the [SyncWorker].
 *
 *  - [enqueueNow] is a genuine "sync now": it REPLACEs any pass currently
 *    sitting in backoff and asks WorkManager to expedite it, so a manual tap
 *    (or `onResume`) runs immediately instead of waiting out the retry timer.
 *
 *  - [schedulePeriodic] is called once on app start as a safety net for
 *    devices that recover connectivity while the app is in the background. It
 *    also acts as the hard ceiling on retry latency (see below).
 *
 * Both flavours share the same unique name [UNIQUE_NAME] so they cannot run
 * concurrently with each other.
 */
object SyncScheduler {

    const val UNIQUE_NAME = "visits-sync"
    private const val UNIQUE_PERIODIC_NAME = "visits-sync-periodic"

    fun enqueueNow(ctx: Context) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(networkConstraints())
            // LINEAR + 1-min base instead of an exponential curve that WorkManager
            // lets balloon to its 5-hour cap — a stuck row would sit for hours
            // "trying but doing nothing". Growth here is slow, and the 15-min
            // periodic worker is the real ceiling: nothing waits more than ~15 min.
            .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.MINUTES)
            // Ask the OS to run this ASAP; falls back to a normal job when the
            // expedited quota is exhausted rather than being rejected.
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        WorkManager.getInstance(ctx.applicationContext)
            // REPLACE → cancel a pass that's waiting out its backoff and run a
            // fresh one now. This is what makes "Sync now" actually force a sync.
            .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    fun schedulePeriodic(ctx: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(networkConstraints())
            .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(ctx.applicationContext)
            // KEEP → don't reset the schedule on every app cold-start.
            .enqueueUniquePeriodicWork(UNIQUE_PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /**
     * Live state of the one-shot sync work, for UI that wants to report the
     * outcome of a "Sync now" tap (running / succeeded / failed / enqueued).
     */
    fun statusFlow(ctx: Context): Flow<List<WorkInfo>> =
        WorkManager.getInstance(ctx.applicationContext)
            .getWorkInfosForUniqueWorkFlow(UNIQUE_NAME)

    private fun networkConstraints(): Constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
}
