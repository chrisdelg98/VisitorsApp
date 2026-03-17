package com.eflglobal.visitorsapp

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.eflglobal.visitorsapp.ui.VisitorsAppRoot
import com.eflglobal.visitorsapp.ui.viewmodel.SplashViewModel

class MainActivity : ComponentActivity() {

    private val splashViewModel: SplashViewModel by viewModels {
        SplashViewModel.Factory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()

        // Keep the splash visible until BOTH:
        //  1. The station check has resolved (not Loading)
        //  2. At least 2 seconds have elapsed
        val startTime = System.currentTimeMillis()
        splash.setKeepOnScreenCondition {
            val elapsed = System.currentTimeMillis() - startTime
            splashViewModel.isLoading || elapsed < 2_000L
        }

        super.onCreate(savedInstanceState)

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        enableEdgeToEdge()
        setContent {
            VisitorsAppRoot(splashViewModel = splashViewModel)
        }
    }
}
