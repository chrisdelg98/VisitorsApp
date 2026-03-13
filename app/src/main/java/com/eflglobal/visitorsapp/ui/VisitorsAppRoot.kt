package com.eflglobal.visitorsapp.ui

import android.app.Activity
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eflglobal.visitorsapp.ui.localization.LanguageManager
import com.eflglobal.visitorsapp.ui.navigation.AppNavHost
import com.eflglobal.visitorsapp.ui.navigation.Routes
import com.eflglobal.visitorsapp.ui.theme.VisitorsAppTheme
import com.eflglobal.visitorsapp.ui.viewmodel.LanguageViewModel
import com.eflglobal.visitorsapp.ui.viewmodel.SplashViewModel
import com.google.accompanist.navigation.animation.rememberAnimatedNavController

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun VisitorsAppRoot(splashViewModel: SplashViewModel) {
    val context = LocalContext.current
    val activityContext: android.content.Context = remember(context) {
        var ctx = context
        while (ctx is android.content.ContextWrapper && ctx !is Activity) {
            ctx = ctx.baseContext
        }
        ctx
    }

    val languageViewModel: LanguageViewModel = viewModel(
        factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(
            context.applicationContext as android.app.Application
        )
    )
    val selectedLanguage by languageViewModel.selectedLanguage.collectAsState()

    val localizedContext = remember(selectedLanguage) {
        LanguageManager.wrapContext(activityContext, selectedLanguage)
    }

    // Determine start destination from splash result (splash keeps screen while Loading)
    val splashState by splashViewModel.state.collectAsState()
    val startDestination = when (splashState) {
        is SplashViewModel.State.HasStation -> Routes.Home
        else                               -> Routes.StationSetup
    }

    VisitorsAppTheme(dynamicColor = false) {
        CompositionLocalProvider(LocalContext provides localizedContext) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color    = MaterialTheme.colorScheme.background
            ) {
                AppNavHost(
                    navController    = rememberAnimatedNavController(),
                    languageViewModel = languageViewModel,
                    selectedLanguage  = selectedLanguage,
                    startDestination  = startDestination
                )
            }
        }
    }
}
