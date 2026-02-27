package com.eflglobal.visitorsapp.ui.navigation

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.eflglobal.visitorsapp.ui.screens.CheckoutQrScreen
import com.eflglobal.visitorsapp.ui.screens.ConfirmScreen
import com.eflglobal.visitorsapp.ui.screens.DocumentScanScreen
import com.eflglobal.visitorsapp.ui.screens.HomeScreen
import com.eflglobal.visitorsapp.ui.screens.PersonDataScreen
import com.eflglobal.visitorsapp.ui.screens.RecurrentDocumentScanScreen
import com.eflglobal.visitorsapp.ui.screens.RecurrentSearchScreen
import com.eflglobal.visitorsapp.ui.screens.RecurrentVisitDataScreen
import com.eflglobal.visitorsapp.ui.screens.StationSetupScreen
import com.eflglobal.visitorsapp.ui.viewmodel.EndVisitViewModel
import com.eflglobal.visitorsapp.ui.viewmodel.LanguageViewModel
import com.eflglobal.visitorsapp.ui.viewmodel.NewVisitViewModel
import com.eflglobal.visitorsapp.ui.viewmodel.RecurrentSearchViewModel
import com.eflglobal.visitorsapp.ui.viewmodel.RecurrentVisitViewModel
import com.eflglobal.visitorsapp.ui.viewmodel.ViewModelFactory
import com.google.accompanist.navigation.animation.AnimatedNavHost
import com.google.accompanist.navigation.animation.composable

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AppNavHost(
    navController: NavHostController,
    languageViewModel: LanguageViewModel,
    selectedLanguage: String
) {
    val context = LocalContext.current
    val factory = ViewModelFactory(context)

    // ViewModels compartidos entre múltiples pantallas
    val newVisitViewModel: NewVisitViewModel = viewModel(factory = factory)
    val recurrentSearchViewModel: RecurrentSearchViewModel = viewModel(factory = factory)
    val recurrentVisitViewModel: RecurrentVisitViewModel = viewModel(factory = factory)
    val endVisitViewModel: EndVisitViewModel = viewModel(factory = factory)

    AnimatedNavHost(
        navController = navController,
        startDestination = Routes.Home,
        enterTransition = {
            fadeIn(animationSpec = tween(600, easing = androidx.compose.animation.core.FastOutSlowInEasing))
        },
        exitTransition = {
            fadeOut(animationSpec = tween(450, easing = androidx.compose.animation.core.FastOutSlowInEasing))
        },
        popEnterTransition = {
            fadeIn(animationSpec = tween(600, easing = androidx.compose.animation.core.FastOutSlowInEasing))
        },
        popExitTransition = {
            fadeOut(animationSpec = tween(450, easing = androidx.compose.animation.core.FastOutSlowInEasing))
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
                selectedLanguage = selectedLanguage,
                viewModel = newVisitViewModel
            )
        }
        composable(Routes.RecurrentSearch) {
            RecurrentSearchScreen(
                onPersonSelected = { navController.navigate(Routes.RecurrentDocumentScan) },
                onBack = {
                    recurrentVisitViewModel.resetState()
                    navController.popBackStack()
                },
                selectedLanguage = selectedLanguage,
                searchViewModel = recurrentSearchViewModel,
                recurrentVisitViewModel = recurrentVisitViewModel
            )
        }
        composable(Routes.RecurrentDocumentScan) {
            RecurrentDocumentScanScreen(
                onContinue       = { navController.navigate(Routes.RecurrentVisitData) },
                onBack           = {
                    recurrentVisitViewModel.resetDocuments()
                    navController.popBackStack()
                },
                selectedLanguage = selectedLanguage,
                viewModel        = recurrentVisitViewModel
            )
        }
        composable(Routes.RecurrentVisitData) {
            val person = recurrentVisitViewModel.getSelectedPerson()
            RecurrentVisitDataScreen(
                visitorName = person?.fullName ?: "",
                onContinue = { navController.navigate(Routes.Confirm) },
                onBack = { navController.popBackStack() },
                selectedLanguage = selectedLanguage,
                viewModel = recurrentVisitViewModel
            )
        }
        composable(Routes.PersonData) {
            PersonDataScreen(
                onContinue = { navController.navigate(Routes.Confirm) },
                onBack = { navController.popBackStack() },
                selectedLanguage = selectedLanguage,
                viewModel = newVisitViewModel
            )
        }
        composable(Routes.Confirm) {
            // Obtener datos del estado del ViewModel
            val newVisitState = newVisitViewModel.uiState.collectAsState().value
            val recurrentVisitState = recurrentVisitViewModel.uiState.collectAsState().value

            val qrCode = when {
                newVisitState is com.eflglobal.visitorsapp.ui.viewmodel.NewVisitUiState.Success -> newVisitState.qrCode
                recurrentVisitState is com.eflglobal.visitorsapp.ui.viewmodel.RecurrentVisitUiState.Success -> recurrentVisitState.qrCode
                else -> null
            }

            val personName = when {
                newVisitState is com.eflglobal.visitorsapp.ui.viewmodel.NewVisitUiState.Success -> newVisitState.personName
                recurrentVisitState is com.eflglobal.visitorsapp.ui.viewmodel.RecurrentVisitUiState.Success -> recurrentVisitState.personName
                else -> null
            }

            val visitingPerson = when {
                newVisitState is com.eflglobal.visitorsapp.ui.viewmodel.NewVisitUiState.Success -> newVisitState.visitingPerson
                recurrentVisitState is com.eflglobal.visitorsapp.ui.viewmodel.RecurrentVisitUiState.Success -> recurrentVisitState.visitingPerson
                else -> null
            }

            val company = when {
                newVisitState is com.eflglobal.visitorsapp.ui.viewmodel.NewVisitUiState.Success -> newVisitState.company
                recurrentVisitState is com.eflglobal.visitorsapp.ui.viewmodel.RecurrentVisitUiState.Success -> recurrentVisitState.company
                else -> null
            }

            val profilePhotoPath = when {
                newVisitState is com.eflglobal.visitorsapp.ui.viewmodel.NewVisitUiState.Success -> newVisitState.profilePhotoPath
                recurrentVisitState is com.eflglobal.visitorsapp.ui.viewmodel.RecurrentVisitUiState.Success -> recurrentVisitState.profilePhotoPath
                else -> null
            }

            val visitorType = when {
                newVisitState is com.eflglobal.visitorsapp.ui.viewmodel.NewVisitUiState.Success -> newVisitState.visitorType
                recurrentVisitState is com.eflglobal.visitorsapp.ui.viewmodel.RecurrentVisitUiState.Success -> recurrentVisitState.visitorType
                else -> "VISITOR"
            }

            ConfirmScreen(
                onConfirm = {
                    newVisitViewModel.resetState()
                    recurrentVisitViewModel.resetState()
                    navController.navigate(Routes.Home) {
                        popUpTo(Routes.Home) { inclusive = false }
                    }
                },
                onEdit = {
                    // Reset Success state FIRST so PersonDataScreen doesn't
                    // immediately navigate forward again when it recomposes
                    newVisitViewModel.resetToEditing()
                    recurrentVisitViewModel.resetToEditing()
                    navController.popBackStack()
                },
                selectedLanguage = selectedLanguage,
                qrCode           = qrCode,
                personName       = personName,
                visitingPerson   = visitingPerson,
                company          = company,
                profilePhotoPath = profilePhotoPath,
                visitorType      = visitorType
            )
        }
        composable(Routes.CheckoutQr) {
            CheckoutQrScreen(
                onFinish = { navController.navigate(Routes.Home) },
                onBack = { navController.popBackStack() },
                viewModel = endVisitViewModel,
                selectedLanguage = selectedLanguage
            )
        }
    }
}
