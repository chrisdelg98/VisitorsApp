package com.eflglobal.visitorsapp.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Public entry-point to schedule the [SyncWorker].
 *
 *  - [enqueueNow] is fire-and-forget; the right thing to call after creating
 *    a visit or on `onResume` of the main activity. Multiple rapid calls are
 *    coalesced — only one upload pass runs at a time.
 *
 *  - [schedulePeriodic] is called once on app start as a safety net for
 *    devices that recover connectivity while the app is in the background.
 *
 * Both flavours share the same unique name [UNIQUE_NAME] so they cannot run
 * concurrently with each other.
 */
object SyncScheduler {

    private const val UNIQUE_NAME = "visits-sync"
    private const val UNIQUE_PERIODIC_NAME = "visits-sync-periodic"

    fun enqueueNow(ctx: Context) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(networkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(ctx.applicationContext)
            // APPEND_OR_REPLACE → if a sync is already running, queue another
            // pass after it so newly-inserted rows are picked up.
            .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    fun schedulePeriodic(ctx: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(networkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(ctx.applicationContext)
            // KEEP → don't reset the schedule on every app cold-start.
            .enqueueUniquePeriodicWork(UNIQUE_PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private fun networkConstraints(): Constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
}

