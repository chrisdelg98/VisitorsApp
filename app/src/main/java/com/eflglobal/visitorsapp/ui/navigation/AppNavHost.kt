package com.eflglobal.visitorsapp.ui.navigation

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import com.eflglobal.visitorsapp.ui.screens.CheckoutQrScreen
import com.eflglobal.visitorsapp.ui.screens.ConfirmScreen
import com.eflglobal.visitorsapp.ui.screens.DocumentScanScreen
import com.eflglobal.visitorsapp.ui.screens.HomeScreen
import com.eflglobal.visitorsapp.ui.screens.PersonDataScreen
import com.eflglobal.visitorsapp.ui.screens.RecurrentDocumentScanScreen
import com.eflglobal.visitorsapp.ui.screens.RecurrentSearchScreen
import com.eflglobal.visitorsapp.ui.screens.RecurrentVisitDataScreen
import com.eflglobal.visitorsapp.ui.screens.StationSetupScreen
import com.eflglobal.visitorsapp.ui.viewmodel.LanguageViewModel
import com.google.accompanist.navigation.animation.AnimatedNavHost
import com.google.accompanist.navigation.animation.composable
import androidx.navigation.NavHostController

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AppNavHost(
    navController: NavHostController,
    languageViewModel: LanguageViewModel,
    selectedLanguage: String
) {
    AnimatedNavHost(
        navController = navController,
        startDestination = Routes.Home,
        enterTransition = {
            fadeIn(animationSpec = tween(220)) +
                slideInHorizontally(animationSpec = tween(220)) { it / 6 }
        },
        exitTransition = {
            fadeOut(animationSpec = tween(160)) +
                slideOutHorizontally(animationSpec = tween(160)) { -it / 6 }
        },
        popEnterTransition = {
            fadeIn(animationSpec = tween(200)) +
                slideInHorizontally(animationSpec = tween(200)) { -it / 6 }
        },
        popExitTransition = {
            fadeOut(animationSpec = tween(140)) +
                slideOutHorizontally(animationSpec = tween(140)) { it / 6 }
        }
    ) {
        composable(Routes.Home) {
            HomeScreen(
                onNewVisit = { navController.navigate(Routes.DocumentScan) },
                onRecurrentVisit = { navController.navigate(Routes.RecurrentSearch) },
                onCheckout = { navController.navigate(Routes.CheckoutQr) },
                onStationSetup = { navController.navigate(Routes.StationSetup) },
                languageViewModel = languageViewModel,
                selectedLanguage = selectedLanguage
            )
        }
        composable(Routes.StationSetup) {
            StationSetupScreen(
                onActivate = { navController.navigate(Routes.Home) },
                onBack = { navController.popBackStack() },
                selectedLanguage = selectedLanguage
            )
        }
        composable(Routes.DocumentScan) {
            DocumentScanScreen(
                onContinue = { navController.navigate(Routes.PersonData) },
                onBack = { navController.popBackStack() },
                selectedLanguage = selectedLanguage
            )
        }
        composable(Routes.RecurrentSearch) {
            RecurrentSearchScreen(
                onPersonSelected = { navController.navigate(Routes.RecurrentDocumentScan) },
                onBack = { navController.popBackStack() },
                selectedLanguage = selectedLanguage
            )
        }
        composable(Routes.RecurrentDocumentScan) {
            RecurrentDocumentScanScreen(
                visitorName = "Juan Pérez", // TODO: Pasar datos reales desde búsqueda
                documentNumber = "12345678-9",
                company = "Tech Solutions Inc.",
                email = "juan.perez@example.com",
                phone = "+503 7890-1234",
                onContinue = { navController.navigate(Routes.RecurrentVisitData) },
                onBack = { navController.popBackStack() },
                selectedLanguage = selectedLanguage
            )
        }
        composable(Routes.RecurrentVisitData) {
            RecurrentVisitDataScreen(
                visitorName = "Juan Pérez", // TODO: Pasar datos reales desde búsqueda
                documentNumber = "12345678-9",
                onContinue = { navController.navigate(Routes.Confirm) },
                onBack = { navController.popBackStack() },
                selectedLanguage = selectedLanguage
            )
        }
        composable(Routes.PersonData) {
            PersonDataScreen(
                onContinue = { navController.navigate(Routes.Confirm) },
                onBack = { navController.popBackStack() },
                selectedLanguage = selectedLanguage
            )
        }
        composable(Routes.Confirm) {
            ConfirmScreen(
                onConfirm = { navController.navigate(Routes.Home) },
                onEdit = { navController.popBackStack() },
                selectedLanguage = selectedLanguage
            )
        }
        composable(Routes.CheckoutQr) {
            CheckoutQrScreen(
                onFinish = { navController.navigate(Routes.Home) },
                onBack = { navController.popBackStack() },
                selectedLanguage = selectedLanguage
            )
        }
    }
}
