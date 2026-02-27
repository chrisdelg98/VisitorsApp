package com.eflglobal.visitorsapp.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eflglobal.visitorsapp.R
import com.eflglobal.visitorsapp.core.utils.ImageSaver
import com.eflglobal.visitorsapp.ui.theme.OrangePrimary
import com.eflglobal.visitorsapp.ui.theme.SlatePrimary
import com.eflglobal.visitorsapp.ui.viewmodel.RecurrentVisitViewModel
import kotlinx.coroutines.launch

// ═══════════════════════════════════════════════════════════════════════════════
// RecurrentDocumentScanScreen
// Mirror of DocumentScanScreen with title "Verificar Documento".
// Visitor data is NOT shown here — it will be used in the next screen.
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurrentDocumentScanScreen(
    // Legacy params kept for NavHost compatibility
    visitorName: String = "",
    documentNumber: String = "",
    company: String = "",
    email: String = "",
    phone: String = "",
    onContinue: () -> Unit,
    onBack: () -> Unit,
    selectedLanguage: String = "es",
    viewModel: RecurrentVisitViewModel? = null
) {
    val context        = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // ── Visitor type (mirrors DocumentScanScreen) ─────────────────────────────
    val visitorTypeOptions = listOf(
        "VISITOR"         to stringResource(R.string.visitor_type),
        "CONTRACTOR"      to stringResource(R.string.contractor),
        "VENDOR"          to stringResource(R.string.vendor),
        "DELIVERY"        to stringResource(R.string.delivery),
        "DRIVER"          to stringResource(R.string.driver),
        "TEMPORARY_STAFF" to stringResource(R.string.temporary_staff),
        "OTHER"           to stringResource(R.string.other)
    )
    var selectedVisitorOption by remember { mutableStateOf(visitorTypeOptions.first()) }
    var expandedVisitorType   by remember { mutableStateOf(false) }

    // ── Document type ─────────────────────────────────────────────────────────
    var selectedDocType by remember { mutableStateOf(viewModel?.documentType ?: "DUI") }

    LaunchedEffect(selectedDocType)       { viewModel?.setDocumentType(selectedDocType) }
    LaunchedEffect(selectedVisitorOption) { viewModel?.setVisitorType(selectedVisitorOption.first) }

    // ── Scan state ────────────────────────────────────────────────────────────
    var frontScanned    by remember { mutableStateOf(viewModel?.documentFrontPath != null) }
    var backScanned     by remember { mutableStateOf(viewModel?.documentBackPath  != null) }
    var showFrontCamera by remember { mutableStateOf(false) }
    var showBackCamera  by remember { mutableStateOf(false) }
    var frontBitmap     by remember { mutableStateOf<Bitmap?>(null) }
    var backBitmap      by remember { mutableStateOf<Bitmap?>(null) }

    val person = viewModel?.getSelectedPerson()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.verify_document), fontSize = 18.sp)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
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

                // ── Visitor type dropdown ─────────────────────────────────────
                Text(
                    text = stringResource(R.string.i_am_a),
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = SlatePrimary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                ExposedDropdownMenuBox(
                    expanded         = expandedVisitorType,
                    onExpandedChange = { expandedVisitorType = !expandedVisitorType },
                    modifier         = Modifier.fillMaxWidth().padding(bottom = 24.dp)
                ) {
                    OutlinedTextField(
                        value         = selectedVisitorOption.second,
                        onValueChange = {},
                        readOnly      = true,
                        label = {
                            Text(stringResource(R.string.select_option), fontSize = 12.sp)
                        },
                        trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedVisitorType) },
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SlatePrimary,
                            focusedLabelColor  = SlatePrimary
                        ),
                        modifier  = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                        shape     = RoundedCornerShape(12.dp),
                        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
                    )
                    ExposedDropdownMenu(
                        expanded         = expandedVisitorType,
                        onDismissRequest = { expandedVisitorType = false }
                    ) {
                        visitorTypeOptions.forEach { option ->
                            DropdownMenuItem(
                                text    = { Text(option.second, fontSize = 14.sp) },
                                onClick = {
                                    selectedVisitorOption = option
                                    expandedVisitorType   = false
                                }
                            )
                        }
                    }
                }

                // ── Document type chips ───────────────────────────────────────
                Text(
                    text = stringResource(R.string.what_doc_type),
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = SlatePrimary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DocumentTypeChip(
                        stringResource(R.string.identity_document_id),
                        selectedDocType == "DUI",
                        { selectedDocType = "DUI" },
                        Modifier.weight(1f)
                    )
                    DocumentTypeChip(
                        stringResource(R.string.passport),
                        selectedDocType == "PASSPORT",
                        { selectedDocType = "PASSPORT" },
                        Modifier.weight(1f)
                    )
                    DocumentTypeChip(
                        stringResource(R.string.other),
                        selectedDocType == "OTHER",
                        { selectedDocType = "OTHER" },
                        Modifier.weight(1f)
                    )
                }

                HorizontalDivider(
                    color    = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )

                // ── Scan section title ────────────────────────────────────────
                Text(
                    text = stringResource(R.string.verify_document),
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = SlatePrimary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = stringResource(R.string.scan_document_hint),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // ── Scan cards ────────────────────────────────────────────────
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ScanDocumentCard(
                        title          = stringResource(R.string.front_of_document),
                        isScanned      = frontScanned,
                        onClick        = { showFrontCamera = true },
                        modifier       = Modifier.weight(1f),
                        capturedBitmap = frontBitmap
                    )
                    ScanDocumentCard(
                        title          = stringResource(R.string.back_of_document),
                        isScanned      = backScanned,
                        onClick        = { showBackCamera = true },
                        modifier       = Modifier.weight(1f),
                        enabled        = frontScanned,
                        capturedBitmap = backBitmap
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ── Continue button ───────────────────────────────────────────
                Button(
                    onClick  = onContinue,
                    enabled  = frontScanned && backScanned && selectedVisitorOption.first.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor         = OrangePrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text       = stringResource(R.string.continue_btn),
                        style      = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // ── Front camera modal ────────────────────────────────────────────
            if (showFrontCamera) {
                DocumentCameraModal(
                    title            = stringResource(R.string.front_of_document),
                    isBackSide       = false,
                    referenceBitmap  = null,
                    selectedLanguage = selectedLanguage,
                    onDismiss        = { showFrontCamera = false },
                    onCapture        = { bitmap, ocrData ->
                        coroutineScope.launch {
                            try {
                                frontBitmap = bitmap
                                val personId = person?.personId ?: "recurrent"
                                val path = ImageSaver.saveImage(
                                    context   = context,
                                    bitmap    = bitmap,
                                    personId  = personId,
                                    imageType = ImageSaver.ImageType.DOCUMENT_FRONT
                                )
                                viewModel?.setDocumentFront(path)
                                frontScanned    = true
                                showFrontCamera = false
                            } catch (e: Exception) {
                                e.printStackTrace()
                                viewModel?.setDocumentFront("")
                                frontScanned    = true
                                showFrontCamera = false
                            }
                        }
                    }
                )
            }

            // ── Back camera modal ─────────────────────────────────────────────
            if (showBackCamera) {
                DocumentCameraModal(
                    title            = stringResource(R.string.back_of_document),
                    isBackSide       = true,
                    referenceBitmap  = frontBitmap,
                    selectedLanguage = selectedLanguage,
                    onDismiss        = { showBackCamera = false },
                    onCapture        = { bitmap, _ ->
                        coroutineScope.launch {
                            try {
                                backBitmap = bitmap
                                val personId = person?.personId ?: "recurrent"
                                val path = ImageSaver.saveImage(
                                    context   = context,
                                    bitmap    = bitmap,
                                    personId  = personId,
                                    imageType = ImageSaver.ImageType.DOCUMENT_BACK
                                )
                                viewModel?.setDocumentBack(path)
                                backScanned    = true
                                showBackCamera = false
                            } catch (e: Exception) {
                                e.printStackTrace()
                                backScanned    = true
                                showBackCamera = false
                            }
                        }
                    }
                )
            }
        }
    }
}
