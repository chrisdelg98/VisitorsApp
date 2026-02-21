package com.eflglobal.visitorsapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eflglobal.visitorsapp.ui.theme.OrangePrimary
import com.eflglobal.visitorsapp.ui.theme.SlatePrimary
import com.eflglobal.visitorsapp.ui.viewmodel.StationSetupViewModel
import com.eflglobal.visitorsapp.ui.viewmodel.StationSetupUiState
import com.eflglobal.visitorsapp.ui.viewmodel.ViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationSetupScreen(
    onActivate: () -> Unit,
    onBack: () -> Unit,
    selectedLanguage: String = "es",
    viewModel: StationSetupViewModel = viewModel(
        factory = ViewModelFactory(LocalContext.current)
    )
) {
    var pin by remember { mutableStateOf("") }
    val uiState by viewModel.uiState.collectAsState()

    // Manejar navegación cuando se activa exitosamente
    LaunchedEffect(uiState) {
        when (uiState) {
            is StationSetupUiState.Success -> {
                onActivate()
            }
            is StationSetupUiState.StationExists -> {
                onActivate()
            }
            else -> { /* No hacer nada */ }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Station Setup",
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Icono o logo
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .background(
                            color = OrangePrimary.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(60.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = OrangePrimary,
                        modifier = Modifier.size(60.dp)
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = "Activación de Estación",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = SlatePrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Ingrese el PIN de 8 dígitos para activar esta tableta como estación de registro",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(48.dp))

                // Campo de PIN
                OutlinedTextField(
                    value = pin,
                    onValueChange = {
                        if (it.length <= 8 && it.all { char -> char.isDigit() }) {
                            pin = it
                            isError = false
                        }
                    },
                    label = { Text("PIN de Estación") },
                    placeholder = { Text("00000000") },
                    isError = isError,
                    supportingText = {
                        if (isError) {
                            Text("PIN incorrecto. Intente nuevamente.")
                        } else {
                            Text("${pin.length}/8 dígitos")
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SlatePrimary,
                        focusedLabelColor = SlatePrimary,
                        errorBorderColor = MaterialTheme.colorScheme.error
                    )
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Botón de activar
                Button(
                    onClick = {
                        viewModel.validatePin(pin)
                    },
                    enabled = pin.length == 8 && uiState !is StationSetupUiState.Loading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = OrangePrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 4.dp
                    )
                ) {
                    if (uiState is StationSetupUiState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text(
                            text = "Activar Estación",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Información de ayuda
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "ℹ️ Información",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = SlatePrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Esta configuración solo se realiza una vez por tableta. Contacte al administrador si necesita el PIN.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}




