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
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eflglobal.visitorsapp.R
import com.eflglobal.visitorsapp.core.utils.FaceDetectionHelper
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
    onContinue: () -> Unit,
    onBack: () -> Unit,
    viewModel: RecurrentVisitViewModel? = null,
    selectedLanguage: String = "es"
) {
    val context        = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // ── Pre-fill: draft (edit flow) takes priority, then person DB data ───────
    val person = viewModel?.getSelectedPerson()
    val hasDraft = viewModel?.hasDraft == true

    // Draft has priority, then last visit data, then person data
    val lastVisitData = viewModel?.getLastVisit()

    var editFirstName by remember(person?.personId, hasDraft) { mutableStateOf(
        when {
            hasDraft && viewModel?.draftFirstName != null -> viewModel.draftFirstName!!
            lastVisitData != null -> person?.firstName ?: ""
            else -> person?.firstName ?: visitorName.substringBefore(" ")
        }
    ) }
    var editLastName  by remember(person?.personId, hasDraft) { mutableStateOf(
        when {
            hasDraft && viewModel?.draftLastName != null -> viewModel.draftLastName!!
            lastVisitData != null -> person?.lastName ?: ""
            else -> person?.lastName ?: visitorName.substringAfter(" ", "")
        }
    ) }
    var editDoc       by remember(person?.personId, hasDraft) { mutableStateOf(
        when {
            hasDraft && viewModel?.draftDoc != null -> viewModel.draftDoc!!
            else -> person?.documentNumber ?: ""
        }
    ) }
    var editCompany   by remember(person?.personId, hasDraft) { mutableStateOf(
        when {
            hasDraft && viewModel?.draftCompany != null -> viewModel.draftCompany!!
            else -> person?.company ?: ""
        }
    ) }
    var editEmail     by remember(person?.personId, hasDraft) { mutableStateOf(
        when {
            hasDraft && viewModel?.draftEmail != null -> viewModel.draftEmail!!
            else -> person?.email ?: ""
        }
    ) }
    var editPhone     by remember(person?.personId, hasDraft) { mutableStateOf(
        when {
            hasDraft && viewModel?.draftPhone != null -> viewModel.draftPhone!!
            else -> person?.phoneNumber ?: ""
        }
    ) }

    // Load profile photo: new photo taken in this session wins, otherwise use stored person photo
    val effectivePhotoPath = viewModel?.profilePhotoPath ?: person?.profilePhotoPath
    var storedProfileBitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(effectivePhotoPath) {
        if (!effectivePhotoPath.isNullOrBlank()) {
            withContext(Dispatchers.IO) {
                runCatching { BitmapFactory.decodeFile(effectivePhotoPath) }.getOrNull()
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
    var selectedReason   by remember { mutableStateOf<VisitReason?>(
        if (hasDraft && viewModel?.draftVisitReasonKey != null) {
            visitReasons.firstOrNull { it.reasonKey == viewModel.draftVisitReasonKey }
        } else null
    ) }
    var reasonExpanded   by remember { mutableStateOf(false) }
    var customReasonText by remember { mutableStateOf(
        if (hasDraft) viewModel?.draftVisitReasonCustom ?: ""
        else ""
    ) }
    val isOtherSelected  = selectedReason?.reasonKey == VisitReasonKeys.OTHER

    // Restore selectedReason from draft once the list is populated
    LaunchedEffect(visitReasons.size) {
        if (visitReasons.isNotEmpty() && selectedReason == null && hasDraft) {
            val draftKey = viewModel?.draftVisitReasonKey
            if (!draftKey.isNullOrBlank()) {
                selectedReason = visitReasons.firstOrNull { it.reasonKey == draftKey }
            }
        }
    }

    // ── Who visiting ─────────────────────────────────────────────────────────
    var visitingPerson by remember { mutableStateOf(
        when {
            hasDraft && viewModel?.draftVisitingPerson != null -> viewModel.draftVisitingPerson!!
            lastVisitData != null -> lastVisitData.visitingPersonName
            else -> ""
        }
    ) }

    // Reactively update visitingPerson and selectedReason when last visit pre-fill arrives
    // (only if no draft exists and user hasn't manually entered a value yet)
    val lastVisitPreFill by (viewModel?.lastVisitPreFill ?: return).collectAsState()
    LaunchedEffect(lastVisitPreFill) {
        val last = lastVisitPreFill ?: return@LaunchedEffect
        // Only apply pre-fill if user hasn't manually entered a value yet AND no draft exists
        if (visitingPerson.isBlank() && !hasDraft) {
            visitingPerson = last.visitingPersonName
        }
        // Apply visit reason pre-fill if not already set by a draft
        if (selectedReason == null && visitReasons.isNotEmpty() && !hasDraft) {
            selectedReason = visitReasons.firstOrNull { it.reasonKey == last.visitReason }
            if (last.visitReason == VisitReasonKeys.OTHER && last.visitReasonCustom != null && customReasonText.isBlank()) {
                customReasonText = last.visitReasonCustom ?: ""
            }
        }
    }

    // Also re-apply when visitReasons list loads after lastVisitPreFill was already set
    LaunchedEffect(visitReasons.size, lastVisitPreFill) {
        val last = lastVisitPreFill ?: return@LaunchedEffect
        if (visitReasons.isNotEmpty() && selectedReason == null) {
            selectedReason = visitReasons.firstOrNull { it.reasonKey == last.visitReason }
            if (last.visitReason == VisitReasonKeys.OTHER && last.visitReasonCustom != null && customReasonText.isBlank()) {
                customReasonText = last.visitReasonCustom ?: ""
            }
        }
    }

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
                actions = {
                    Image(
                        painter = painterResource(id = R.drawable.logo),
                        contentDescription = "EFL Global",
                        modifier = Modifier.padding(end = 12.dp).size(32.dp)
                    )
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

                    // First name — editable
                    OutlinedTextField(
                        value         = editFirstName,
                        onValueChange = { editFirstName = it },
                        label         = { Text(stringResource(R.string.first_name), fontSize = 11.sp) },
                        modifier      = Modifier.fillMaxWidth(),
                        shape         = RoundedCornerShape(12.dp),
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = OrangePrimary,
                            focusedLabelColor    = OrangePrimary,
                            unfocusedBorderColor = if (editFirstName.isNotBlank()) OrangePrimary.copy(alpha = 0.6f)
                                                   else MaterialTheme.colorScheme.outline
                        ),
                        singleLine = true,
                        textStyle  = LocalTextStyle.current.copy(fontSize = 13.sp)
                    )
                    Spacer(Modifier.height(10.dp))

                    // Last name — editable
                    OutlinedTextField(
                        value         = editLastName,
                        onValueChange = { editLastName = it },
                        label         = { Text(stringResource(R.string.last_name), fontSize = 11.sp) },
                        modifier      = Modifier.fillMaxWidth(),
                        shape         = RoundedCornerShape(12.dp),
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = OrangePrimary,
                            focusedLabelColor    = OrangePrimary,
                            unfocusedBorderColor = if (editLastName.isNotBlank()) OrangePrimary.copy(alpha = 0.6f)
                                                   else MaterialTheme.colorScheme.outline
                        ),
                        singleLine = true,
                        textStyle  = LocalTextStyle.current.copy(fontSize = 13.sp)
                    )
                    Spacer(Modifier.height(10.dp))

                    // Document number — optional, editable
                    OutlinedTextField(
                        value         = editDoc,
                        onValueChange = { editDoc = it },
                        label         = { Text(stringResource(R.string.document_number) + " (" + stringResource(R.string.optional) + ")", fontSize = 11.sp) },
                        modifier      = Modifier.fillMaxWidth(),
                        shape         = RoundedCornerShape(12.dp),
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = OrangePrimary,
                            focusedLabelColor    = OrangePrimary
                        ),
                        singleLine = true,
                        textStyle  = LocalTextStyle.current.copy(fontSize = 13.sp)
                    )
                    Spacer(Modifier.height(10.dp))

                    // Company — optional, editable
                    OutlinedTextField(
                        value         = editCompany,
                        onValueChange = { editCompany = it },
                        label         = { Text(stringResource(R.string.company) + " (" + stringResource(R.string.optional) + ")", fontSize = 11.sp) },
                        modifier      = Modifier.fillMaxWidth(),
                        shape         = RoundedCornerShape(12.dp),
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = OrangePrimary,
                            focusedLabelColor    = OrangePrimary
                        ),
                        singleLine = true,
                        textStyle  = LocalTextStyle.current.copy(fontSize = 13.sp)
                    )
                    Spacer(Modifier.height(10.dp))

                    // Email — editable
                    OutlinedTextField(
                        value         = editEmail,
                        onValueChange = { editEmail = it },
                        label         = { Text(stringResource(R.string.email), fontSize = 11.sp) },
                        modifier      = Modifier.fillMaxWidth(),
                        shape         = RoundedCornerShape(12.dp),
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = OrangePrimary,
                            focusedLabelColor    = OrangePrimary
                        ),
                        singleLine = true,
                        textStyle  = LocalTextStyle.current.copy(fontSize = 13.sp)
                    )
                    Spacer(Modifier.height(10.dp))

                    // Phone — editable
                    OutlinedTextField(
                        value         = editPhone,
                        onValueChange = { editPhone = it },
                        label         = { Text(stringResource(R.string.phone), fontSize = 11.sp) },
                        modifier      = Modifier.fillMaxWidth(),
                        shape         = RoundedCornerShape(12.dp),
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = OrangePrimary,
                            focusedLabelColor    = OrangePrimary
                        ),
                        singleLine = true,
                        textStyle  = LocalTextStyle.current.copy(fontSize = 13.sp)
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
                    // Persist all edits to ViewModel before navigating forward
                    viewModel?.saveDraft(
                        firstName         = editFirstName,
                        lastName          = editLastName,
                        doc               = editDoc,
                        company           = editCompany,
                        email             = editEmail,
                        phone             = editPhone,
                        visitingPerson    = visitingPerson,
                        visitReasonKey    = selectedReason?.reasonKey,
                        visitReasonCustom = if (isOtherSelected) customReasonText else null
                    )
                    viewModel?.setVisitReason(
                        selectedReason?.reasonKey ?: VisitReasonKeys.VISITOR,
                        if (isOtherSelected) customReasonText else null
                    )
                    viewModel?.createVisit(
                        visitingPersonName = visitingPerson,
                        editedFirstName    = editFirstName,
                        editedLastName     = editLastName,
                        editedCompany      = editCompany,
                        editedEmail        = editEmail,
                        editedPhone        = editPhone
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
        } // end Box
    } // end Scaffold

    // ── Selfie capture modal — OUTSIDE Scaffold to cover full screen ─────────
    if (isCapturing) {
        RecurrentSelfieCaptureModal(
            onDismiss       = { isCapturing = false },
            onPhotoCaptured = { bitmap ->
                coroutineScope.launch {
                    try {
                        val savedPath = withContext(Dispatchers.IO) {
                            ImageSaver.saveImageForVisit(
                                context   = context,
                                bitmap    = bitmap,
                                visitId   = viewModel?.getVisitId() ?: "visit_temp",
                                imageType = ImageSaver.ImageType.PROFILE
                            )
                        }
                        viewModel?.setProfilePhoto(savedPath)
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
}


// ── Selfie capture modal — face-detection gated countdown ────────────────────
@Composable
private fun RecurrentSelfieCaptureModal(
    onDismiss: () -> Unit,
    onPhotoCaptured: (Bitmap) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    var phase          by remember { mutableStateOf("DETECTING") }
    var countdown      by remember { mutableStateOf(5) }
    var shouldCapture  by remember { mutableStateOf(false) }
    var retryCount     by remember { mutableStateOf(0) }
    var faceDetected   by remember { mutableStateOf(false) }
    var lastResult     by remember { mutableStateOf(FaceDetectionHelper.FaceResult.NO_FACE) }
    var showSkipButton by remember { mutableStateOf(false) }
    var skipValidation by remember { mutableStateOf(false) }
    var captureInFlight by remember { mutableStateOf(false) }

    // When face detected → start 5-second countdown
    LaunchedEffect(faceDetected) {
        if (faceDetected && phase == "DETECTING") {
            phase = "COUNTDOWN"
            countdown = 5
            while (countdown > 0) {
                delay(1000)
                if (phase != "COUNTDOWN") return@LaunchedEffect
                countdown--
            }
            if (phase == "COUNTDOWN" && !captureInFlight) {
                captureInFlight = true
                phase = "CAPTURING"
                shouldCapture = true
            }
        }
    }

    // Skip-mode countdown
    LaunchedEffect(skipValidation) {
        if (skipValidation && phase == "SKIP_COUNTDOWN") {
            countdown = 5
            while (countdown > 0) {
                delay(1000)
                if (phase != "SKIP_COUNTDOWN") return@LaunchedEffect
                countdown--
            }
            if (phase == "SKIP_COUNTDOWN" && !captureInFlight) {
                captureInFlight = true
                phase = "CAPTURING"
                shouldCapture = true
            }
        }
    }

    // Auto-retry after rejection
    LaunchedEffect(phase) {
        if (phase == "REJECTED") {
            delay(2500)
            faceDetected    = false
            shouldCapture   = false
            captureInFlight = false
            retryCount++
            if (retryCount >= 2) showSkipButton = true
            phase = "DETECTING"
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
        CameraPermissionHandler(onPermissionGranted = {}, onPermissionDenied = { onDismiss() }) {
            CameraPreviewComposable(
                onImageCaptured   = { bitmap ->
                    try {
                        if (skipValidation) {
                            onPhotoCaptured(bitmap)
                        } else if (phase == "CAPTURING" || phase == "VALIDATING") {
                            phase = "VALIDATING"
                            coroutineScope.launch {
                                try {
                                    val result = FaceDetectionHelper.validateFace(bitmap)
                                    lastResult = result
                                    if (result == FaceDetectionHelper.FaceResult.OK) {
                                        onPhotoCaptured(bitmap)
                                    } else {
                                        phase = "REJECTED"
                                    }
                                } catch (_: Exception) {
                                    onPhotoCaptured(bitmap)
                                }
                            }
                        }
                    } catch (_: Exception) {
                        try { onPhotoCaptured(bitmap) } catch (_: Exception) {}
                    }
                },
                onError           = { /* swallow — don't crash */ },
                lensFacing        = CameraSelector.LENS_FACING_FRONT,
                modifier          = Modifier.fillMaxSize(),
                shouldCapture     = shouldCapture,
                onCaptureComplete = { shouldCapture = false }
            )
        }

        // Face-detection warmup
        if (phase == "DETECTING") {
            LaunchedEffect(retryCount) {
                delay(1500)
                faceDetected = true
            }
        }

        // ── Close button — top-right corner ──────────────────────────────────
        if (phase != "VALIDATING" && phase != "CAPTURING") {
            Box(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                contentAlignment = Alignment.TopEnd
            ) {
                IconButton(
                    onClick  = onDismiss,
                    modifier = Modifier.background(
                        androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f),
                        CircleShape
                    )
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.cancel),
                        tint = androidx.compose.ui.graphics.Color.White
                    )
                }
            }
        }

        // ── Face guide + countdown + status ──────────────────────────────────
        Column(
            modifier            = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text     = stringResource(R.string.personal_photo),
                style    = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color    = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f),
                modifier = Modifier
                    .background(
                        androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f),
                        RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )

            Spacer(Modifier.height(20.dp))

            val guideColor = when (phase) {
                "REJECTED"       -> androidx.compose.ui.graphics.Color(0xFFE53935)
                "COUNTDOWN"      -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
                "SKIP_COUNTDOWN" -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
                "VALIDATING"     -> OrangePrimary
                else             -> OrangePrimary
            }

            Box(
                modifier         = Modifier.size(240.dp).border(4.dp, guideColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                when (phase) {
                    "VALIDATING" -> {
                        CircularProgressIndicator(color = OrangePrimary, modifier = Modifier.size(56.dp))
                    }
                    "COUNTDOWN", "SKIP_COUNTDOWN" -> {
                        if (countdown > 0) {
                            Box(
                                modifier         = Modifier.size(120.dp)
                                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.65f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text       = countdown.toString(),
                                    style      = MaterialTheme.typography.displayLarge,
                                    fontWeight = FontWeight.Bold,
                                    color      = OrangePrimary,
                                    fontSize   = 64.sp
                                )
                            }
                        }
                    }
                    "REJECTED" -> {
                        Box(
                            modifier         = Modifier.size(120.dp)
                                .background(androidx.compose.ui.graphics.Color(0xFFE53935).copy(alpha = 0.25f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null,
                                tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(64.dp))
                        }
                    }
                    else -> {}
                }
            }

            Spacer(Modifier.height(20.dp))

            val statusText = when (phase) {
                "DETECTING"      -> stringResource(R.string.detecting_face)
                "COUNTDOWN"      -> if (countdown > 0) stringResource(R.string.position_face)
                                    else stringResource(R.string.look_at_camera)
                "SKIP_COUNTDOWN" -> stringResource(R.string.taking_photo_no_detection)
                "VALIDATING"     -> stringResource(R.string.verifying)
                "REJECTED"       -> when (lastResult) {
                    FaceDetectionHelper.FaceResult.NO_FACE      -> stringResource(R.string.no_face_detected)
                    FaceDetectionHelper.FaceResult.PARTIAL_FACE -> stringResource(R.string.face_partial)
                    FaceDetectionHelper.FaceResult.TOO_SMALL    -> stringResource(R.string.face_too_small)
                    FaceDetectionHelper.FaceResult.OFF_CENTER   -> stringResource(R.string.face_off_center)
                    else -> stringResource(R.string.no_face_detected)
                }
                "CAPTURING"      -> stringResource(
                    if (skipValidation) R.string.taking_photo_no_detection
                    else R.string.look_at_camera
                )
                else             -> stringResource(R.string.position_face)
            }
            val isError = phase == "REJECTED"

            Text(
                text      = statusText,
                style     = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                color     = if (isError) androidx.compose.ui.graphics.Color(0xFFE53935)
                            else androidx.compose.ui.graphics.Color.White,
                textAlign = TextAlign.Center,
                modifier  = Modifier
                    .background(
                        androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            )
        }

        // ── Manual override — fixed at bottom, stays visible once enabled ────
        if (showSkipButton && phase != "VALIDATING" && phase != "CAPTURING" && phase != "SKIP_COUNTDOWN") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 36.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text     = stringResource(R.string.skip_face_detection),
                    style    = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color    = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier
                        .background(
                            androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f),
                            RoundedCornerShape(12.dp)
                        )
                        .clickable {
                            faceDetected    = false
                            shouldCapture   = false
                            captureInFlight = false
                            skipValidation  = true
                            phase           = "SKIP_COUNTDOWN"
                        }
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                )
            }
        }
    }
}
