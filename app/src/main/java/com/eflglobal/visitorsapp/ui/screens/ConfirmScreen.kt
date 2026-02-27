package com.eflglobal.visitorsapp.ui.screens

import android.graphics.Bitmap
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
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
import com.eflglobal.visitorsapp.core.utils.QRCodeGenerator
import com.eflglobal.visitorsapp.ui.components.VisitorBadgeButton
import com.eflglobal.visitorsapp.ui.theme.OrangePrimary
import com.eflglobal.visitorsapp.ui.theme.SlatePrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmScreen(
    onConfirm: () -> Unit,
    onEdit: () -> Unit,
    selectedLanguage: String = "es",
    qrCode: String? = null,
    personName: String? = null,
    visitingPerson: String? = null,
    company: String? = null,
    profilePhotoPath: String? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var showSuccessDialog by remember { mutableStateOf(false) }
    var qrSaved by remember { mutableStateOf(false) }

    // Pre-resolve dialog strings here where LocalContext has the correct locale.
    // AlertDialog creates a new window that breaks the CompositionLocalProvider
    // locale chain, so we must capture the strings before entering the dialog.
    val strRegistrationSuccess       = stringResource(R.string.registration_success)
    val strVisitorRegisteredCorrectly = stringResource(R.string.visitor_registered_correctly)
    val strNoPrinterFound            = stringResource(R.string.no_printer_found)
    val strFinish                    = stringResource(R.string.finish)

    val qrBitmap: Bitmap? = remember(qrCode) {
        qrCode?.let {
            try { QRCodeGenerator.generateQRCode(it, 400) } catch (e: Exception) { null }
        }
    }

    // Load profile photo from path
    val profileBitmap: Bitmap? = remember(profilePhotoPath) {
        profilePhotoPath?.let {
            try { android.graphics.BitmapFactory.decodeFile(it) } catch (e: Exception) { null }
        }
    }

    LaunchedEffect(qrBitmap, qrCode) {
        if (qrBitmap != null && qrCode != null && !qrSaved) {
            coroutineScope.launch {
                try {
                    val visitId = qrCode.removePrefix("VISIT-")
                    ImageSaver.saveVisitImage(context = context, bitmap = qrBitmap, visitId = visitId)
                    qrSaved = true
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.confirmation), fontSize = 18.sp) },
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
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 40.dp, vertical = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(40.dp)
            ) {
                // ===== COLUMNA IZQUIERDA (40%) - Textos, Checkbox y Botones =====
                Column(
                    modifier = Modifier.weight(4f).fillMaxHeight(),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Título principal
                    Text(
                        text = stringResource(R.string.verify_information),
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                        fontWeight = FontWeight.Bold,
                        color = SlatePrimary
                    )

                    Spacer(Modifier.height(12.dp))

                    // Subtítulo/Instrucciones
                    Text(
                        text = stringResource(R.string.confirm_subtitle),
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        lineHeight = 22.sp
                    )

                    Spacer(Modifier.height(32.dp))

                    // Card de documentos verificados (checkbox)
                    Card(
                        modifier = Modifier.fillMaxWidth(0.95f),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = OrangePrimary.copy(alpha = 0.08f)),
                        border = BorderStroke(1.dp, OrangePrimary.copy(alpha = 0.3f))
                    ) {
                        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = OrangePrimary, modifier = Modifier.size(32.dp))
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.documents_verified),
                                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                                    fontWeight = FontWeight.Bold, color = SlatePrimary)
                                Spacer(Modifier.height(4.dp))
                                Text(stringResource(R.string.front_back_scanned),
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), lineHeight = 16.sp)
                            }
                        }
                    }

                    Spacer(Modifier.height(40.dp))

                    // Botones de acción
                    Column(modifier = Modifier.fillMaxWidth(0.95f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        // Botón Confirmar (primero, más prominente)
                        Button(
                            onClick = { showSuccessDialog = true },
                            modifier = Modifier.fillMaxWidth().height(60.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary),
                            shape = RoundedCornerShape(16.dp),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp, pressedElevation = 8.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(R.string.confirm_registration),
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                                fontWeight = FontWeight.Bold)
                        }

                        // Botón Editar (secundario)
                        OutlinedButton(
                            onClick = onEdit,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = SlatePrimary),
                            border = BorderStroke(2.dp, SlatePrimary.copy(alpha = 0.5f))
                        ) {
                            Icon(Icons.Default.Edit, null, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(stringResource(R.string.edit_information),
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                                fontWeight = FontWeight.SemiBold)
                        }

                        // Botón Ver Carnet de Visitante
                        if (qrBitmap != null && personName != null && visitingPerson != null) {
                            VisitorBadgeButton(
                                visitorName      = personName,
                                company          = company,
                                visitingPerson   = visitingPerson,
                                visitDate        = System.currentTimeMillis(),
                                printedDate      = System.currentTimeMillis(),
                                qrBitmap         = qrBitmap,
                                profileBitmap    = profileBitmap,
                                selectedLanguage = selectedLanguage,
                                modifier         = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // ===== COLUMNA DERECHA (60%) - Card de Datos del Visitante =====
                Column(
                    modifier = Modifier.weight(6f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Card de resumen con foto
                    Card(
                        modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp).verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // ── Visitor photo (primary) — full color ─────────
                            if (profileBitmap != null) {
                                androidx.compose.foundation.Image(
                                    bitmap             = profileBitmap.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale       = androidx.compose.ui.layout.ContentScale.Crop,
                                    modifier           = Modifier
                                        .size(160.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .border(3.dp, OrangePrimary, RoundedCornerShape(16.dp))
                                )
                            } else {
                                // Fallback — person icon
                                Box(
                                    modifier = Modifier.size(160.dp)
                                        .background(OrangePrimary.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                                        .border(3.dp, OrangePrimary, RoundedCornerShape(16.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Person, null, tint = OrangePrimary, modifier = Modifier.size(80.dp))
                                }
                            }

                            Spacer(Modifier.height(24.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 8.dp))

                            // Visitor data rows
                            InfoRow(label = stringResource(R.string.full_name_label), value = personName ?: "—")
                            InfoRow(label = stringResource(R.string.visiting_label),  value = visitingPerson ?: "—")
                            InfoRow(
                                label  = stringResource(R.string.date_and_time),
                                value  = java.text.SimpleDateFormat("d MMM yyyy, h:mm a", java.util.Locale.getDefault()).format(java.util.Date()),
                                isLast = qrBitmap == null
                            )

                            // ── QR code (secondary, small) ────────────────────
                            if (qrBitmap != null) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Card(
                                        modifier = Modifier.size(80.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        elevation = CardDefaults.cardElevation(2.dp)
                                    ) {
                                        Box(modifier = Modifier.fillMaxSize().padding(6.dp), contentAlignment = Alignment.Center) {
                                            Image(bitmap = qrBitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize())
                                        }
                                    }
                                    Column {
                                        Text(stringResource(R.string.visit_qr_code),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = OrangePrimary, fontWeight = FontWeight.SemiBold)
                                        Spacer(Modifier.height(4.dp))
                                        Text(stringResource(R.string.keep_qr_code),
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                            lineHeight = 15.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Diálogo de éxito ──
            if (showSuccessDialog) {
                AlertDialog(
                    onDismissRequest = { },
                    icon = {
                        Box(modifier = Modifier.size(80.dp)
                            .background(OrangePrimary.copy(alpha = 0.1f), CircleShape),
                            contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.CheckCircle, null, tint = OrangePrimary, modifier = Modifier.size(48.dp))
                        }
                    },
                    title = {
                        Text(strRegistrationSuccess,
                            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    },
                    text = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(strVisitorRegisteredCorrectly,
                                style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))

                            Spacer(Modifier.height(16.dp))

                            Text(strNoPrinterFound,
                                style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = { showSuccessDialog = false; onConfirm() },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(strFinish,
                                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        }
                    },
                    shape = RoundedCornerShape(24.dp)
                )
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, isLast: Boolean = false) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = SlatePrimary, fontWeight = FontWeight.SemiBold)
        if (!isLast) {
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}
