package com.eflglobal.visitorsapp.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Portrait
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eflglobal.visitorsapp.R
import com.eflglobal.visitorsapp.core.utils.ImageSaver
import com.eflglobal.visitorsapp.data.local.AppDatabase
import com.eflglobal.visitorsapp.data.local.mapper.toDomain
import com.eflglobal.visitorsapp.domain.model.VisitReason
import com.eflglobal.visitorsapp.domain.model.VisitReasonKeys
import com.eflglobal.visitorsapp.ui.components.CameraPermissionHandler
import com.eflglobal.visitorsapp.ui.components.CameraPreviewComposable
import com.eflglobal.visitorsapp.ui.theme.OrangePrimary
import com.eflglobal.visitorsapp.ui.theme.SlatePrimary
import com.eflglobal.visitorsapp.ui.viewmodel.RecurrentVisitViewModel
import com.eflglobal.visitorsapp.ui.viewmodel.RecurrentVisitUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurrentVisitDataScreen(
    visitorName: String = "",
    documentNumber: String = "",
    onContinue: () -> Unit,
    onBack: () -> Unit,
    viewModel: RecurrentVisitViewModel? = null,
    selectedLanguage: String = "es"
) {
    val context        = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // ── Pre-fill from ViewModel ───────────────────────────────────────────────
    val person = viewModel?.getSelectedPerson()

    val displayFirstName = person?.firstName  ?: visitorName.substringBefore(" ")
    val displayLastName  = person?.lastName   ?: visitorName.substringAfter(" ", "")
    val displayDoc       = person?.documentNumber
    val displayCompany   = person?.company
    val displayEmail     = person?.email      ?: ""
    val displayPhone     = person?.phoneNumber ?: ""

    // Load stored profile photo from disk
    var storedProfileBitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(person?.profilePhotoPath) {
        val path = person?.profilePhotoPath
        if (!path.isNullOrBlank()) {
            withContext(Dispatchers.IO) {
                runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
            }?.let { storedProfileBitmap = it }
        }
    }

    // ── Photo ─────────────────────────────────────────────────────────────────
    var photoTaken       by remember { mutableStateOf(false) }
    var isCapturing      by remember { mutableStateOf(false) }
    var capturedBitmap   by remember { mutableStateOf<Bitmap?>(null) }

    // Show stored photo until a new one is taken
    val displayBitmap = capturedBitmap ?: storedProfileBitmap

    // ── Visit reason ──────────────────────────────────────────────────────────
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

    // ── Who visiting ─────────────────────────────────────────────────────────
    var visitingPerson by remember { mutableStateOf("") }

    // ── UiState → navigate on success ────────────────────────────────────────
    val uiState by (viewModel?.uiState ?: return).collectAsState()
    LaunchedEffect(uiState) {
        if (uiState is RecurrentVisitUiState.Success) onContinue()
    }

    val canSubmit = visitingPerson.isNotBlank()
        && selectedReason != null
        && (!isOtherSelected || customReasonText.isNotBlank())
        && uiState !is RecurrentVisitUiState.Loading

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.visit_information), fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.Top
            ) {
                // ════════════════════════════════════════════
                // LEFT COLUMN — Registered Visitor Info (read-only)
                // ════════════════════════════════════════════
                Column(modifier = Modifier.weight(1f)) {

                    Text(
                        text       = stringResource(R.string.registered_visitor),
                        style      = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                        fontWeight = FontWeight.SemiBold,
                        color      = SlatePrimary,
                        modifier   = Modifier.padding(bottom = 12.dp)
                    )

                    // ── Verified badge ────────────────────────────────────────
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(OrangePrimary.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                            .border(1.dp, OrangePrimary.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, null, tint = OrangePrimary, modifier = Modifier.size(16.dp))
                        Text(
                            stringResource(R.string.document_verified),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = OrangePrimary, fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    // First name — read-only
                    ReadOnlyField(
                        label = stringResource(R.string.first_name),
                        value = displayFirstName
                    )
                    Spacer(Modifier.height(10.dp))

                    // Last name — read-only
                    ReadOnlyField(
                        label = stringResource(R.string.last_name),
                        value = displayLastName
                    )
                    Spacer(Modifier.height(10.dp))

                    // Document number — optional
                    if (!displayDoc.isNullOrBlank()) {
                        ReadOnlyField(
                            label = stringResource(R.string.document_number) + " (" + stringResource(R.string.optional) + ")",
                            value = displayDoc
                        )
                        Spacer(Modifier.height(10.dp))
                    }

                    // Company — optional
                    if (!displayCompany.isNullOrBlank()) {
                        ReadOnlyField(
                            label = stringResource(R.string.company) + " (" + stringResource(R.string.optional) + ")",
                            value = displayCompany
                        )
                        Spacer(Modifier.height(10.dp))
                    }

                    // Email
                    ReadOnlyField(
                        label = stringResource(R.string.email),
                        value = displayEmail
                    )
                    Spacer(Modifier.height(10.dp))

                    // Phone
                    ReadOnlyField(
                        label = stringResource(R.string.phone),
                        value = displayPhone
                    )
                }

                // ════════════════════════════════════════════
                // RIGHT COLUMN — Who visiting + Visit Reason + Photo
                // ════════════════════════════════════════════
                Column(modifier = Modifier.weight(1f)) {

                    // ── Who are you visiting ──────────────────────────────────
                    OutlinedTextField(
                        value         = visitingPerson,
                        onValueChange = { visitingPerson = it },
                        label         = { Text(stringResource(R.string.who_visiting), fontSize = 11.sp) },
                        placeholder   = { Text(stringResource(R.string.enter_who_visiting), fontSize = 11.sp) },
                        modifier      = Modifier.fillMaxWidth(),
                        shape         = RoundedCornerShape(12.dp),
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = OrangePrimary,
                            focusedLabelColor  = OrangePrimary,
                            unfocusedBorderColor = if (visitingPerson.isNotBlank()) OrangePrimary.copy(alpha = 0.6f)
                                                   else MaterialTheme.colorScheme.outline
                        ),
                        singleLine  = true,
                        textStyle   = LocalTextStyle.current.copy(fontSize = 13.sp)
                    )

                    Spacer(Modifier.height(16.dp))

                    // ── Visit Reason ──────────────────────────────────────────
                    Text(
                        text       = stringResource(R.string.visit_reason),
                        style      = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                        fontWeight = FontWeight.SemiBold,
                        color      = SlatePrimary,
                        modifier   = Modifier.padding(bottom = 12.dp)
                    )

                    if (visitReasons.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = OrangePrimary, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
                        }
                    } else {
                        ExposedDropdownMenuBox(
                            expanded         = reasonExpanded,
                            onExpandedChange = { reasonExpanded = !reasonExpanded },
                            modifier         = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value         = selectedReason?.label(selectedLanguage) ?: stringResource(R.string.select_visit_reason),
                                onValueChange = {},
                                readOnly      = true,
                                label         = { Text(stringResource(R.string.visit_reason), fontSize = 11.sp) },
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
                            ExposedDropdownMenu(expanded = reasonExpanded, onDismissRequest = { reasonExpanded = false }) {
                                visitReasons.forEach { reason ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                reason.label(selectedLanguage), fontSize = 13.sp,
                                                fontWeight = if (reason.reasonKey == VisitReasonKeys.OTHER) FontWeight.SemiBold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            selectedReason = reason
                                            reasonExpanded = false
                                            if (reason.reasonKey != VisitReasonKeys.OTHER) customReasonText = ""
                                            viewModel?.setVisitReason(reason.reasonKey)
                                        }
                                    )
                                }
                            }
                        }

                        // "Other" free-text
                        AnimatedVisibility(visible = isOtherSelected, enter = expandVertically(), exit = shrinkVertically()) {
                            Column(modifier = Modifier.padding(top = 8.dp)) {
                                OutlinedTextField(
                                    value         = customReasonText,
                                    onValueChange = {
                                        customReasonText = it
                                        viewModel?.setVisitReason(VisitReasonKeys.OTHER, it)
                                    },
                                    label         = { Text(stringResource(R.string.other_specify), fontSize = 11.sp) },
                                    modifier      = Modifier.fillMaxWidth(),
                                    shape         = RoundedCornerShape(12.dp),
                                    colors        = OutlinedTextFieldDefaults.colors(focusedBorderColor = OrangePrimary, focusedLabelColor = OrangePrimary),
                                    maxLines      = 3,
                                    textStyle     = LocalTextStyle.current.copy(fontSize = 13.sp)
                                )
                                if (customReasonText.isBlank()) {
                                    Text(
                                        text     = "* " + stringResource(R.string.required_field),
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
                        text       = stringResource(R.string.personal_photo),
                        style      = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                        fontWeight = FontWeight.SemiBold,
                        color      = SlatePrimary,
                        modifier   = Modifier.padding(bottom = 12.dp)
                    )

                    Card(
                        onClick   = { if (!photoTaken) isCapturing = true },
                        modifier  = Modifier.fillMaxWidth().aspectRatio(2f),
                        shape     = RoundedCornerShape(16.dp),
                        colors    = CardDefaults.cardColors(
                            containerColor = if (displayBitmap != null) OrangePrimary.copy(alpha = 0.08f)
                                             else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        border = if (displayBitmap != null) BorderStroke(2.dp, OrangePrimary)
                                 else BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            if (displayBitmap != null) {
                                Image(
                                    bitmap             = displayBitmap.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale       = ContentScale.FillWidth,
                                    modifier           = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp))
                                )
                                Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp))
                                    .background(OrangePrimary.copy(alpha = 0.12f)))
                                Box(
                                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(28.dp)
                                        .background(OrangePrimary, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.CheckCircle, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(20.dp))
                                }
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.Portrait, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f), modifier = Modifier.size(56.dp))
                                    Text(
                                        stringResource(R.string.take_photo),
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Retake / take photo button
                    OutlinedButton(
                        onClick  = { isCapturing = true },
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        shape    = RoundedCornerShape(12.dp),
                        border   = BorderStroke(1.dp, OrangePrimary),
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = OrangePrimary)
                    ) {
                        Icon(Icons.Default.Portrait, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (displayBitmap != null) stringResource(R.string.retake_photo) else stringResource(R.string.take_photo),
                            fontSize = 12.sp
                        )
                    }
                } // end right column
            } // end Row

            Spacer(Modifier.height(24.dp))

            // ── Continue button ───────────────────────────────────────────────
            Button(
                onClick  = {
                    viewModel?.createVisit(visitingPersonName = visitingPerson)
                },
                enabled  = canSubmit,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor         = OrangePrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (uiState is RecurrentVisitUiState.Loading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.5.dp)
                } else {
                    Text(stringResource(R.string.continue_btn), style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp), fontWeight = FontWeight.SemiBold)
                }
            }

            if (uiState is RecurrentVisitUiState.Error) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text     = (uiState as RecurrentVisitUiState.Error).message,
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // ── Selfie capture modal ──────────────────────────────────────────────
        if (isCapturing) {
            RecurrentSelfieCaptureModal(
                onDismiss       = { isCapturing = false },
                onPhotoCaptured = { bitmap ->
                    coroutineScope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                ImageSaver.saveImage(
                                    context   = context,
                                    bitmap    = bitmap,
                                    personId  = person?.personId ?: "recurrent",
                                    imageType = ImageSaver.ImageType.PROFILE
                                )
                            }
                            capturedBitmap = bitmap
                            photoTaken     = true
                            isCapturing    = false
                        } catch (e: Exception) {
                            e.printStackTrace()
                            capturedBitmap = bitmap
                            photoTaken     = true
                            isCapturing    = false
                        }
                    }
                }
            )
        }
        } // end Box
    } // end Scaffold
}

