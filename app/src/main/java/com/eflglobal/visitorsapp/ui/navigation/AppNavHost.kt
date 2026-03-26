package com.eflglobal.visitorsapp.ui.navigation

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.eflglobal.visitorsapp.ui.screens.CheckoutQrScreen
import com.eflglobal.visitorsapp.ui.screens.ConfirmScreen
import com.eflglobal.visitorsapp.ui.screens.ContinueVisitScreen
import com.eflglobal.visitorsapp.ui.screens.DocumentScanScreen
import com.eflglobal.visitorsapp.ui.screens.HomeScreen
import com.eflglobal.visitorsapp.ui.screens.PersonDataScreen
import com.eflglobal.visitorsapp.ui.screens.RecurrentDocumentScanScreen
import com.eflglobal.visitorsapp.ui.screens.RecurrentSearchScreen
import com.eflglobal.visitorsapp.ui.screens.RecurrentVisitDataScreen
import com.eflglobal.visitorsapp.ui.screens.StationSetupScreen
import com.eflglobal.visitorsapp.ui.viewmodel.ContinueVisitViewModel
import com.eflglobal.visitorsapp.ui.viewmodel.EndVisitViewModel
import com.eflglobal.visitorsapp.ui.viewmodel.LanguageViewModel
import com.eflglobal.visitorsapp.ui.viewmodel.NewVisitViewModel
import com.eflglobal.visitorsapp.ui.viewmodel.RecurrentSearchViewModel
import com.eflglobal.visitorsapp.ui.viewmodel.RecurrentVisitViewModel
import com.eflglobal.visitorsapp.ui.viewmodel.ViewModelFactory
import com.google.accompanist.navigation.animation.AnimatedNavHost
import com.google.accompanist.navigation.animation.composable

// ── Safe navigation helpers — prevent double-tap / mid-animation crashes ─────

/** Only navigate if the current entry is fully RESUMED (not mid-transition). */
private fun NavHostController.safeNavigate(route: String) {
    if (currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
        navigate(route)
    }
}

/** Only navigate (with builder) if the current entry is fully RESUMED. */
private fun NavHostController.safeNavigate(
    route: String,
    builder: androidx.navigation.NavOptionsBuilder.() -> Unit
) {
    if (currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
        navigate(route, builder)
    }
}

