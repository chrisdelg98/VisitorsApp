package com.eflglobal.visitorsapp

import android.app.Application
import com.eflglobal.visitorsapp.core.DependencyProvider

/**
 * Application class para la app de registro de visitantes.
 */
class VisitorsApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Inicializar el proveedor de dependencias
        DependencyProvider.initialize(this)
    }
}

