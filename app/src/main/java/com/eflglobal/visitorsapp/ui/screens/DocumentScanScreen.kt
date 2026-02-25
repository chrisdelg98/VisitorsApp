package com.eflglobal.visitorsapp.ui.screens

import android.graphics.Bitmap
import androidx.camera.core.CameraSelector
import androidx.compose.animation.core.*
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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eflglobal.visitorsapp.core.utils.ImageSaver
import com.eflglobal.visitorsapp.core.validation.DocumentValidator
import com.eflglobal.visitorsapp.ui.components.CameraPermissionHandler
import com.eflglobal.visitorsapp.ui.components.DocumentCameraPreviewComposable
import com.eflglobal.visitorsapp.ui.theme.OrangePrimary
import com.eflglobal.visitorsapp.ui.theme.SlatePrimary
import com.eflglobal.visitorsapp.ui.viewmodel.NewVisitViewModel
import com.eflglobal.visitorsapp.ui.viewmodel.ViewModelFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

// ═══════════════════════════════════════════════════════════════════════════════
// DocumentScanScreen
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentScanScreen(
    onContinue: () -> Unit,
    onBack: () -> Unit,
    selectedLanguage: String = "es",
    viewModel: NewVisitViewModel = viewModel(factory = ViewModelFactory(LocalContext.current))
) {
    val context       = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedDocType by remember { mutableStateOf("DUI") }

    val visitorTypes = if (selectedLanguage == "es")
        listOf("Visitante", "Contratista", "Proveedor", "Haciendo una entrega")
    else
        listOf("Visitor", "Contractor", "Supplier", "Making a delivery")

    var selectedVisitorType by remember { mutableStateOf(visitorTypes.first()) }
    var expandedVisitorType by remember { mutableStateOf(false) }

    LaunchedEffect(selectedDocType)    { viewModel.setDocumentType(selectedDocType) }
    LaunchedEffect(selectedVisitorType) { viewModel.setVisitorType(selectedVisitorType) }

    var frontScanned        by remember { mutableStateOf(false) }
    var backScanned         by remember { mutableStateOf(false) }
    var showFrontCamera     by remember { mutableStateOf(false) }
    var showBackCamera      by remember { mutableStateOf(false) }
    var frontBitmap         by remember { mutableStateOf<Bitmap?>(null) }
    var backBitmap          by remember { mutableStateOf<Bitmap?>(null) }

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
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (selectedLanguage == "es") "Atrás" else "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
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
                // ── Visitor type ──────────────────────────────────────────────
                Text(
                    text = if (selectedLanguage == "es") "Yo soy un:" else "I am a:",
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = SlatePrimary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                ExposedDropdownMenuBox(
                    expanded = expandedVisitorType,
                    onExpandedChange = { expandedVisitorType = !expandedVisitorType },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
                ) {
                    OutlinedTextField(
                        value = selectedVisitorType,
                        onValueChange = {},
                        readOnly = true,
                        label = {
                            Text(
                                if (selectedLanguage == "es") "Seleccione una opción" else "Select an option",
                                fontSize = 12.sp
                            )
                        },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedVisitorType) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SlatePrimary,
                            focusedLabelColor  = SlatePrimary
                        ),
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
                    )
                    ExposedDropdownMenu(
                        expanded = expandedVisitorType,
                        onDismissRequest = { expandedVisitorType = false }
                    ) {
                        visitorTypes.forEach { t ->
                            DropdownMenuItem(
                                text = { Text(t, fontSize = 14.sp) },
                                onClick = { selectedVisitorType = t; expandedVisitorType = false }
                            )
                        }
                    }
                }

                // ── Document type ─────────────────────────────────────────────
                Text(
                    text = if (selectedLanguage == "es") "¿Qué tipo de documento presenta?" else "What type of document do you present?",
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = SlatePrimary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DocumentTypeChip("DUI / ID",  selectedDocType == "DUI",      { selectedDocType = "DUI" },      Modifier.weight(1f))
                    DocumentTypeChip(if (selectedLanguage == "es") "Pasaporte" else "Passport", selectedDocType == "PASSPORT", { selectedDocType = "PASSPORT" }, Modifier.weight(1f))
                    DocumentTypeChip(if (selectedLanguage == "es") "Otro" else "Other",         selectedDocType == "OTHER",    { selectedDocType = "OTHER" },    Modifier.weight(1f))
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 16.dp))

                // ── Scan section ──────────────────────────────────────────────
                Text(
                    text = if (selectedLanguage == "es") "Escanear Documento" else "Scan Document",
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = SlatePrimary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ScanDocumentCard(
                        title          = if (selectedLanguage == "es") "Frente del Documento" else "Front of Document",
                        isScanned      = frontScanned,
                        onClick        = { showFrontCamera = true },
                        modifier       = Modifier.weight(1f),
                        selectedLanguage = selectedLanguage,
                        capturedBitmap = frontBitmap
                    )
                    ScanDocumentCard(
                        title          = if (selectedLanguage == "es") "Reverso del Documento" else "Back of Document",
                        isScanned      = backScanned,
                        onClick        = { showBackCamera = true },
                        modifier       = Modifier.weight(1f),
                        selectedLanguage = selectedLanguage,
                        enabled        = frontScanned,
                        capturedBitmap = backBitmap
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick  = onContinue,
                    enabled  = frontScanned && backScanned && selectedVisitorType.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor         = OrangePrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text       = if (selectedLanguage == "es") "Continuar" else "Continue",
                        style      = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // ── Front camera modal ─────────────────────────────────────────────
            if (showFrontCamera) {
                DocumentCameraModal(
                    title            = if (selectedLanguage == "es") "Frente del Documento" else "Front of Document",
                    isBackSide       = false,
                    referenceBitmap  = null,
                    selectedLanguage = selectedLanguage,
                    onDismiss        = { showFrontCamera = false },
                    onCapture        = { bitmap, ocrData ->
                        coroutineScope.launch {
                            try {
                                frontBitmap = bitmap
                                val personId = viewModel.getPersonId()
                                val path     = ImageSaver.saveImage(
                                    context   = context,
                                    bitmap    = bitmap,
                                    personId  = personId,
                                    imageType = ImageSaver.ImageType.DOCUMENT_FRONT
                                )
                                val name = ocrData?.detectedName?.takeIf { it.isNotBlank() }
                                    ?: if (selectedLanguage == "es") "Visitante Internacional" else "International Visitor"
                                val doc  = ocrData?.detectedDocNumber?.takeIf { it.isNotBlank() }
                                    ?: "DOC-${UUID.randomUUID().toString().take(8).uppercase()}"
                                viewModel.setDocumentFront(path = path, name = name, docNumber = doc)
                                frontScanned   = true
                                showFrontCamera = false
                            } catch (e: Exception) {
                                e.printStackTrace()
                                viewModel.setDocumentFront(
                                    path      = "",
                                    name      = if (selectedLanguage == "es") "Visitante" else "Visitor",
                                    docNumber = "DOC-${UUID.randomUUID().toString().take(8).uppercase()}"
                                )
                                frontScanned   = true
                                showFrontCamera = false
                            }
                        }
                    }
                )
            }

            // ── Back camera modal ──────────────────────────────────────────────
            if (showBackCamera) {
                DocumentCameraModal(
                    title            = if (selectedLanguage == "es") "Reverso del Documento" else "Back of Document",
                    isBackSide       = true,
                    referenceBitmap  = frontBitmap,
                    selectedLanguage = selectedLanguage,
                    onDismiss        = { showBackCamera = false },
                    onCapture        = { bitmap, _ ->
                        coroutineScope.launch {
                            try {
                                backBitmap = bitmap
                                val personId = viewModel.getPersonId()
                                val path     = ImageSaver.saveImage(
                                    context   = context,
                                    bitmap    = bitmap,
                                    personId  = personId,
                                    imageType = ImageSaver.ImageType.DOCUMENT_BACK
                                )
                                viewModel.setDocumentBack(path)
                                backScanned   = true
                                showBackCamera = false
                            } catch (e: Exception) {
                                e.printStackTrace()
                                backScanned   = true
                                showBackCamera = false
                            }
                        }
                    }
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// DocumentTypeChip
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun DocumentTypeChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = isSelected,
        onClick  = onClick,
        label    = {
            Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        },
        modifier = modifier.height(48.dp),
        colors   = FilterChipDefaults.filterChipColors(
            selectedContainerColor = SlatePrimary,
            selectedLabelColor     = MaterialTheme.colorScheme.onPrimary,
            containerColor         = MaterialTheme.colorScheme.surface,
            labelColor             = MaterialTheme.colorScheme.onSurface
        ),
        shape  = RoundedCornerShape(12.dp),
        border = FilterChipDefaults.filterChipBorder(
            enabled             = true,
            selected            = isSelected,
            borderColor         = if (isSelected) SlatePrimary else MaterialTheme.colorScheme.outline,
            selectedBorderColor = SlatePrimary,
            borderWidth         = 2.dp,
            selectedBorderWidth = 2.dp
        )
    )
}

// ═══════════════════════════════════════════════════════════════════════════════
// ScanDocumentCard
// ═══════════════════════════════════════════════════════════════════════════════

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
        onClick  = onClick,
        modifier = modifier.fillMaxHeight(),
        enabled  = enabled,
        colors   = CardDefaults.cardColors(
            containerColor = when {
                isScanned  -> OrangePrimary.copy(alpha = 0.05f)
                !enabled   -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                else       -> MaterialTheme.colorScheme.surface
            }
        ),
        shape  = RoundedCornerShape(16.dp),
        border = if (isScanned)
            BorderStroke(2.dp, OrangePrimary)
        else
            BorderStroke(2.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // Mostrar imagen de fondo si está escaneada
            if (isScanned && capturedBitmap != null) {
                Image(
                    bitmap       = capturedBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier     = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Overlay naranja semitransparente
                Box(
                    modifier = Modifier.fillMaxSize().background(OrangePrimary.copy(alpha = 0.25f))
                )
            }

            // Contenido superpuesto
            if (isScanned) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize().padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint     = Color.White,
                        modifier = Modifier.size(56.dp)
                            .background(OrangePrimary.copy(alpha = 0.9f), CircleShape)
                            .padding(12.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text  = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .background(SlatePrimary.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text  = if (selectedLanguage == "es") "✓ Escaneado correctamente" else "✓ Scanned successfully",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(OrangePrimary.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
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
                        tint     = if (enabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                   else         MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                        modifier = Modifier.size(56.dp)
                    )
                    Text(
                        text  = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                        fontWeight = FontWeight.SemiBold,
                        color = if (enabled) SlatePrimary else SlatePrimary.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text  = if (selectedLanguage == "es")
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

// ═══════════════════════════════════════════════════════════════════════════════
// DocumentCameraModal — State machine + validation pipeline
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Internal state of the camera modal.
 *
 *  Waiting → (enough consecutive sharp frames) → ReadyToCapture
 *          → Capturing → Processing
 *          → Success  (auto-closes after 1.2 s)
 *          → Error    (shown inside frame, auto-resets after delay)
 */
private sealed class ScanState {
    /** Live preview active, waiting for stable sharp frame. */
    object Waiting : ScanState()
    /** Sharpness requirement met — capture command sent to CameraX. */
    object ReadyToCapture : ScanState()
    /** CameraX is taking the photo. */
    object Capturing : ScanState()
    /** Bitmap received, validation pipeline running. */
    object Processing : ScanState()
    /** Validation passed — display success and close. */
    object Success : ScanState()
    /** A validation step failed — show message, then auto-retry. */
    data class Error(val message: String) : ScanState()
}

// ── Tuning constants ─────────────────────────────────────────────────────────

/** YUV live-frame sharpness floor.  Below this: document not yet steady. */
private const val LIVE_THRESHOLD = 20f

/** Number of consecutive frames above [LIVE_THRESHOLD] required before capture. */
private const val STREAK_REQUIRED = 6

/** Camera warm-up period (ms) — discard the first few frames after opening. */
private const val WARMUP_MS = 1800L

/** How long to display an error message before auto-retrying (ms). */
private const val ERROR_DISPLAY_MS = 3000L

@Composable
private fun DocumentCameraModal(
    title: String,
    isBackSide: Boolean,
    referenceBitmap: Bitmap?,
    selectedLanguage: String,
    onDismiss: () -> Unit,
    /** [ocrData] is null when scanning the back side (OCR data comes from front). */
    onCapture: (Bitmap, DocumentValidator.OcrData?) -> Unit
) {
    // ── State machine ────────────────────────────────────────────────────────
    var state          by remember { mutableStateOf<ScanState>(ScanState.Waiting) }
    var captureTrigger by remember { mutableStateOf(false) }

    // ── Live sharpness for UI feedback (main thread) ─────────────────────────
    var liveSharpness  by remember { mutableStateOf(0f) }
    var streakCount    by remember { mutableStateOf(0) }

    // ── Thread-safe accumulators (written from main thread via mainHandler) ───
    val streakRef      = remember { java.util.concurrent.atomic.AtomicInteger(0) }
    val triggerGuard   = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    val cameraReady    = remember { java.util.concurrent.atomic.AtomicBoolean(false) }

    val scope = rememberCoroutineScope()

    // Camera warm-up: ignore the first WARMUP_MS of frames
    LaunchedEffect(Unit) {
        delay(WARMUP_MS)
        cameraReady.set(true)
    }

    // ── Reset helper ─────────────────────────────────────────────────────────
    fun resetToWaiting() {
        streakRef.set(0)
        triggerGuard.set(false)
        streakCount   = 0
        liveSharpness = 0f
        state         = ScanState.Waiting
    }

    // ── Sharpness progress for the indicator bar ──────────────────────────────
    val sharpProgress by remember(liveSharpness, streakCount) {
        derivedStateOf {
            ((streakCount.toFloat() / STREAK_REQUIRED) * 0.6f +
             (liveSharpness / (LIVE_THRESHOLD * 2f)).coerceIn(0f, 0.4f))
                .coerceIn(0f, 1f)
        }
    }

    // ── Full-screen modal container ───────────────────────────────────────────
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        // ── Live camera feed ──────────────────────────────────────────────────
        CameraPermissionHandler(
            onPermissionGranted = {},
            onPermissionDenied  = { onDismiss() }
        ) {
            DocumentCameraPreviewComposable(
                modifier      = Modifier.fillMaxSize(),
                lensFacing    = CameraSelector.LENS_FACING_BACK,
                shouldCapture = captureTrigger,
                onCaptureComplete = { captureTrigger = false },

                // ── Image captured by CameraX ─────────────────────────────────
                onImageCaptured = { rawBitmap ->
                    if (state !is ScanState.ReadyToCapture && state !is ScanState.Capturing) return@DocumentCameraPreviewComposable
                    state = ScanState.Processing

                    scope.launch {
                        val result = DocumentValidator.validate(
                            rawBitmap       = rawBitmap,
                            isBackSide      = isBackSide,
                            referenceBitmap = referenceBitmap,
                            lang            = selectedLanguage
                        )

                        when (result) {
                            is DocumentValidator.ValidationResult.Accepted -> {
                                state = ScanState.Success
                                delay(1200)
                                onCapture(result.croppedBitmap, result.ocrData)
                            }
                            is DocumentValidator.ValidationResult.Rejected -> {
                                state = ScanState.Error(result.userMessage)
                                delay(ERROR_DISPLAY_MS)
                                if (state is ScanState.Error) resetToWaiting()
                            }
                        }
                    }
                },

                onError = { onDismiss() },

                // ── Live sharpness callback (main thread, called ~30 fps) ─────
                onLiveSharpness = { s ->
                    if (state !is ScanState.Waiting || !cameraReady.get()) return@DocumentCameraPreviewComposable
                    liveSharpness = s

                    if (s >= LIVE_THRESHOLD) {
                        val streak = streakRef.incrementAndGet()
                        streakCount = streak
                        if (streak >= STREAK_REQUIRED && triggerGuard.compareAndSet(false, true)) {
                            streakRef.set(0)
                            streakCount   = 0
                            state         = ScanState.ReadyToCapture
                            captureTrigger = true
                        }
                    } else {
                        if (streakRef.get() > 0) streakRef.set(0)
                        streakCount = 0
                    }
                }
            )
        }

        // ── Vignette overlay (darkens outside the guide frame) ────────────────
        Canvas(modifier = Modifier.fillMaxSize()) {
            val fw = size.width  * 0.70f
            val fh = fw / 1.6f
            val fl = (size.width  - fw) / 2f
            val ft = (size.height - fh) / 2f
            val vig = Color.Black.copy(alpha = 0.55f)
            drawRect(vig, Offset(0f, 0f),         Size(size.width, ft))
            drawRect(vig, Offset(0f, ft + fh),    Size(size.width, size.height - ft - fh))
            drawRect(vig, Offset(0f, ft),          Size(fl, fh))
            drawRect(vig, Offset(fl + fw, ft),     Size(size.width - fl - fw, fh))
        }

        // ── Top title bar ─────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(Color.Black.copy(alpha = 0.72f))
                .padding(horizontal = 20.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text       = title,
                style      = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                fontWeight = FontWeight.Bold,
                color      = Color.White
            )
        }

        // ── Guide frame colour based on state ─────────────────────────────────
        val frameColor = when (state) {
            is ScanState.Error                                          -> Color(0xFFE53935)
            is ScanState.Success                                        -> Color(0xFF43A047)
            is ScanState.Processing, ScanState.ReadyToCapture,
            ScanState.Capturing                                         -> OrangePrimary
            else -> {
                val p = sharpProgress
                when {
                    p >= 0.75f -> OrangePrimary
                    p >= 0.40f -> Color(0xFFFF8C00)
                    else       -> Color(0xFFE53935).copy(alpha = 0.85f)
                }
            }
        }

        // ── Document guide frame ──────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth(0.70f)
                .aspectRatio(1.6f)
                .align(Alignment.Center)
                .border(3.dp, frameColor, RoundedCornerShape(14.dp))
                .clip(RoundedCornerShape(14.dp))
        ) {
            // Corner accent marks drawn on top of the border
            Canvas(modifier = Modifier.fillMaxSize()) {
                val len = 50f; val sw = 9f; val c = frameColor
                drawLine(c, Offset(0f, len),           Offset(0f, 0f),           sw)
                drawLine(c, Offset(0f, 0f),            Offset(len, 0f),           sw)
                drawLine(c, Offset(size.width-len,0f), Offset(size.width,0f),     sw)
                drawLine(c, Offset(size.width,0f),     Offset(size.width,len),    sw)
                drawLine(c, Offset(0f,size.height-len),Offset(0f,size.height),    sw)
                drawLine(c, Offset(0f,size.height),    Offset(len,size.height),   sw)
                drawLine(c, Offset(size.width-len,size.height), Offset(size.width,size.height), sw)
                drawLine(c, Offset(size.width,size.height-len), Offset(size.width,size.height), sw)
            }

            // ── State overlay inside the frame ───────────────────────────────
            when (val s = state) {

                is ScanState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize()
                            .background(Color(0xFFB71C1C).copy(alpha = 0.88f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(horizontal = 20.dp)
                        ) {
                            Icon(Icons.Default.Warning, null, tint = Color.White, modifier = Modifier.size(38.dp))
                            Text(
                                text  = s.message,
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = 19.sp
                            )
                        }
                    }
                }

                is ScanState.Success -> {
                    Box(
                        modifier = Modifier.fillMaxSize()
                            .background(Color(0xFF1B5E20).copy(alpha = 0.90f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, null, tint = Color.White, modifier = Modifier.size(44.dp))
                            Text(
                                text  = if (selectedLanguage == "es") "¡Documento aceptado!" else "Document accepted!",
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                is ScanState.Processing, ScanState.ReadyToCapture, ScanState.Capturing -> {
                    Box(
                        modifier = Modifier.fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                color       = OrangePrimary,
                                modifier    = Modifier.size(40.dp),
                                strokeWidth = 4.dp
                            )
                            Text(
                                text  = if (selectedLanguage == "es") "Verificando…" else "Verifying…",
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                else -> { /* Waiting — transparent, live feed visible */ }
            }
        }

        // ── Sharpness progress bar (Waiting only, just below the guide frame) ─
        if (state is ScanState.Waiting) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.70f)
                    .padding(top = 144.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                LinearProgressIndicator(
                    progress    = { sharpProgress },
                    modifier    = Modifier.fillMaxWidth().height(4.dp),
                    color       = when {
                        sharpProgress >= 0.8f -> Color(0xFF43A047)
                        sharpProgress >= 0.4f -> OrangePrimary
                        else                  -> Color(0xFFE53935).copy(alpha = 0.8f)
                    },
                    trackColor  = Color.White.copy(alpha = 0.18f)
                )
                Text(
                    text  = when {
                        sharpProgress >= 0.8f -> if (selectedLanguage == "es") "Capturando…" else "Capturing…"
                        sharpProgress >= 0.4f -> if (selectedLanguage == "es") "Enfocando…"  else "Focusing…"
                        else                  -> if (selectedLanguage == "es") "Centre el documento en el marco" else "Center the document in the frame"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = Color.White.copy(alpha = 0.80f),
                    textAlign = TextAlign.Center
                )
            }
        }

        // ── Bottom hint strip ─────────────────────────────────────────────────
        if (state is ScanState.Waiting) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.72f))
                    .padding(start = 24.dp, end = 24.dp, top = 10.dp, bottom = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text  = if (selectedLanguage == "es")
                        "Se capturará automáticamente cuando el documento esté enfocado"
                    else
                        "Will capture automatically once the document is in focus",
                    style      = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color      = OrangePrimary,
                    textAlign  = TextAlign.Center,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // ── Cancel button — just above the bottom strip ───────────────────────
        if (state !is ScanState.Success) {
            OutlinedButton(
                onClick  = { state = ScanState.Waiting; onDismiss() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 68.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.65f)),
                shape  = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text  = if (selectedLanguage == "es") "Cancelar" else "Cancel",
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp)
                )
            }
        }
    }
}
