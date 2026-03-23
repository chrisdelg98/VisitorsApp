package com.eflglobal.visitorsapp

import android.app.Application
import com.eflglobal.visitorsapp.core.printing.PrinterDiscoveryWorker

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
    }
}

