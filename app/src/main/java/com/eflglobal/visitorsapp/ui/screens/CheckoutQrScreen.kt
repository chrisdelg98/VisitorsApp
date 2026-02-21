package com.eflglobal.visitorsapp.ui.screens

import androidx.camera.core.CameraSelector
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eflglobal.visitorsapp.ui.components.CameraPermissionHandler
import com.eflglobal.visitorsapp.ui.components.QRScannerComposable
import com.eflglobal.visitorsapp.ui.localization.Strings
import com.eflglobal.visitorsapp.ui.theme.OrangePrimary
import com.eflglobal.visitorsapp.ui.theme.SlatePrimary
import com.eflglobal.visitorsapp.ui.viewmodel.EndVisitViewModel
import com.eflglobal.visitorsapp.ui.viewmodel.EndVisitUiState
import com.eflglobal.visitorsapp.ui.viewmodel.ViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutQrScreen(
    onFinish: () -> Unit,
    onBack: () -> Unit,
    viewModel: EndVisitViewModel? = null,
    selectedLanguage: String = "es"
) {
    val context = LocalContext.current
    val actualViewModel = viewModel ?: viewModel(factory = ViewModelFactory(context))
    val uiState by actualViewModel.uiState.collectAsState()

    var isScanning by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var successVisitorName by remember { mutableStateOf("") }
    var showQRScanner by remember { mutableStateOf(false) }

    // Búsqueda en tiempo real
    LaunchedEffect(searchQuery) {
        if (searchQuery.length >= 3) {
            actualViewModel.searchActiveVisits(searchQuery)
        }
    }

    // Mostrar diálogo de éxito cuando se marca salida
    LaunchedEffect(uiState) {
        when (uiState) {
            is EndVisitUiState.Success -> {
                showSuccessDialog = true
                showQRScanner = false
            }
            is EndVisitUiState.QRSuccess -> {
                showSuccessDialog = true
                showQRScanner = false
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        Strings.endVisitTitle(selectedLanguage),
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Strings.back(selectedLanguage))
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
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 40.dp, vertical = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(40.dp)
            ) {
                // ===== COLUMNA IZQUIERDA (40%) - Salida Manual con Búsqueda =====
                Column(
                    modifier = Modifier
                        .weight(4f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.Top
                ) {
                    // Título de sección
                    Text(
                        text = if (selectedLanguage == "es") "Salida Manual" else "Manual Checkout",
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                        fontWeight = FontWeight.Bold,
                        color = SlatePrimary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Instrucciones
                    Text(
                        text = if (selectedLanguage == "es")
                            "Escanee el código QR del visitante o búsquelo manualmente por su nombre para completar la salida."
                        else
                            "Scan the visitor's QR code or search manually by name to complete checkout.",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    // Campo de búsqueda
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = if (selectedLanguage == "es")
                                "Buscar Visitante por Nombre"
                            else
                                "Search Visitor by Name",
                            style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
                            fontWeight = FontWeight.SemiBold,
                            color = SlatePrimary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = {
                                searchQuery = it
                                showSearchResults = it.length >= 3
                            },
                            placeholder = {
                                Text(
                                    if (selectedLanguage == "es")
                                        "Ingrese al menos 3 caracteres..."
                                    else
                                        "Enter at least 3 characters...",
                                    fontSize = 12.sp
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = OrangePrimary,
                                unfocusedBorderColor = SlatePrimary.copy(alpha = 0.3f)
                            ),
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = SlatePrimary.copy(alpha = 0.6f)
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = {
                                        searchQuery = ""
                                        showSearchResults = false
                                    }) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Clear",
                                            tint = SlatePrimary.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            },
                            singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Resultados de búsqueda REALES desde ViewModel
                    when (val state = uiState) {
                        is EndVisitUiState.Loading -> {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = OrangePrimary)
                            }
                        }

                        is EndVisitUiState.SearchResults -> {
                            if (state.visits.isNotEmpty()) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    ),
                                    elevation = CardDefaults.cardElevation(
                                        defaultElevation = 4.dp
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp)
                                    ) {
                                        Text(
                                            text = if (selectedLanguage == "es")
                                                "Visitas Activas Hoy"
                                            else
                                                "Active Visits Today",
                                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                                            fontWeight = FontWeight.Bold,
                                            color = OrangePrimary,
                                            modifier = Modifier.padding(bottom = 12.dp)
                                        )

                                        state.visits.forEach { visit ->
                                            RealVisitorSearchCard(
                                                visit = visit,
                                                onEndVisit = {
                                                    successVisitorName = visit.personName
                                                    actualViewModel.endVisit(visit.visitId)
                                                },
                                                selectedLanguage = selectedLanguage
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                        }
                                    }
                                }
                            } else if (searchQuery.length >= 3) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    )
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (selectedLanguage == "es")
                                                "No se encontraron visitas activas"
                                            else
                                                "No active visits found",
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }

                        is EndVisitUiState.Error -> {
                            Text(
                                text = state.message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        else -> { /* Idle state */ }
                    }
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Información adicional sobre escaneo QR
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = SlatePrimary.copy(alpha = 0.05f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, SlatePrimary.copy(alpha = 0.1f))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "📷",
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 20.sp)
                            )
                            Text(
                                text = if (selectedLanguage == "es")
                                    "El escaneo QR se activará automáticamente con la cámara frontal"
                                else
                                    "QR scanning will activate automatically with front camera",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                // ===== COLUMNA DERECHA (60%) - Área de Escaneo QR REAL =====
                Column(
                    modifier = Modifier
                        .weight(6f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Card del área de escaneo REAL con cámara
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.75f)
                            .aspectRatio(1f),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = androidx.compose.ui.graphics.Color.Black
                        ),
                        elevation = CardDefaults.cardElevation(
                            defaultElevation = 8.dp
                        )
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            if (showQRScanner) {
                                // Scanner REAL con CameraX + ZXing
                                CameraPermissionHandler(
                                    onPermissionGranted = {},
                                    onPermissionDenied = { showQRScanner = false }
                                ) {
                                    QRScannerComposable(
                                        onQRScanned = { qrCode ->
                                            // Extraer nombre del visitante del QR si es posible
                                            // Por ahora usamos el QR code directamente
                                            actualViewModel.scanQRCode(qrCode)
                                            isScanning = false
                                        },
                                        onError = {
                                            showQRScanner = false
                                        },
                                        lensFacing = CameraSelector.LENS_FACING_FRONT,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                // Overlay con marco de guía
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize(0.7f)
                                            .border(
                                                width = 4.dp,
                                                color = OrangePrimary,
                                                shape = RoundedCornerShape(20.dp)
                                            )
                                    )

                                    // Texto instructivo
                                    Column(
                                        modifier = Modifier
                                            .align(Alignment.TopCenter)
                                            .padding(top = 16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = if (selectedLanguage == "es")
                                                "Coloque el código QR en el área marcada"
                                            else
                                                "Place QR code in marked area",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = androidx.compose.ui.graphics.Color.White,
                                            modifier = Modifier
                                                .background(
                                                    androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f),
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                        )
                                    }

                                    // Botón cerrar
                                    IconButton(
                                        onClick = { showQRScanner = false },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(8.dp)
                                            .background(
                                                androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f),
                                                shape = androidx.compose.foundation.shape.CircleShape
                                            )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Close",
                                            tint = androidx.compose.ui.graphics.Color.White
                                        )
                                    }
                                }
                            } else {
                                // Estado inicial - Mostrar instrucción y botón
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(24.dp),
                                    modifier = Modifier.padding(24.dp)
                                ) {
                                    // Icono grande
                                    Box(
                                        modifier = Modifier
                                            .size(120.dp)
                                            .background(
                                                color = OrangePrimary.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(60.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.QrCodeScanner,
                                            contentDescription = null,
                                            tint = OrangePrimary,
                                            modifier = Modifier.size(64.dp)
                                        )
                                    }

                                    Text(
                                        text = if (selectedLanguage == "es")
                                            "Escanear Código QR"
                                        else
                                            "Scan QR Code",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = androidx.compose.ui.graphics.Color.White
                                    )

                                    Text(
                                        text = if (selectedLanguage == "es")
                                            "Presione el botón para activar la cámara y escanear el código QR del visitante"
                                        else
                                            "Press the button to activate camera and scan visitor's QR code",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f),
                                        textAlign = TextAlign.Center
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Botón para activar scanner
                                    Button(
                                        onClick = { showQRScanner = true },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = OrangePrimary
                                        ),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier
                                            .fillMaxWidth(0.8f)
                                            .height(56.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.QrCodeScanner,
                                            contentDescription = null,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = if (selectedLanguage == "es")
                                                "Activar Escáner"
                                            else
                                                "Activate Scanner",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Texto informativo
                    Text(
                        text = if (selectedLanguage == "es")
                            "Cámara frontal • Escaneo automático"
                        else
                            "Front camera • Automatic scanning",
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Diálogo de éxito al finalizar
            if (showSuccessDialog) {
                AlertDialog(
                    onDismissRequest = { },
                    icon = {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .background(
                                    color = OrangePrimary.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(40.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = OrangePrimary,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    },
                    title = {
                        Text(
                            text = Strings.checkoutSuccess(selectedLanguage),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = SlatePrimary,
                            textAlign = TextAlign.Center
                        )
                    },
                    text = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (selectedLanguage == "es")
                                    "Salida registrada correctamente para:"
                                else
                                    "Checkout successfully registered for:",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = successVisitorName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = OrangePrimary,
                                textAlign = TextAlign.Center
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showSuccessDialog = false
                                actualViewModel.resetState()
                                onFinish()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = OrangePrimary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(Strings.accept(selectedLanguage))
                        }
                    }
                )
            }
                                .size(80.dp)
                                .background(
                                    color = OrangePrimary.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(40.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = OrangePrimary,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    },
                    title = {
                        Text(
                            text = Strings.checkoutRegistered(selectedLanguage),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    text = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (visitorName.isNotEmpty()) {
                                Text(
                                    text = visitorName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = SlatePrimary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            Text(
                                text = "${Strings.checkoutTime(selectedLanguage)}: $checkoutTime",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = Strings.thanksForVisit(selectedLanguage),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showSuccessDialog = false
                                onFinish()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = OrangePrimary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = Strings.finish(selectedLanguage),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    },
                    shape = RoundedCornerShape(24.dp)
                )
            }
        }
    }
}

/**
 * Composable para mostrar resultados REALES de visitas activas desde ViewModel.
 */
@Composable
private fun RealVisitorSearchCard(
    visit: com.eflglobal.visitorsapp.domain.model.ActiveVisit,
    onEndVisit: () -> Unit,
    selectedLanguage: String
) {
    Card(
        onClick = onEndVisit,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = SlatePrimary.copy(alpha = 0.05f)
        ),
        border = BorderStroke(1.dp, SlatePrimary.copy(alpha = 0.15f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icono de usuario
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = OrangePrimary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(24.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = OrangePrimary,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Información de la visita
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = visit.personName,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.sp),
                    fontWeight = FontWeight.Bold,
                    color = SlatePrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (selectedLanguage == "es")
                        "Visitando a: ${visit.visitingPerson}"
                    else
                        "Visiting: ${visit.visitingPerson}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (selectedLanguage == "es")
                        "Entrada: ${visit.entryTime}"
                    else
                        "Entry: ${visit.entryTime}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }

            // Botón de salida
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                contentDescription = "End Visit",
                tint = OrangePrimary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

// Data class para resultados de búsqueda (legacy, mantener por compatibilidad)
data class VisitSearchResult(
    val visitorName: String,
    val visitingPerson: String,
    val entryTime: String,
    val isActive: Boolean
)

// Composable para tarjeta de resultado de búsqueda
@Composable
private fun VisitorSearchCard(
    result: VisitSearchResult,
    onEndVisit: () -> Unit,
    selectedLanguage: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = OrangePrimary.copy(alpha = 0.08f)
        ),
        border = BorderStroke(1.dp, OrangePrimary.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Info del visitante
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = result.visitorName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                    fontWeight = FontWeight.Bold,
                    color = SlatePrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${if (selectedLanguage == "en") "Visiting" else "Visita a"}: ${result.visitingPerson}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${if (selectedLanguage == "en") "Entry" else "Entrada"}: ${result.entryTime}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }

            // Botón de finalizar
            Button(
                onClick = onEndVisit,
                modifier = Modifier
                    .height(36.dp)
                    .padding(start = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = OrangePrimary
                ),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = if (selectedLanguage == "en") "End" else "Finalizar",
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

