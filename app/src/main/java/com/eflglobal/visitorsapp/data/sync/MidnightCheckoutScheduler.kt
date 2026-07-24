package com.eflglobal.visitorsapp.data.sync

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Wires the [MidnightCheckoutWorker] into WorkManager. Runs once a day with the
 * first pass timed a few minutes after the next local midnight; KEEP policy so
 * repeated cold-starts don't reset the schedule (which would keep pushing the
 * initial delay forward and the pass would never fire).
 */
object MidnightCheckoutScheduler {

    private const val UNIQUE_NAME = "visits-midnight-checkout"

    fun schedule(ctx: Context) {
        val request = PeriodicWorkRequestBuilder<MidnightCheckoutWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(millisUntilNextRun(), TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(ctx.applicationContext)
            .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Milliseconds from now until 00:05 of the next day (small buffer past midnight). */
    private fun millisUntilNextRun(): Long {
        val now = Calendar.getInstance()
        val next = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 5)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return next.timeInMillis - now.timeInMillis
    }
}
