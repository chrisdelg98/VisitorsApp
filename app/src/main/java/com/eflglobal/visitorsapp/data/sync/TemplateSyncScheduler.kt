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
 * Schedules the [TemplateSyncWorker].
 *
 *  - [scheduleDaily] — a once-per-day periodic sync (safety net). KEEP policy so
 *    cold starts don't reset the schedule.
 *  - [enqueueNow]   — an immediate one-shot pass, called on app start so a fresh
 *    catalog lands ASAP after activation / a backend publish.
 *
 * Mirrors [SyncScheduler]'s conventions (unique names, network constraint,
 * exponential backoff).
 */
object TemplateSyncScheduler {

    private const val UNIQUE_NAME = "ocr-templates-sync"
    private const val UNIQUE_PERIODIC_NAME = "ocr-templates-sync-periodic"

    fun enqueueNow(ctx: Context) {
        val request = OneTimeWorkRequestBuilder<TemplateSyncWorker>()
            .setConstraints(networkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(ctx.applicationContext)
            .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.KEEP, request)
    }

    fun scheduleDaily(ctx: Context) {
        val request = PeriodicWorkRequestBuilder<TemplateSyncWorker>(1, TimeUnit.DAYS)
            .setConstraints(networkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(ctx.applicationContext)
            .enqueueUniquePeriodicWork(UNIQUE_PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private fun networkConstraints(): Constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
}
