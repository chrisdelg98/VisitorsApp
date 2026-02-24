package com.eflglobal.visitorsapp.ui.screens

import android.graphics.Bitmap
import androidx.camera.core.CameraSelector
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraEnhance
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eflglobal.visitorsapp.core.ocr.OCRHelper
import com.eflglobal.visitorsapp.core.utils.ImageSaver
import com.eflglobal.visitorsapp.ui.components.CameraPermissionHandler
import com.eflglobal.visitorsapp.ui.components.CameraPreviewComposable
import com.eflglobal.visitorsapp.ui.theme.OrangePrimary
import com.eflglobal.visitorsapp.ui.theme.SlatePrimary
import com.eflglobal.visitorsapp.ui.viewmodel.NewVisitViewModel
import com.eflglobal.visitorsapp.ui.viewmodel.ViewModelFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

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
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

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

    // Estados para las imágenes capturadas (para preview)
    var frontBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var backBitmap by remember { mutableStateOf<Bitmap?>(null) }


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
                    .verticalScroll(rememberScrollState())
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
                        selectedLanguage = selectedLanguage,
                        capturedBitmap = frontBitmap
                    )

                    // Card para reverso
                    ScanDocumentCard(
                        title = if (selectedLanguage == "es") "Reverso del Documento" else "Back of Document",
                        isScanned = backScanned,
                        onClick = { showBackCameraModal = true },
                        modifier = Modifier.weight(1f),
                        selectedLanguage = selectedLanguage,
                        enabled = frontScanned,
                        capturedBitmap = backBitmap
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

            // Modal de cámara REAL para frente del documento
            if (showFrontCameraModal) {
                RealDocumentCameraModal(
                    title = if (selectedLanguage == "es") "Frente del Documento" else "Front of Document",
                    instruction = if (selectedLanguage == "es")
                        "Coloque el frente del documento dentro del marco.\nAsegúrese de que esté completamente visible y sin reflejos."
                    else
                        "Place the front of the document inside the frame.\nMake sure it is fully visible and glare-free.",
                    onDismiss = { showFrontCameraModal = false },
                    onCapture = { bitmap ->
                        coroutineScope.launch {
                            try {
                                // Guardar bitmap para preview
                                frontBitmap = bitmap

                                // Guardar imagen
                                val personId = viewModel.getPersonId()
                                val savedImagePath = ImageSaver.saveImage(
                                    context = context,
                                    bitmap = bitmap,
                                    personId = personId,
                                    imageType = ImageSaver.ImageType.DOCUMENT_FRONT
                                )

                                // Procesar con ML Kit OCR mejorado
                                val ocrResult = OCRHelper.processDocument(bitmap)

                                // Log para debugging (remover en producción)
                                println("OCR Result - Full Text: ${ocrResult.fullText}")
                                println("OCR Result - Detected Name: ${ocrResult.detectedName}")
                                println("OCR Result - Detected Doc Number: ${ocrResult.detectedDocumentNumber}")
                                println("OCR Result - Confidence: ${ocrResult.confidence}")
                                println("OCR Result - Sharpness: ${ocrResult.sharpness}")

                                // Si el OCR detecta datos, usarlos; sino, generar valores genéricos
                                val detectedName = ocrResult.detectedName?.takeIf { it.isNotBlank() }
                                    ?: if (selectedLanguage == "es") "Visitante Internacional" else "International Visitor"

                                val detectedDocNumber = ocrResult.detectedDocumentNumber?.takeIf { it.isNotBlank() }
                                    ?: "DOC-${UUID.randomUUID().toString().take(8).uppercase()}"

                                // Guardar en ViewModel
                                viewModel.setDocumentFront(
                                    path = savedImagePath,
                                    name = detectedName,
                                    docNumber = detectedDocNumber
                                )

                                frontScanned = true
                                showFrontCameraModal = false
                            } catch (e: Exception) {
                                e.printStackTrace()
                                // Incluso si hay error, generar valores por defecto
                                val genericName = if (selectedLanguage == "es") "Visitante" else "Visitor"
                                val genericDoc = "DOC-${UUID.randomUUID().toString().take(8).uppercase()}"

                                viewModel.setDocumentFront(
                                    path = "",
                                    name = genericName,
                                    docNumber = genericDoc
                                )

                                frontScanned = true
                                showFrontCameraModal = false
                            }
                        }
                    },
                    selectedLanguage = selectedLanguage
                )
            }

            // Modal de cámara REAL para reverso del documento
            if (showBackCameraModal) {
                RealDocumentCameraModal(
                    title = if (selectedLanguage == "es") "Reverso del Documento" else "Back of Document",
                    instruction = if (selectedLanguage == "es")
                        "Coloque el reverso del documento dentro del marco.\nAsegúrese de que esté completamente visible y sin reflejos."
                    else
                        "Place the back of the document inside the frame.\nMake sure it is fully visible and glare-free.",
                    onDismiss = { showBackCameraModal = false },
                    onCapture = { bitmap ->
                        coroutineScope.launch {
                            try {
                                // Guardar bitmap para preview
                                backBitmap = bitmap

                                // Guardar imagen
                                val personId = viewModel.getPersonId()
                                val savedImagePath = ImageSaver.saveImage(
                                    context = context,
                                    bitmap = bitmap,
                                    personId = personId,
                                    imageType = ImageSaver.ImageType.DOCUMENT_BACK
                                )

                                // Guardar en ViewModel
                                viewModel.setDocumentBack(savedImagePath)

                                backScanned = true
                                showBackCameraModal = false
                            } catch (e: Exception) {
                                e.printStackTrace()
                                backScanned = true
                                showBackCameraModal = false
                            }
                        }
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
    enabled: Boolean = true,
    capturedBitmap: Bitmap? = null
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
            // Mostrar imagen de fondo si está escaneada
            if (isScanned && capturedBitmap != null) {
                Image(
                    bitmap = capturedBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Overlay naranja semitransparente
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(OrangePrimary.copy(alpha = 0.25f))
                )
            }

            // Contenido superpuesto
            if (isScanned) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier
                            .size(56.dp)
                            .background(
                                OrangePrimary.copy(alpha = 0.9f),
                                shape = CircleShape
                            )
                            .padding(12.dp)
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                        fontWeight = FontWeight.Bold,
                        color = androidx.compose.ui.graphics.Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .background(
                                SlatePrimary.copy(alpha = 0.85f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                    Text(
                        text = if (selectedLanguage == "es") "✓ Escaneado correctamente" else "✓ Scanned successfully",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                        color = androidx.compose.ui.graphics.Color.White,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(
                                OrangePrimary.copy(alpha = 0.85f),
                                shape = RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
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

/**
 * Modal de cámara REAL con CameraX para capturar documentos.
 * Captura automáticamente cuando detecta que la imagen no está borrosa.
 */
@Composable
private fun RealDocumentCameraModal(
    title: String,
    instruction: String,
    onDismiss: () -> Unit,
    onCapture: (Bitmap) -> Unit,
    selectedLanguage: String = "es"
) {
    var isProcessing by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var showSuccess by remember { mutableStateOf(false) }
    var captureTrigger by remember { mutableStateOf(false) }
    var alreadyCaptured by remember { mutableStateOf(false) }
    var retryCount by remember { mutableStateOf(0) }  // máximo 1 reintento

    val coroutineScope = rememberCoroutineScope()

    // Captura automática después de 2 segundos
    LaunchedEffect(Unit) {
        delay(2000)
        if (!alreadyCaptured) {
            captureTrigger = true
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {

        // ── Cámara ──────────────────────────────────────────────────────────
        CameraPermissionHandler(
            onPermissionGranted = {},
            onPermissionDenied = { onDismiss() }
        ) {
            CameraPreviewComposable(
                onImageCaptured = { bitmap ->
                    if (alreadyCaptured) return@CameraPreviewComposable
                    alreadyCaptured = true
                    isProcessing = true

                    coroutineScope.launch {
                        try {
                            val sharpness = OCRHelper.calculateSharpness(bitmap)
                            println("📸 Sharpness: $sharpness  |  retry: $retryCount")

                            // Solo rechazar si está EXTREMADAMENTE borrosa Y aún no hemos reintentado
                            if (sharpness < 5f && retryCount < 1) {
                                retryCount++
                                isProcessing = false
                                alreadyCaptured = false
                                showError = true
                                errorMessage = if (selectedLanguage == "es")
                                    "Imagen borrosa, reintentando..."
                                else
                                    "Blurry image, retrying..."
                                delay(1200)
                                showError = false
                                delay(400)
                                captureTrigger = true
                                return@launch
                            }

                            // Cualquier otro caso → aceptar la imagen y continuar
                            isProcessing = false
                            showSuccess = true
                            delay(600)
                            onCapture(bitmap)

                        } catch (e: Exception) {
                            e.printStackTrace()
                            // Si falla el OCR igual entregamos la imagen
                            isProcessing = false
                            onCapture(bitmap)
                        }
                    }
                },
                onError = { onDismiss() },
                lensFacing = CameraSelector.LENS_FACING_BACK,
                modifier = Modifier.fillMaxSize(),
                shouldCapture = captureTrigger,
                onCaptureComplete = { captureTrigger = false }
            )
        }

        // ── Overlay ─────────────────────────────────────────────────────────
        // Título arriba
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
            fontWeight = FontWeight.Bold,
            color = androidx.compose.ui.graphics.Color.White,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 32.dp)
                .background(
                    androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 20.dp, vertical = 10.dp)
        )

        // Marco guía del documento (centrado)
        Box(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .aspectRatio(1.6f)
                .align(Alignment.Center)
                .border(
                    width = 3.dp,
                    color = when {
                        showError   -> androidx.compose.ui.graphics.Color.Red
                        showSuccess -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
                        else        -> OrangePrimary
                    },
                    shape = RoundedCornerShape(14.dp)
                )
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val len = 50f
                val sw  = 8f
                val col = when {
                    showError   -> androidx.compose.ui.graphics.Color.Red
                    showSuccess -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
                    else        -> OrangePrimary
                }
                // Esquina superior izquierda
                drawLine(col, Offset(0f, len), Offset(0f, 0f), sw)
                drawLine(col, Offset(0f, 0f), Offset(len, 0f), sw)
                // Esquina superior derecha
                drawLine(col, Offset(size.width - len, 0f), Offset(size.width, 0f), sw)
                drawLine(col, Offset(size.width, 0f), Offset(size.width, len), sw)
                // Esquina inferior izquierda
                drawLine(col, Offset(0f, size.height - len), Offset(0f, size.height), sw)
                drawLine(col, Offset(0f, size.height), Offset(len, size.height), sw)
                // Esquina inferior derecha
                drawLine(col, Offset(size.width - len, size.height), Offset(size.width, size.height), sw)
                drawLine(col, Offset(size.width, size.height - len), Offset(size.width, size.height), sw)
            }
        }

        // ── Mensaje de estado CENTRADO en pantalla ───────────────────────────
        when {
            showError -> {
                Card(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth(0.75f),
                    colors = CardDefaults.cardColors(
                        containerColor = androidx.compose.ui.graphics.Color.Red.copy(alpha = 0.92f)
                    ),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(16.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraEnhance,
                            contentDescription = null,
                            tint = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier.size(52.dp)
                        )
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                            color = androidx.compose.ui.graphics.Color.White,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 28.sp
                        )
                    }
                }
            }
            showSuccess -> {
                Card(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth(0.75f),
                    colors = CardDefaults.cardColors(
                        containerColor = androidx.compose.ui.graphics.Color(0xFF2E7D32).copy(alpha = 0.95f)
                    ),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(16.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier.size(52.dp)
                        )
                        Text(
                            text = if (selectedLanguage == "es") "¡Documento capturado!" else "Document captured!",
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                            color = androidx.compose.ui.graphics.Color.White,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            isProcessing -> {
                Card(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth(0.75f),
                    colors = CardDefaults.cardColors(
                        containerColor = OrangePrimary.copy(alpha = 0.95f)
                    ),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(16.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            color = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier.size(52.dp),
                            strokeWidth = 5.dp
                        )
                        Text(
                            text = if (selectedLanguage == "es") "Procesando..." else "Processing...",
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                            color = androidx.compose.ui.graphics.Color.White,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (selectedLanguage == "es") "Verificando calidad" else "Checking quality",
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.9f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            else -> {
                // Instrucción mientras espera capturar
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(0.85f)
                        .padding(bottom = 72.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.75f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = instruction,
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                            color = androidx.compose.ui.graphics.Color.White,
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp
                        )
                        Text(
                            text = if (selectedLanguage == "es")
                                "📷 Capturando automáticamente en un momento..."
                            else
                                "📷 Auto-capturing in a moment...",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                            color = OrangePrimary,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Botón cancelar (abajo, siempre visible mientras no procesa)
        if (!isProcessing && !showSuccess) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = androidx.compose.ui.graphics.Color.White
                ),
                border = BorderStroke(2.dp, androidx.compose.ui.graphics.Color.White),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = if (selectedLanguage == "es") "Cancelar" else "Cancel",
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp)
                )
            }
        }
    }
}
