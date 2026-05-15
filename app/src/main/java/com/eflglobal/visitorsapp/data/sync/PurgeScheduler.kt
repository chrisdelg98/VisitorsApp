package com.eflglobal.visitorsapp.data.sync

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Wires the [PurgeWorker] into WorkManager. Runs daily with KEEP policy so
 * repeated cold-starts do not reset the schedule.
 */
object PurgeScheduler {

    private const val UNIQUE_NAME = "visits-purge-daily"

    fun schedule(ctx: Context) {
        val request = PeriodicWorkRequestBuilder<PurgeWorker>(1, TimeUnit.DAYS)
            // Small initial delay so we don't fight the sync worker at boot.
            .setInitialDelay(15, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(ctx.applicationContext)
            .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

