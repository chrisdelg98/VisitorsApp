package com.eflglobal.visitorsapp.ui.screens

import android.graphics.Bitmap
import androidx.camera.core.CameraSelector
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Portrait
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eflglobal.visitorsapp.core.ocr.DocumentDataExtractor
import com.eflglobal.visitorsapp.core.utils.ImageSaver
import com.eflglobal.visitorsapp.data.local.AppDatabase
import com.eflglobal.visitorsapp.data.local.mapper.toDomain
import com.eflglobal.visitorsapp.domain.model.VisitReason
import com.eflglobal.visitorsapp.domain.model.VisitReasonKeys
import com.eflglobal.visitorsapp.ui.components.CameraPermissionHandler
import com.eflglobal.visitorsapp.ui.components.CameraPreviewComposable
import com.eflglobal.visitorsapp.ui.localization.Strings
import com.eflglobal.visitorsapp.ui.theme.OrangePrimary
import com.eflglobal.visitorsapp.ui.theme.SlatePrimary
import com.eflglobal.visitorsapp.ui.viewmodel.NewVisitViewModel
import com.eflglobal.visitorsapp.ui.viewmodel.NewVisitUiState
import com.eflglobal.visitorsapp.ui.viewmodel.ViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonDataScreen(
    onContinue: () -> Unit,
    onBack: () -> Unit,
    selectedLanguage: String = "es",
    viewModel: NewVisitViewModel = viewModel(
        factory = ViewModelFactory(LocalContext.current)
    )
) {
    val context        = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()

    // ── OCR pre-fill ──────────────────────────────────────────────────────────
    val detectedFirstName  = viewModel.getDetectedFirstName() ?: ""
    val detectedLastName   = viewModel.getDetectedLastName()  ?: ""
    val detectedDocNumber  = viewModel.getDocumentNumber()    ?: ""
    val extractionSource   = viewModel.getExtractionSource()
    val extractionConf     = viewModel.getExtractionConfidence()

    var firstName      by remember { mutableStateOf(detectedFirstName) }
    var lastName       by remember { mutableStateOf(detectedLastName) }
    var company        by remember { mutableStateOf("") }
    var email          by remember { mutableStateOf("") }
    var phone          by remember { mutableStateOf("") }
    var visitingPerson by remember { mutableStateOf("") }

    // ── Photo ─────────────────────────────────────────────────────────────────
    var photoTaken       by remember { mutableStateOf(false) }
    var isCapturing      by remember { mutableStateOf(false) }
    var profilePhotoPath by remember { mutableStateOf<String?>(null) }
    var capturedBitmap   by remember { mutableStateOf<Bitmap?>(null) }

    // ── Visit reason — loaded from DB on IO thread ────────────────────────────
    val visitReasons = remember { mutableStateListOf<VisitReason>() }
    LaunchedEffect(Unit) {
        val reasons = withContext(Dispatchers.IO) {
            AppDatabase.getInstance(context).visitReasonDao().getAllActiveReasons()
        }.map { it.toDomain() }
        visitReasons.clear()
        visitReasons.addAll(reasons)
    }

    var selectedReason   by remember { mutableStateOf<VisitReason?>(null) }
    var reasonExpanded   by remember { mutableStateOf(false) }
    var customReasonText by remember { mutableStateOf("") }
    val isOtherSelected  = selectedReason?.reasonKey == VisitReasonKeys.OTHER

    LaunchedEffect(selectedReason, customReasonText) {
        selectedReason?.let {
            viewModel.setVisitReason(
                key    = it.reasonKey,
                custom = if (isOtherSelected) customReasonText else null
            )
        }
    }

    // Always fill OCR-detected names when the screen first appears.
    // LaunchedEffect(Unit) runs exactly once after the initial composition,
    // guaranteeing that the ViewModel already holds the scanned data.
    LaunchedEffect(Unit) {
        viewModel.getDetectedFirstName()?.takeIf { it.isNotBlank() }?.let { firstName = it }
        viewModel.getDetectedLastName()?.takeIf  { it.isNotBlank() }?.let { lastName  = it }
    }

    LaunchedEffect(uiState) {
        if (uiState is NewVisitUiState.Success) onContinue()
    }

    val canSubmit = firstName.isNotBlank()
        && email.isNotBlank()
        && phone.isNotBlank()
        && visitingPerson.isNotBlank()
        && photoTaken
        && selectedReason != null
        && (!isOtherSelected || customReasonText.isNotBlank())
        && uiState !is NewVisitUiState.Loading

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Strings.visitorInformation(selectedLanguage), fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            // ── TWO-COLUMN LAYOUT ─────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.Top
            ) {
                // ════════════════════════════════════════════
                // LEFT COLUMN — Personal Information
                // ════════════════════════════════════════════
                Column(modifier = Modifier.weight(1f)) {

                    Text(
                        text       = Strings.personalInformation(selectedLanguage),
                        style      = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                        fontWeight = FontWeight.SemiBold,
                        color      = SlatePrimary,
                        modifier   = Modifier.padding(bottom = 8.dp)
                    )

                    // ── Extraction status banner ──────────────────────────────
                    ExtractionStatusBanner(
                        source           = extractionSource,
                        confidence       = extractionConf,
                        hasName          = detectedFirstName.isNotEmpty() || detectedLastName.isNotEmpty(),
                        selectedLanguage = selectedLanguage
                    )

                    Spacer(Modifier.height(8.dp))

                    // First name
                    FieldWithOcrHint(
                        value         = firstName,
                        onValueChange = { firstName = it },
                        label         = if (selectedLanguage == "es") "Nombres" else "First Name",
                        placeholder   = if (selectedLanguage == "es") "Ingrese nombres" else "Enter first name",
                        ocr           = detectedFirstName.isNotEmpty(),
                        selectedLanguage = selectedLanguage
                    )

                    Spacer(Modifier.height(10.dp))

                    // Last name
                    FieldWithOcrHint(
                        value         = lastName,
                        onValueChange = { lastName = it },
                        label         = if (selectedLanguage == "es") "Apellidos" else "Last Name",
                        placeholder   = if (selectedLanguage == "es") "Ingrese apellidos" else "Enter last name",
                        ocr           = detectedLastName.isNotEmpty(),
                        selectedLanguage = selectedLanguage
                    )

                    Spacer(Modifier.height(10.dp))

                    // Document number — read-only optional
                    OutlinedTextField(
                        value         = detectedDocNumber,
                        onValueChange = {},
                        readOnly      = true,
                        enabled       = false,
                        label         = { Text(if (selectedLanguage == "es") "N° Documento (opcional)" else "Doc. No. (optional)", fontSize = 11.sp) },
                        placeholder   = { Text(if (selectedLanguage == "es") "No detectado" else "Not detected", fontSize = 11.sp) },
                        modifier      = Modifier.fillMaxWidth(),
                        shape         = RoundedCornerShape(12.dp),
                        colors        = OutlinedTextFieldDefaults.colors(
                            disabledBorderColor = if (detectedDocNumber.isNotEmpty()) OrangePrimary.copy(alpha = 0.4f)
                                                  else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                            disabledTextColor   = MaterialTheme.colorScheme.onSurface,
                            disabledLabelColor  = if (detectedDocNumber.isNotEmpty()) OrangePrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    )
                    if (detectedDocNumber.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                    } else {
                        Spacer(Modifier.height(10.dp))
                    }

                    // Company (optional)
                    OutlinedTextField(
                        value         = company,
                        onValueChange = { company = it },
                        label         = { Text("${Strings.company(selectedLanguage)} (${Strings.optional(selectedLanguage)})", fontSize = 11.sp) },
                        modifier      = Modifier.fillMaxWidth(),
                        shape         = RoundedCornerShape(12.dp),
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SlatePrimary,
                            focusedLabelColor  = SlatePrimary
                        ),
                        singleLine  = true,
                        textStyle   = LocalTextStyle.current.copy(fontSize = 13.sp)
                    )

                    Spacer(Modifier.height(10.dp))

                    // Email
                    OutlinedTextField(
                        value           = email,
                        onValueChange   = { email = it },
                        label           = { Text(Strings.email(selectedLanguage), fontSize = 11.sp) },
                        placeholder     = { Text(Strings.enterEmail(selectedLanguage), fontSize = 11.sp) },
                        modifier        = Modifier.fillMaxWidth(),
                        shape           = RoundedCornerShape(12.dp),
                        colors          = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SlatePrimary,
                            focusedLabelColor  = SlatePrimary
                        ),
                        singleLine      = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        textStyle       = LocalTextStyle.current.copy(fontSize = 13.sp)
                    )

                    Spacer(Modifier.height(10.dp))

                    // Phone
                    OutlinedTextField(
                        value           = phone,
                        onValueChange   = { phone = it },
                        label           = { Text(Strings.phone(selectedLanguage), fontSize = 11.sp) },
                        placeholder     = { Text(Strings.enterPhone(selectedLanguage), fontSize = 11.sp) },
                        modifier        = Modifier.fillMaxWidth(),
                        shape           = RoundedCornerShape(12.dp),
                        colors          = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SlatePrimary,
                            focusedLabelColor  = SlatePrimary
                        ),
                        singleLine      = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        textStyle       = LocalTextStyle.current.copy(fontSize = 13.sp)
                    )

                    Spacer(Modifier.height(10.dp))

                    // Who are you visiting
                    OutlinedTextField(
                        value         = visitingPerson,
                        onValueChange = { visitingPerson = it },
                        label         = { Text(Strings.whoVisiting(selectedLanguage), fontSize = 11.sp) },
                        placeholder   = { Text(Strings.enterWhoVisiting(selectedLanguage), fontSize = 11.sp) },
                        modifier      = Modifier.fillMaxWidth(),
                        shape         = RoundedCornerShape(12.dp),
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SlatePrimary,
                            focusedLabelColor  = SlatePrimary
                        ),
                        singleLine  = true,
                        textStyle   = LocalTextStyle.current.copy(fontSize = 13.sp)
                    )
                } // end left column

                // ════════════════════════════════════════════
                // RIGHT COLUMN — Visit Reason + Photo
                // ════════════════════════════════════════════
                Column(modifier = Modifier.weight(1f)) {

                    // ── Reason for Visit ──────────────────────────────────────
                    Text(
                        text       = if (selectedLanguage == "es") "Motivo de la Visita" else "Reason for Visit",
                        style      = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                        fontWeight = FontWeight.SemiBold,
                        color      = SlatePrimary,
                        modifier   = Modifier.padding(bottom = 12.dp)
                    )

                    if (visitReasons.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color       = OrangePrimary,
                                strokeWidth = 3.dp,
                                modifier    = Modifier.size(28.dp)
                            )
                        }
                    } else {
                        ExposedDropdownMenuBox(
                            expanded         = reasonExpanded,
                            onExpandedChange = { reasonExpanded = !reasonExpanded },
                            modifier         = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value         = selectedReason?.label(selectedLanguage)
                                    ?: if (selectedLanguage == "es") "Seleccione el motivo" else "Select reason",
                                onValueChange = {},
                                readOnly      = true,
                                label         = { Text(if (selectedLanguage == "es") "Motivo" else "Reason", fontSize = 11.sp) },
                                trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = reasonExpanded) },
                                modifier      = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                                shape         = RoundedCornerShape(12.dp),
                                colors        = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor   = OrangePrimary,
                                    focusedLabelColor    = OrangePrimary,
                                    unfocusedBorderColor = if (selectedReason != null) OrangePrimary.copy(alpha = 0.6f)
                                                           else MaterialTheme.colorScheme.outline
                                ),
                                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                            )
                            ExposedDropdownMenu(
                                expanded         = reasonExpanded,
                                onDismissRequest = { reasonExpanded = false }
                            ) {
                                visitReasons.forEach { reason ->
                                    DropdownMenuItem(
                                        text    = {
                                            Text(
                                                reason.label(selectedLanguage),
                                                fontSize   = 13.sp,
                                                fontWeight = if (reason.reasonKey == VisitReasonKeys.OTHER)
                                                    FontWeight.SemiBold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            selectedReason = reason
                                            reasonExpanded  = false
                                            if (reason.reasonKey != VisitReasonKeys.OTHER) customReasonText = ""
                                        }
                                    )
                                }
                            }
                        }

                        // "Other" free-text
                        AnimatedVisibility(
                            visible = isOtherSelected,
                            enter   = expandVertically(),
                            exit    = shrinkVertically()
                        ) {
                            Column(modifier = Modifier.padding(top = 8.dp)) {
                                OutlinedTextField(
                                    value         = customReasonText,
                                    onValueChange = { customReasonText = it },
                                    label         = {
                                        Text(
                                            if (selectedLanguage == "es") "Describa el motivo" else "Describe the reason",
                                            fontSize = 11.sp
                                        )
                                    },
                                    modifier  = Modifier.fillMaxWidth(),
                                    shape     = RoundedCornerShape(12.dp),
                                    colors    = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = OrangePrimary,
                                        focusedLabelColor  = OrangePrimary
                                    ),
                                    maxLines  = 3,
                                    textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                                )
                                if (customReasonText.isBlank()) {
                                    Text(
                                        text     = if (selectedLanguage == "es") "* Requerido" else "* Required",
                                        style    = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                        color    = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(start = 12.dp, top = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // ── Personal Photo ────────────────────────────────────────
                    Text(
                        text       = Strings.personalPhoto(selectedLanguage),
                        style      = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                        fontWeight = FontWeight.SemiBold,
                        color      = SlatePrimary,
                        modifier   = Modifier.padding(bottom = 12.dp)
                    )

                    // Photo preview card — fills the width of the right column
                    Card(
                        onClick   = { if (!photoTaken) isCapturing = true },
                        modifier  = Modifier.fillMaxWidth().aspectRatio(2f),
                        shape     = RoundedCornerShape(16.dp),
                        colors    = CardDefaults.cardColors(
                            containerColor = if (photoTaken) OrangePrimary.copy(alpha = 0.08f)
                                             else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        border    = if (photoTaken) BorderStroke(2.dp, OrangePrimary)
                                    else BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    ) {
                        Box(
                            modifier         = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            if (photoTaken && capturedBitmap != null) {
                                // ── REAL photo preview ──────────────────────
                                Image(
                                    bitmap             = capturedBitmap!!.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale       = ContentScale.FillWidth,
                                    modifier           = Modifier.fillMaxSize()
                                        .clip(RoundedCornerShape(16.dp))
                                )
                                // Orange tint overlay
                                Box(
                                    modifier = Modifier.fillMaxSize()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(OrangePrimary.copy(alpha = 0.12f))
                                )
                                // ✓ badge
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp)
                                        .size(28.dp)
                                        .background(OrangePrimary, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint     = androidx.compose.ui.graphics.Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            } else if (!photoTaken) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Portrait,
                                        contentDescription = null,
                                        tint     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                                        modifier = Modifier.size(56.dp)
                                    )
                                    Text(
                                        text  = if (selectedLanguage == "es") "Toca para tomar foto" else "Tap to take photo",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Retake button (shown after photo taken)
                    if (photoTaken) {
                        OutlinedButton(
                            onClick  = { isCapturing = true },
                            modifier = Modifier.fillMaxWidth().height(40.dp),
                            shape    = RoundedCornerShape(12.dp),
                            border   = BorderStroke(1.dp, OrangePrimary),
                            colors   = ButtonDefaults.outlinedButtonColors(contentColor = OrangePrimary)
                        ) {
                            Icon(Icons.Default.Portrait, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(Strings.retakePhoto(selectedLanguage), fontSize = 12.sp)
                        }
                    }
                } // end right column
            } // end Row

            Spacer(Modifier.height(24.dp))

            // ── Continue button ───────────────────────────────────────────────
            Button(
                onClick  = {
                    viewModel.createPersonAndVisit(
                        firstName          = firstName,
                        lastName           = lastName,
                        email              = email,
                        phoneNumber        = phone,
                        company            = company.ifBlank { null },
                        visitingPersonName = visitingPerson,
                        profilePhotoPath   = profilePhotoPath
                    )
                },
                enabled  = canSubmit,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor         = OrangePrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (uiState is NewVisitUiState.Loading) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(24.dp),
                        color       = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Text(
                        Strings.continueBtn(selectedLanguage),
                        style      = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (uiState is NewVisitUiState.Error) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text      = (uiState as NewVisitUiState.Error).message,
                    style     = MaterialTheme.typography.bodySmall,
                    color     = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier  = Modifier.fillMaxWidth()
                )
            }
        } // end outer Column

        // ── Selfie capture modal ──────────────────────────────────────────────
        if (isCapturing) {
            SelfieCaptureModal(
                selectedLanguage = selectedLanguage,
                onDismiss        = { isCapturing = false },
                onPhotoCaptured  = { bitmap ->
                    coroutineScope.launch {
                        try {
                            val savedPath = withContext(Dispatchers.IO) {
                                ImageSaver.saveImage(
                                    context   = context,
                                    bitmap    = bitmap,
                                    personId  = viewModel.getPersonId(),
                                    imageType = ImageSaver.ImageType.PROFILE
                                )
                            }
                            capturedBitmap   = bitmap
                            profilePhotoPath = savedPath
                            photoTaken       = true
                            isCapturing      = false
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            )
        }
    }
}

// ── Extraction status banner ──────────────────────────────────────────────────
@Composable
private fun ExtractionStatusBanner(
    source: DocumentDataExtractor.ExtractionSource,
    confidence: DocumentDataExtractor.Confidence,
    hasName: Boolean,
    selectedLanguage: String
) {
    val (bgColor, icon, label) = when {
        source == DocumentDataExtractor.ExtractionSource.MRZ -> Triple(
            androidx.compose.ui.graphics.Color(0xFF1B5E20).copy(alpha = 0.12f),
            "✦",
            if (selectedLanguage == "es")
                "✦ Datos detectados vía MRZ (alta confianza)"
            else
                "✦ Data detected via MRZ (high confidence)"
        )
        source == DocumentDataExtractor.ExtractionSource.OCR_KEYED && hasName -> Triple(
            OrangePrimary.copy(alpha = 0.10f),
            "◎",
            if (selectedLanguage == "es")
                "◎ Datos detectados vía OCR — verifique los campos"
            else
                "◎ Data detected via OCR — please verify the fields"
        )
        source == DocumentDataExtractor.ExtractionSource.OCR_HEURISTIC && hasName -> Triple(
            androidx.compose.ui.graphics.Color(0xFFF57F17).copy(alpha = 0.10f),
            "⚠",
            if (selectedLanguage == "es")
                "⚠ Datos aproximados — verifique y corrija si es necesario"
            else
                "⚠ Approximate data — please verify and correct if needed"
        )
        else -> Triple(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            "✎",
            if (selectedLanguage == "es")
                "✎ No se detectaron datos — ingrese la información manualmente"
            else
                "✎ No data detected — please fill in the fields manually"
        )
    }

    val textColor = when (source) {
        DocumentDataExtractor.ExtractionSource.MRZ ->
            androidx.compose.ui.graphics.Color(0xFF1B5E20)
        DocumentDataExtractor.ExtractionSource.OCR_KEYED ->
            OrangePrimary
        DocumentDataExtractor.ExtractionSource.OCR_HEURISTIC ->
            androidx.compose.ui.graphics.Color(0xFFE65100)
        else ->
            MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor, RoundedCornerShape(8.dp))
            .border(
                1.dp,
                textColor.copy(alpha = 0.25f),
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text  = label,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = textColor,
            fontWeight = FontWeight.Medium,
            lineHeight = 15.sp
        )
    }
}

// ── Reusable field with OCR hint ──────────────────────────────────────────────
@Composable
private fun FieldWithOcrHint(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    ocr: Boolean,
    selectedLanguage: String
) {
    OutlinedTextField(
        value         = value,
        onValueChange = onValueChange,
        label         = { Text(label, fontSize = 11.sp) },
        placeholder   = { Text(placeholder, fontSize = 11.sp) },
        modifier      = Modifier.fillMaxWidth(),
        shape         = RoundedCornerShape(12.dp),
        colors        = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = if (ocr) OrangePrimary else SlatePrimary,
            focusedLabelColor  = if (ocr) OrangePrimary else SlatePrimary
        ),
        singleLine  = true,
        textStyle   = LocalTextStyle.current.copy(fontSize = 13.sp)
    )
}

// ── Selfie capture modal — AUTO countdown, no manual button ──────────────────
@Composable
private fun SelfieCaptureModal(
    selectedLanguage: String,
    onDismiss: () -> Unit,
    onPhotoCaptured: (Bitmap) -> Unit
) {
    var countdown     by remember { mutableStateOf(5) }   // starts immediately
    var shouldCapture by remember { mutableStateOf(false) }
    var isProcessing  by remember { mutableStateOf(false) }

    // Auto-countdown starts as soon as the modal opens
    LaunchedEffect(Unit) {
        while (countdown > 0) {
            delay(1000)
            countdown--
        }
        shouldCapture = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black)
    ) {
        CameraPermissionHandler(onPermissionGranted = {}, onPermissionDenied = { onDismiss() }) {
            CameraPreviewComposable(
                onImageCaptured   = { bitmap -> isProcessing = true; onPhotoCaptured(bitmap) },
                onError           = { onDismiss() },
                lensFacing        = CameraSelector.LENS_FACING_FRONT,
                modifier          = Modifier.fillMaxSize(),
                shouldCapture     = shouldCapture,
                onCaptureComplete = { shouldCapture = false }
            )
        }

        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(
                text      = if (selectedLanguage == "es") "Foto Personal" else "Personal Photo",
                style     = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color     = androidx.compose.ui.graphics.Color.White,
                modifier  = Modifier
                    .background(
                        androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f),
                        RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            )
            if (!isProcessing) {
                IconButton(
                    onClick  = onDismiss,
                    modifier = Modifier
                        .background(
                            androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f),
                            CircleShape
                        )
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = androidx.compose.ui.graphics.Color.White
                    )
                }
            }
        }

        // Face guide + countdown
        Column(
            modifier            = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier         = Modifier
                    .size(240.dp)
                    .border(4.dp, OrangePrimary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        color    = OrangePrimary,
                        modifier = Modifier.size(56.dp)
                    )
                } else if (countdown > 0) {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .background(
                                androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.65f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text      = countdown.toString(),
                            style     = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.Bold,
                            color     = OrangePrimary,
                            fontSize  = 64.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = when {
                    isProcessing -> if (selectedLanguage == "es") "Procesando..." else "Processing..."
                    countdown > 0 -> if (selectedLanguage == "es")
                        "Posiciona tu rostro en el círculo"
                    else
                        "Position your face in the circle"
                    else -> if (selectedLanguage == "es") "¡Sonríe!" else "Smile!"
                },
                style     = MaterialTheme.typography.titleMedium,
                color     = androidx.compose.ui.graphics.Color.White,
                textAlign = TextAlign.Center,
                modifier  = Modifier
                    .background(
                        androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            )
        }
    }
}