/** Only pop back if the current entry is fully RESUMED. */
private fun NavHostController.safePopBackStack() {
    if (currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
        popBackStack()
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AppNavHost(
    navController: NavHostController,
    languageViewModel: LanguageViewModel,
    selectedLanguage: String,
    startDestination: String = Routes.Home
) {
    val context = LocalContext.current
    val factory = ViewModelFactory(context)

    // ViewModels compartidos entre múltiples pantallas
    val newVisitViewModel: NewVisitViewModel = viewModel(factory = factory)
    val recurrentSearchViewModel: RecurrentSearchViewModel = viewModel(factory = factory)
    val recurrentVisitViewModel: RecurrentVisitViewModel = viewModel(factory = factory)
    val endVisitViewModel: EndVisitViewModel = viewModel(factory = factory)
    val continueVisitViewModel: ContinueVisitViewModel = viewModel(factory = factory)


    AnimatedNavHost(
        navController = navController,
        startDestination = startDestination,
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
        // ── Home ─────────────────────────────────────────────────────────────────
        composable(Routes.Home) {
            HomeScreen(
                onNewVisit      = { navController.safeNavigate(Routes.DocumentScan) },
                onRecurrentVisit = { navController.safeNavigate(Routes.RecurrentSearch) },
                onCheckout      = { navController.safeNavigate(Routes.CheckoutQr) },
                onContinueVisit = { navController.safeNavigate(Routes.ContinueVisit) },
                onStationSetup  = { navController.safeNavigate(Routes.StationSetup) },
                onAdminAccess   = { navController.safeNavigate(Routes.AdminPanel) },
                languageViewModel = languageViewModel,
                selectedLanguage  = selectedLanguage
            )
        }
        composable(Routes.StationSetup) {
            StationSetupScreen(
                onActivate = { navController.safeNavigate(Routes.Home) },
                onBack = { navController.safePopBackStack() },
                selectedLanguage = selectedLanguage,
                languageViewModel = languageViewModel
            )
        }
        composable(Routes.DocumentScan) {
            DocumentScanScreen(
                onContinue = { navController.safeNavigate(Routes.PersonData) },
                onBack = { navController.safePopBackStack() },
                selectedLanguage = selectedLanguage,
                viewModel = newVisitViewModel
            )
        }
        composable(Routes.RecurrentSearch) {
            RecurrentSearchScreen(
                onPersonSelected = { navController.safeNavigate(Routes.RecurrentDocumentScan) },
                onBack = {
                    recurrentVisitViewModel.resetState()
                    navController.safePopBackStack()
                },
                selectedLanguage = selectedLanguage,
                searchViewModel = recurrentSearchViewModel,
                recurrentVisitViewModel = recurrentVisitViewModel
            )
        }
        composable(Routes.RecurrentDocumentScan) {
            RecurrentDocumentScanScreen(
                onContinue       = { navController.safeNavigate(Routes.RecurrentVisitData) },
                onBack           = {
                    recurrentVisitViewModel.resetDocuments()
                    navController.safePopBackStack()
                },
                selectedLanguage = selectedLanguage,
                viewModel        = recurrentVisitViewModel
            )
        }
        composable(Routes.RecurrentVisitData) {
            val person = recurrentVisitViewModel.getSelectedPerson()
            RecurrentVisitDataScreen(
                visitorName = person?.fullName ?: "",
                onContinue = { navController.safeNavigate(Routes.Confirm) },
                onBack = {
                    recurrentVisitViewModel.resetDocuments()
                    navController.safePopBackStack()
                },
                selectedLanguage = selectedLanguage,
                viewModel = recurrentVisitViewModel
            )
        }
        composable(Routes.PersonData) {
            PersonDataScreen(
                onContinue = { navController.safeNavigate(Routes.Confirm) },
                onBack = {
                    newVisitViewModel.resetDocuments()
                    navController.safePopBackStack()
                },
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

            val personFirstName = when {
                newVisitState is com.eflglobal.visitorsapp.ui.viewmodel.NewVisitUiState.Success -> newVisitState.firstName
                recurrentVisitState is com.eflglobal.visitorsapp.ui.viewmodel.RecurrentVisitUiState.Success -> recurrentVisitState.firstName
                else -> null
            }

            val personLastName = when {
                newVisitState is com.eflglobal.visitorsapp.ui.viewmodel.NewVisitUiState.Success -> newVisitState.lastName
                recurrentVisitState is com.eflglobal.visitorsapp.ui.viewmodel.RecurrentVisitUiState.Success -> recurrentVisitState.lastName
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
                    navController.safeNavigate(Routes.Home) {
                        popUpTo(Routes.Home) { inclusive = false }
                    }
                },
                onEdit = {
                    // Reset Success state FIRST so PersonDataScreen doesn't
                    // immediately navigate forward again when it recomposes
                    newVisitViewModel.resetToEditing()
                    recurrentVisitViewModel.resetToEditing()
                    navController.safePopBackStack()
                },
                selectedLanguage = selectedLanguage,
                qrCode           = qrCode,
                personName       = personName,
                firstName        = personFirstName,
                lastName         = personLastName,
                visitingPerson   = visitingPerson,
                company          = company,
                profilePhotoPath = profilePhotoPath,
                visitorType      = visitorType
            )
        }
        composable(Routes.CheckoutQr) {
            CheckoutQrScreen(
                onFinish = { navController.safeNavigate(Routes.Home) },
                onBack = { navController.safePopBackStack() },
                viewModel = endVisitViewModel,
                selectedLanguage = selectedLanguage
            )
        }
        composable(Routes.ContinueVisit) {
            ContinueVisitScreen(
                onFinish         = {
                    navController.safeNavigate(Routes.Home) {
                        popUpTo(Routes.Home) { inclusive = false }
                    }
                },
                onBack           = { navController.safePopBackStack() },
                viewModel        = continueVisitViewModel,
                selectedLanguage = selectedLanguage
            )
        }
        composable(Routes.AdminPanel) {
            val adminPanelViewModel: com.eflglobal.visitorsapp.ui.viewmodel.AdminPanelViewModel = viewModel(factory = factory)
            com.eflglobal.visitorsapp.ui.screens.AdminPanelScreen(
                onBack = { navController.safePopBackStack() },
                onLogout = {
                    // Navegar a StationSetup y limpiar el back stack
                    navController.safeNavigate(Routes.StationSetup) {
                        popUpTo(Routes.Home) { inclusive = true }
                    }
                },
                viewModel = adminPanelViewModel,
                selectedLanguage = selectedLanguage
            )
        }
    }
}
