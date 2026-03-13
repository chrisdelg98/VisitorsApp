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
        // ① Install splash BEFORE super.onCreate() so the theme is applied correctly
        val splash = installSplashScreen()

        super.onCreate(savedInstanceState)

        // ② Keep splash on screen while the station check is still running
        splash.setKeepOnScreenCondition { splashViewModel.isLoading }

        // Forzar orientación horizontal (landscape)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        enableEdgeToEdge()
        setContent {
            VisitorsAppRoot(splashViewModel = splashViewModel)
        }
    }
}
