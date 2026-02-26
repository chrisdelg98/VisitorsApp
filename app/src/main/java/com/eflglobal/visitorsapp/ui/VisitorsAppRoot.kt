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
import com.eflglobal.visitorsapp.ui.theme.VisitorsAppTheme
import com.eflglobal.visitorsapp.ui.viewmodel.LanguageViewModel
import com.google.accompanist.navigation.animation.rememberAnimatedNavController

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun VisitorsAppRoot() {
    // We need the Activity context (not applicationContext) so that
    // Accompanist permissions and other Activity-aware APIs can still
    // find the Activity by walking up the context chain.
    val context = LocalContext.current
    val activityContext: android.content.Context = remember(context) {
        // Walk up until we find the Activity, fall back to context as-is
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

    // Wrap the Activity context with the selected locale.
    // ContextWrapper preserves the Activity in the chain — permissions,
    // CameraX and other Activity-aware APIs continue to work correctly.
    val localizedContext = remember(selectedLanguage) {
        LanguageManager.wrapContext(activityContext, selectedLanguage)
    }

    VisitorsAppTheme(dynamicColor = false) {
        CompositionLocalProvider(LocalContext provides localizedContext) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color    = MaterialTheme.colorScheme.background
            ) {
                AppNavHost(
                    navController     = rememberAnimatedNavController(),
                    languageViewModel = languageViewModel,
                    selectedLanguage  = selectedLanguage
                )
            }
        }
    }
}
