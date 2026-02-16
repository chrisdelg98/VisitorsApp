package com.eflglobal.visitorsapp.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eflglobal.visitorsapp.ui.localization.Strings
import com.eflglobal.visitorsapp.ui.theme.OrangePrimary
import com.eflglobal.visitorsapp.ui.theme.SlatePrimary
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutQrScreen(
    onFinish: () -> Unit,
    onBack: () -> Unit,
    selectedLanguage: String = "es"
) {
    var isScanning by remember { mutableStateOf(false) }
    var scanSuccess by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var visitorName by remember { mutableStateOf("") }
    var checkoutTime by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var showSearchResults by remember { mutableStateOf(false) }

    // Resultados simulados de búsqueda (solo visitas activas del día)
    val mockSearchResults = if (searchQuery.length >= 3) {
        listOf(
            VisitSearchResult(
                visitorName = "Juan Carlos Pérez",
                visitingPerson = "María García",
                entryTime = "10:30 AM",
                isActive = true
            ),
            VisitSearchResult(
                visitorName = "Ana Sofía Martínez",
                visitingPerson = "Roberto López",
                entryTime = "11:15 AM",
                isActive = true
            ),
            VisitSearchResult(
                visitorName = "Carlos Antonio Gómez",
                visitingPerson = "Laura Hernández",
                entryTime = "09:45 AM",
                isActive = true
            )
        ).filter { it.visitorName.contains(searchQuery, ignoreCase = true) }
    } else emptyList()

    LaunchedEffect(scanSuccess) {
        if (scanSuccess) {
            delay(500)
            showSuccessDialog = true
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

                    // Resultados de búsqueda
                    if (showSearchResults && mockSearchResults.isNotEmpty()) {
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

                                mockSearchResults.forEach { result ->
                                    VisitorSearchCard(
                                        result = result,
                                        onEndVisit = {
                                            visitorName = result.visitorName
                                            checkoutTime = "10:45 AM"
                                            scanSuccess = true
                                        },
                                        selectedLanguage = selectedLanguage
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }
                        }
                    } else if (showSearchResults && mockSearchResults.isEmpty()) {
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

                // ===== COLUMNA DERECHA (60%) - Área de Escaneo QR (Más Pequeña) =====
                Column(
                    modifier = Modifier
                        .weight(6f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Card del área de escaneo (más compacto)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.65f)
                            .aspectRatio(1f),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (scanSuccess)
                                OrangePrimary.copy(alpha = 0.05f)
                            else
                                MaterialTheme.colorScheme.surface
                        ),
                        border = if (scanSuccess)
                            BorderStroke(3.dp, OrangePrimary)
                        else
                            BorderStroke(2.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                        elevation = CardDefaults.cardElevation(
                            defaultElevation = 4.dp
                        )
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            AnimatedContent(
                                targetState = when {
                                    isScanning -> "scanning"
                                    scanSuccess -> "success"
                                    else -> "idle"
                                },
                                transitionSpec = {
                                    fadeIn() togetherWith fadeOut()
                                },
                                label = "QR Scan State"
                            ) { state ->
                                when (state) {
                                    "scanning" -> {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(20.dp)
                                        ) {
                                            CircularProgressIndicator(
                                                color = OrangePrimary,
                                                modifier = Modifier.size(64.dp),
                                                strokeWidth = 6.dp
                                            )
                                            Text(
                                                text = Strings.scanning(selectedLanguage),
                                                style = MaterialTheme.typography.titleLarge.copy(fontSize = 16.sp),
                                                fontWeight = FontWeight.Bold,
                                                color = SlatePrimary
                                            )
                                            Text(
                                                text = if (selectedLanguage == "es")
                                                    "Escaneando con cámara frontal..."
                                                else
                                                    "Scanning with front camera...",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }

                                    "success" -> {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
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

                                            Text(
                                                text = Strings.validCode(selectedLanguage),
                                                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 18.sp),
                                                fontWeight = FontWeight.Bold,
                                                color = OrangePrimary
                                            )

                                            Text(
                                                text = Strings.processingCheckout(selectedLanguage),
                                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 12.sp),
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.padding(horizontal = 20.dp)
                                            )
                                        }
                                    }

                                    else -> {
                                        // Estado inicial - Preview de escaneo
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(16.dp),
                                            modifier = Modifier.padding(24.dp)
                                        ) {
                                            // Marco de escaneo
                                            Box(
                                                modifier = Modifier
                                                    .size(140.dp)
                                                    .border(
                                                        width = 3.dp,
                                                        color = SlatePrimary.copy(alpha = 0.3f),
                                                        shape = RoundedCornerShape(20.dp)
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.QrCodeScanner,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                                    modifier = Modifier.size(80.dp)
                                                )
                                            }

                                            Text(
                                                text = Strings.placeQR(selectedLanguage),
                                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                                                fontWeight = FontWeight.Medium,
                                                color = SlatePrimary,
                                                textAlign = TextAlign.Center
                                            )

                                            Text(
                                                text = if (selectedLanguage == "es")
                                                    "El código QR se detectará automáticamente"
                                                else
                                                    "QR code will be detected automatically",
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.padding(horizontal = 12.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Texto informativo debajo del área de escaneo
                    Text(
                        text = if (selectedLanguage == "es")
                            "Cámara frontal activa"
                        else
                            "Front camera active",
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

// Data class para resultados de búsqueda
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

