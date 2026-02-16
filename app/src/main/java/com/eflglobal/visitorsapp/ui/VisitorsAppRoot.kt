package com.eflglobal.visitorsapp.ui

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eflglobal.visitorsapp.ui.navigation.AppNavHost
import com.eflglobal.visitorsapp.ui.theme.VisitorsAppTheme
import com.eflglobal.visitorsapp.ui.viewmodel.LanguageViewModel
import com.google.accompanist.navigation.animation.rememberAnimatedNavController

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun VisitorsAppRoot() {
    val context = LocalContext.current
    val languageViewModel: LanguageViewModel = viewModel(
        factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(
            context.applicationContext as android.app.Application
        )
    )
    val selectedLanguage by languageViewModel.selectedLanguage.collectAsState()

    VisitorsAppTheme(dynamicColor = false) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            AppNavHost(
                navController = rememberAnimatedNavController(),
                languageViewModel = languageViewModel,
                selectedLanguage = selectedLanguage
            )
        }
    }
}
