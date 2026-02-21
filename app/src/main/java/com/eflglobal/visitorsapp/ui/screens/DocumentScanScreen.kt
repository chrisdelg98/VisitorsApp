package com.eflglobal.visitorsapp.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraEnhance
import androidx.compose.material.icons.filled.CheckCircle
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
import com.eflglobal.visitorsapp.ui.theme.OrangePrimary
import com.eflglobal.visitorsapp.ui.theme.SlatePrimary
import com.eflglobal.visitorsapp.ui.viewmodel.NewVisitViewModel
import com.eflglobal.visitorsapp.ui.viewmodel.ViewModelFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentScanScreen(
    onContinue: () -> Unit,
    onBack: () -> Unit,
    selectedLanguage: String = "es",
    viewModel: NewVisitViewModel = viewModel(
        factory = ViewModelFactory(LocalContext.current)
    )
) {
    var selectedDocType by remember { mutableStateOf("DUI") }

    val visitorTypes = if (selectedLanguage == "es") {
        listOf("Visitante", "Contratista", "Proveedor", "Haciendo una entrega")
    } else {
        listOf("Visitor", "Contractor", "Supplier", "Making a delivery")
    }

    // Establecer valor predeterminado a la primera opción
    var selectedVisitorType by remember { mutableStateOf(visitorTypes.first()) }

    // Actualizar el ViewModel cuando cambia el tipo de documento o visitante
    LaunchedEffect(selectedDocType) {
        viewModel.setDocumentType(selectedDocType)
    }

    LaunchedEffect(selectedVisitorType) {
        viewModel.setVisitorType(selectedVisitorType)
    }
    var expandedVisitorType by remember { mutableStateOf(false) }
    var frontScanned by remember { mutableStateOf(false) }
    var backScanned by remember { mutableStateOf(false) }
    var showFrontCameraModal by remember { mutableStateOf(false) }
    var showBackCameraModal by remember { mutableStateOf(false) }


    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (selectedLanguage == "es") "Escanear Documento" else "Scan Document",
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = if (selectedLanguage == "es") "Atrás" else "Back")
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
                    .padding(24.dp)
            ) {
                // Sección superior: Yo soy un
                Text(
                    text = if (selectedLanguage == "es") "Yo soy un:" else "I am a:",
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = SlatePrimary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Dropdown de tipo de visitante
                ExposedDropdownMenuBox(
                    expanded = expandedVisitorType,
                    onExpandedChange = { expandedVisitorType = !expandedVisitorType },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                ) {
                    OutlinedTextField(
                        value = selectedVisitorType,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(if (selectedLanguage == "es") "Seleccione una opción" else "Select an option", fontSize = 12.sp) },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedVisitorType)
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SlatePrimary,
                            focusedLabelColor = SlatePrimary
                        ),
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
                    )

                    ExposedDropdownMenu(
                        expanded = expandedVisitorType,
                        onDismissRequest = { expandedVisitorType = false }
                    ) {
                        visitorTypes.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type, fontSize = 14.sp) },
                                onClick = {
                                    selectedVisitorType = type
                                    expandedVisitorType = false
                                }
                            )
                        }
                    }
                }

                // Sección: Tipo de documento
                Text(
                    text = if (selectedLanguage == "es") "¿Qué tipo de documento presenta?" else "What type of document do you present?",
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = SlatePrimary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Selector de tipo de documento
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DocumentTypeChip(
                        text = "DUI / ID",
                        isSelected = selectedDocType == "DUI",
                        onClick = { selectedDocType = "DUI" },
                        modifier = Modifier.weight(1f)
                    )
                    DocumentTypeChip(
                        text = if (selectedLanguage == "es") "Pasaporte" else "Passport",
                        isSelected = selectedDocType == "PASSPORT",
                        onClick = { selectedDocType = "PASSPORT" },
                        modifier = Modifier.weight(1f)
                    )
                    DocumentTypeChip(
                        text = if (selectedLanguage == "es") "Otro" else "Other",
                        isSelected = selectedDocType == "OTHER",
                        onClick = { selectedDocType = "OTHER" },
                        modifier = Modifier.weight(1f)
                    )
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )

                // Sección inferior: Escaneo en dos columnas para tablet
                Text(
                    text = if (selectedLanguage == "es") "Escanear Documento" else "Scan Document",
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = SlatePrimary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Cards de escaneo en dos columnas
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Card para frente
                    ScanDocumentCard(
                        title = if (selectedLanguage == "es") "Frente del Documento" else "Front of Document",
                        isScanned = frontScanned,
                        onClick = { showFrontCameraModal = true },
                        modifier = Modifier.weight(1f),
                        selectedLanguage = selectedLanguage
                    )

                    // Card para reverso
                    ScanDocumentCard(
                        title = if (selectedLanguage == "es") "Reverso del Documento" else "Back of Document",
                        isScanned = backScanned,
                        onClick = { showBackCameraModal = true },
                        modifier = Modifier.weight(1f),
                        selectedLanguage = selectedLanguage,
                        enabled = frontScanned
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Botón continuar
                Button(
                    onClick = onContinue,
                    enabled = frontScanned && backScanned && selectedVisitorType.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = OrangePrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (selectedLanguage == "es") "Continuar" else "Continue",
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                    fontWeight = FontWeight.SemiBold
                )
                }
            }

            // Modal de cámara para frente del documento
            if (showFrontCameraModal) {
                DocumentCameraModal(
                    title = if (selectedLanguage == "es") "Frente del Documento" else "Front of Document",
                    instruction = if (selectedLanguage == "es")
                        "Coloque el frente del documento dentro del marco.\nAsegúrese de que esté completamente visible y sin reflejos."
                    else
                        "Place the front of the document inside the frame.\nMake sure it is fully visible and glare-free.",
                    onDismiss = { showFrontCameraModal = false },
                    onCapture = {
                        frontScanned = true
                        showFrontCameraModal = false
                        // TODO: Aquí se capturará la imagen real y se extraerá el texto con ML Kit
                        // Por ahora simulamos los datos
                        val imagePath = "document_front_${System.currentTimeMillis()}.jpg"
                        val detectedName = "" // OCR detectará el nombre
                        val docNumber = "" // OCR detectará el número
                        viewModel.setDocumentFront(imagePath, detectedName, docNumber)
                    },
                    selectedLanguage = selectedLanguage
                )
            }

            // Modal de cámara para reverso del documento
            if (showBackCameraModal) {
                DocumentCameraModal(
                    title = if (selectedLanguage == "es") "Reverso del Documento" else "Back of Document",
                    instruction = if (selectedLanguage == "es")
                        "Coloque el reverso del documento dentro del marco.\nAsegúrese de que esté completamente visible y sin reflejos."
                    else
                        "Place the back of the document inside the frame.\nMake sure it is fully visible and glare-free.",
                    onDismiss = { showBackCameraModal = false },
                    onCapture = {
                        backScanned = true
                        showBackCameraModal = false
                        // TODO: Aquí se capturará la imagen real
                        val imagePath = "document_back_${System.currentTimeMillis()}.jpg"
                        viewModel.setDocumentBack(imagePath)
                    },
                    selectedLanguage = selectedLanguage
                )
            }
        }
    }
}

