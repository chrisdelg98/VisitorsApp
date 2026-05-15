package com.eflglobal.visitorsapp

import android.app.Application
import com.eflglobal.visitorsapp.core.printing.PrinterDiscoveryWorker
import com.eflglobal.visitorsapp.data.sync.PurgeScheduler
import com.eflglobal.visitorsapp.data.sync.SyncScheduler

/**
 * Application class para la app de registro de visitantes.
 */
class VisitorsApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // AppDatabase.getInstance() handles its own lazy initialization.
        // No explicit DependencyProvider.initialize() call needed.

        // Schedule daily auto-discovery of network printers.
        // Uses WorkManager — safe to call on every launch (KEEP policy deduplicates).
        PrinterDiscoveryWorker.schedule(this)

        // Sync engine — periodic safety net + an immediate pass so anything
        // that piled up while the app was closed gets uploaded ASAP.
        SyncScheduler.schedulePeriodic(this)
        SyncScheduler.enqueueNow(this)

        // Daily local retention pass (Phase 8).
        PurgeScheduler.schedule(this)
    }
}

