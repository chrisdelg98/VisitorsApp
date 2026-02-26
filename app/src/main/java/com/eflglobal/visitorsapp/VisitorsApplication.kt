package com.eflglobal.visitorsapp

import android.app.Application

/**
 * Application class para la app de registro de visitantes.
 */
class VisitorsApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // AppDatabase.getInstance() handles its own lazy initialization.
        // No explicit DependencyProvider.initialize() call needed.
    }
}

