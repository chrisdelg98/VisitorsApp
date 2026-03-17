package com.eflglobal.visitorsapp

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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

        // Edge-to-edge + fullscreen: content draws behind system bars, then bars are hidden
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()

        setContent {
            VisitorsAppRoot(splashViewModel = splashViewModel)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Re-hide bars when app regains focus (e.g. after a notification shade)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        // Hide both status bar and navigation bar
        controller.hide(WindowInsetsCompat.Type.systemBars())
        // Allow user to temporarily reveal bars by swiping from edge
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}
