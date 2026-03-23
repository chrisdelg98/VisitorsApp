package com.eflglobal.visitorsapp.core.printing

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that runs printer discovery once per day.
 *
 * Behaviour:
 * 1. Checks if auto-discovery is enabled in preferences.
 * 2. Runs [PrinterDiscoveryService.discoverAll].
 * 3. If the previously saved printer (by [PrinterConfig.printerIdentifier]) is found
 *    in the discovery results but at a different IP → **auto-updates** the saved config.
 * 4. Updates [PrinterConfig.lastDiscoveryTimestamp].
 *
 * This ensures that even with DHCP IP changes the app always points to the
 * correct physical printer without user intervention.
 */
class PrinterDiscoveryWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "PrinterDiscoveryWorker"
        private const val WORK_NAME = "printer_auto_discovery"

        /**
         * Schedules the daily auto-discovery. Safe to call multiple times —
         * [ExistingPeriodicWorkPolicy.KEEP] ensures only one instance runs.
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<PrinterDiscoveryWorker>(
                repeatInterval = 24,
                repeatIntervalTimeUnit = TimeUnit.HOURS
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Log.i(TAG, "Daily auto-discovery scheduled")
        }

        /**
         * Cancels the scheduled daily discovery.
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.i(TAG, "Daily auto-discovery cancelled")
        }
    }

    override suspend fun doWork(): androidx.work.ListenableWorker.Result {
        val ctx = applicationContext
        Log.i(TAG, "Auto-discovery started")

        // 1. Check if auto-discovery is enabled
        val enabled = PrinterConfigRepository.isAutoDiscoveryEnabled(ctx).first()
        if (!enabled) {
            Log.i(TAG, "Auto-discovery disabled — skipping")
            return androidx.work.ListenableWorker.Result.success()
        }

        // 2. Load current config
        val config = PrinterConfigRepository.getConfigOnce(ctx)
        if (config.brand == PrinterConfig.PrinterBrand.NONE) {
            Log.i(TAG, "No printer configured — skipping discovery")
            return androidx.work.ListenableWorker.Result.success()
        }

        // 3. Run discovery
        val discovered = PrinterDiscoveryService.discoverAll(ctx)
        Log.i(TAG, "Discovered ${discovered.size} printer(s)")

        // 4. Try to match saved printer by identifier
        val savedId = config.printerIdentifier
        if (savedId.isNotBlank()) {
            val matched = PrinterDiscoveryService.findKnownPrinter(discovered, savedId)
            if (matched != null) {
                val ipChanged = matched.ipAddress != null && matched.ipAddress != config.networkHost
                if (ipChanged) {
                    Log.i(TAG, "Printer IP changed: ${config.networkHost} → ${matched.ipAddress}")
                    val updatedConfig = config.copy(
                        networkHost           = matched.ipAddress,
                        printerDisplayName    = matched.displayName,
                        lastDiscoveryTimestamp = System.currentTimeMillis()
                    )
                    PrinterConfigRepository.saveConfig(ctx, updatedConfig)
                    Log.i(TAG, "Config auto-updated with new IP")
                } else {
                    // Same IP — just update timestamp
                    PrinterConfigRepository.saveConfig(
                        ctx,
                        config.copy(lastDiscoveryTimestamp = System.currentTimeMillis())
                    )
                    Log.i(TAG, "Printer verified at same IP — timestamp updated")
                }
            } else {
                Log.w(TAG, "Saved printer '$savedId' NOT found in discovery results")
                // Don't clear config — printer might just be offline
                PrinterConfigRepository.saveConfig(
                    ctx,
                    config.copy(lastDiscoveryTimestamp = System.currentTimeMillis())
                )
            }
        } else {
            // No saved identifier — just update timestamp
            PrinterConfigRepository.saveConfig(
                ctx,
                config.copy(lastDiscoveryTimestamp = System.currentTimeMillis())
            )
        }

        return androidx.work.ListenableWorker.Result.success()
    }
}