// ── Read-only styled field ────────────────────────────────────────────────────
@Composable
private fun ReadOnlyField(label: String, value: String) {
    OutlinedTextField(
        value         = value,
        onValueChange = {},
        readOnly      = true,
        enabled       = false,
        label         = { Text(label, fontSize = 11.sp) },
        modifier      = Modifier.fillMaxWidth(),
        shape         = RoundedCornerShape(12.dp),
        colors        = OutlinedTextFieldDefaults.colors(
            disabledBorderColor = OrangePrimary.copy(alpha = 0.4f),
            disabledTextColor   = MaterialTheme.colorScheme.onSurface,
            disabledLabelColor  = OrangePrimary.copy(alpha = 0.7f)
        ),
        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    )
}

// ── Selfie capture modal — AUTO countdown ────────────────────────────────────
@Composable
private fun RecurrentSelfieCaptureModal(
    onDismiss: () -> Unit,
    onPhotoCaptured: (Bitmap) -> Unit
) {
    var countdown     by remember { mutableStateOf(5) }
    var shouldCapture by remember { mutableStateOf(false) }
    var isProcessing  by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (countdown > 0) { delay(1000); countdown-- }
        shouldCapture = true
    }

    Box(modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
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
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(
                text       = stringResource(R.string.personal_photo),
                style      = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color      = androidx.compose.ui.graphics.Color.White,
                modifier   = Modifier
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            )
            if (!isProcessing) {
                IconButton(
                    onClick  = onDismiss,
                    modifier = Modifier.background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f), CircleShape)
                ) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel), tint = androidx.compose.ui.graphics.Color.White)
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
                modifier         = Modifier.size(240.dp).border(4.dp, OrangePrimary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(color = OrangePrimary, modifier = Modifier.size(56.dp))
                } else if (countdown > 0) {
                    Box(
                        modifier         = Modifier.size(120.dp).background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.65f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(countdown.toString(), style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold, color = OrangePrimary, fontSize = 64.sp)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = when {
                    isProcessing  -> stringResource(R.string.verifying)
                    countdown > 0 -> stringResource(R.string.position_face)
                    else          -> stringResource(R.string.look_at_camera)
                },
                style     = MaterialTheme.typography.titleMedium,
                color     = androidx.compose.ui.graphics.Color.White,
                textAlign = TextAlign.Center,
                modifier  = Modifier
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            )
        }
    }
}
