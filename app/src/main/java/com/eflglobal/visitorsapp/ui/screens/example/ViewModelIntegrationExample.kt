package com.eflglobal.visitorsapp.ui.screens.example

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.eflglobal.visitorsapp.ui.viewmodel.StationSetupViewModel
import com.eflglobal.visitorsapp.ui.viewmodel.StationSetupUiState
import com.eflglobal.visitorsapp.ui.viewmodel.ViewModelFactory

/**
 * EJEMPLO DE INTEGRACIÓN: StationSetupScreen con ViewModel
 *
 * Este es un ejemplo de cómo integrar el ViewModel con la Screen.
 * Copiar este patrón para las demás pantallas.
 */
@Composable
fun StationSetupScreenWithViewModel(
    navController: NavHostController,
    viewModel: StationSetupViewModel = viewModel(
        factory = ViewModelFactory(LocalContext.current)
    )
) {
    // Observar el estado del ViewModel
    val uiState by viewModel.uiState.collectAsState()

    // Estado local para el PIN input
    var pinInput by remember { mutableStateOf("") }

    // Efecto para manejar navegación cuando hay éxito
    LaunchedEffect(uiState) {
        when (uiState) {
            is StationSetupUiState.Success -> {
                // Navegar a Home cuando la estación se configura exitosamente
                navController.navigate("home") {
                    popUpTo("station_setup") { inclusive = true }
                }
            }
            is StationSetupUiState.StationExists -> {
                // Si ya existe estación, ir directo a home
                navController.navigate("home") {
                    popUpTo("station_setup") { inclusive = true }
                }
            }
            else -> { /* No hacer nada */ }
        }
    }

    // UI
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Station Setup",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Campo de PIN
        OutlinedTextField(
            value = pinInput,
            onValueChange = {
                if (it.length <= 8 && it.all { char -> char.isDigit() }) {
                    pinInput = it
                    // Limpiar error cuando el usuario empieza a escribir
                    if (uiState is StationSetupUiState.Error) {
                        viewModel.clearError()
                    }
                }
            },
            label = { Text("Enter 8-digit PIN") },
            isError = uiState is StationSetupUiState.Error,
            supportingText = {
                if (uiState is StationSetupUiState.Error) {
                    Text((uiState as StationSetupUiState.Error).message)
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Botón de activación
        Button(
            onClick = {
                viewModel.validatePin(pinInput)
            },
            enabled = pinInput.length == 8 && uiState !is StationSetupUiState.Loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (uiState is StationSetupUiState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Activate Station")
            }
        }
    }
}

/**
 * EJEMPLO 2: NewVisitScreen con ViewModel
 */
@Composable
fun NewVisitScreenExample() {
    // TODO: Implementar usando NewVisitViewModel
    // Patrón similar al ejemplo anterior
}