@Composable
private fun DocumentTypeChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        },
        modifier = modifier.height(48.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = SlatePrimary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            containerColor = MaterialTheme.colorScheme.surface,
            labelColor = MaterialTheme.colorScheme.onSurface
        ),
        shape = RoundedCornerShape(12.dp),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = isSelected,
            borderColor = if (isSelected) SlatePrimary else MaterialTheme.colorScheme.outline,
            selectedBorderColor = SlatePrimary,
            borderWidth = 2.dp,
            selectedBorderWidth = 2.dp
        )
    )
}

@Composable
private fun ScanDocumentCard(
    title: String,
    isScanned: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectedLanguage: String = "es",
    enabled: Boolean = true
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxHeight(),
        enabled = enabled,
        colors = CardDefaults.cardColors(
            containerColor = if (isScanned)
                OrangePrimary.copy(alpha = 0.05f)
            else if (!enabled)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        border = if (isScanned)
            BorderStroke(2.dp, OrangePrimary)
        else
            BorderStroke(2.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (isScanned) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = OrangePrimary,
                        modifier = Modifier.size(56.dp)
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                        fontWeight = FontWeight.SemiBold,
                        color = SlatePrimary,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = if (selectedLanguage == "es") "✓ Escaneado correctamente" else "✓ Scanned successfully",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                        color = OrangePrimary,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraEnhance,
                        contentDescription = null,
                        tint = if (enabled)
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                        modifier = Modifier.size(56.dp)
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                        fontWeight = FontWeight.SemiBold,
                        color = if (enabled) SlatePrimary else SlatePrimary.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = if (selectedLanguage == "es")
                            if (enabled) "Toque para escanear" else "Escanee el frente primero"
                        else
                            if (enabled) "Tap to scan" else "Scan front first",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun DocumentCameraModal(
    title: String,
    instruction: String,
    onDismiss: () -> Unit,
    onCapture: () -> Unit,
    selectedLanguage: String = "es"
) {
    var isAnalyzing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Simular análisis y captura automática
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(2000) // Simular tiempo de análisis
        isAnalyzing = true
        kotlinx.coroutines.delay(1500) // Simular validación
        onCapture()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.95f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Título
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 20.sp),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Área de visualización de cámara simulada
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .aspectRatio(1.6f),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
                border = BorderStroke(
                    3.dp,
                    if (isAnalyzing) OrangePrimary else MaterialTheme.colorScheme.outline
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    // Simulación de vista de cámara en vivo
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        if (isAnalyzing) {
                            // Estado de análisis
                            CircularProgressIndicator(
                                color = OrangePrimary,
                                modifier = Modifier.size(60.dp),
                                strokeWidth = 5.dp
                            )
                            Text(
                                text = if (selectedLanguage == "es")
                                    "Analizando documento..."
                                else
                                    "Analyzing document...",
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                                fontWeight = FontWeight.SemiBold,
                                color = OrangePrimary,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = if (selectedLanguage == "es")
                                    "Verificando claridad y legibilidad"
                                else
                                    "Checking clarity and readability",
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center
                            )
                        } else {
                            // Estado de captura - muestra instrucciones
                            Icon(
                                imageVector = Icons.Default.CameraEnhance,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier.size(64.dp)
                            )
                            Text(
                                text = if (selectedLanguage == "es")
                                    "Vista de cámara en vivo"
                                else
                                    "Live camera view",
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        }
                    }

                    // Marco de guía para el documento (esquinas)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp)
                    ) {
                        // Esquina superior izquierda
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .align(Alignment.TopStart)
                                .background(
                                    color = if (isAnalyzing)
                                        OrangePrimary.copy(alpha = 0.8f)
                                    else
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(
                                        topStart = 8.dp,
                                        topEnd = 0.dp,
                                        bottomStart = 0.dp,
                                        bottomEnd = 0.dp
                                    )
                                )
                        )

                        // Esquina superior derecha
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .align(Alignment.TopEnd)
                                .background(
                                    color = if (isAnalyzing)
                                        OrangePrimary.copy(alpha = 0.8f)
                                    else
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(
                                        topStart = 0.dp,
                                        topEnd = 8.dp,
                                        bottomStart = 0.dp,
                                        bottomEnd = 0.dp
                                    )
                                )
                        )

                        // Esquina inferior izquierda
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .align(Alignment.BottomStart)
                                .background(
                                    color = if (isAnalyzing)
                                        OrangePrimary.copy(alpha = 0.8f)
                                    else
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(
                                        topStart = 0.dp,
                                        topEnd = 0.dp,
                                        bottomStart = 8.dp,
                                        bottomEnd = 0.dp
                                    )
                                )
                        )

                        // Esquina inferior derecha
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .align(Alignment.BottomEnd)
                                .background(
                                    color = if (isAnalyzing)
                                        OrangePrimary.copy(alpha = 0.8f)
                                    else
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(
                                        topStart = 0.dp,
                                        topEnd = 0.dp,
                                        bottomStart = 0.dp,
                                        bottomEnd = 8.dp
                                    )
                                )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Instrucciones
            Card(
                modifier = Modifier.fillMaxWidth(0.7f),
                colors = CardDefaults.cardColors(
                    containerColor = SlatePrimary.copy(alpha = 0.15f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = instruction,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp),
                    lineHeight = 20.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Botón de cancelar (solo visible antes de analizar)
            if (!isAnalyzing) {
                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        text = if (selectedLanguage == "es") "Cancelar" else "Cancel",
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}





