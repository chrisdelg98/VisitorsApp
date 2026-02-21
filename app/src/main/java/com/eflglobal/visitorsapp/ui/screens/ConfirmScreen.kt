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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eflglobal.visitorsapp.core.utils.QRCodeGenerator
import com.eflglobal.visitorsapp.ui.localization.Strings
import com.eflglobal.visitorsapp.ui.theme.OrangePrimary
import com.eflglobal.visitorsapp.ui.theme.SlatePrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmScreen(
    onConfirm: () -> Unit,
    onEdit: () -> Unit,
    selectedLanguage: String = "es",
    qrCode: String? = null,
    personName: String? = null,
    visitingPerson: String? = null
) {
    var showSuccessDialog by remember { mutableStateOf(false) }
    var printerAvailable by remember { mutableStateOf(false) }

    // Generar QR Code si existe el código
    val qrBitmap: Bitmap? = remember(qrCode) {
        qrCode?.let {
            try {
                QRCodeGenerator.generateQRCode(it, 400)
            } catch (e: Exception) {
                null
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        Strings.confirmation(selectedLanguage),
                        fontSize = 18.sp
                    )
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
                // ===== COLUMNA IZQUIERDA (40%) - Textos, Checkbox y Botones =====
                Column(
                    modifier = Modifier
                        .weight(4f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Título principal
                    Text(
                        text = Strings.verifyInformation(selectedLanguage),
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                        fontWeight = FontWeight.Bold,
                        color = SlatePrimary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Subtítulo/Instrucciones
                    Text(
                        text = Strings.confirmSubtitle(selectedLanguage),
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        lineHeight = 22.sp
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    // Card de documentos verificados (checkbox)
                    Card(
                        modifier = Modifier.fillMaxWidth(0.95f),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = OrangePrimary.copy(alpha = 0.08f)
                        ),
                        border = BorderStroke(1.dp, OrangePrimary.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = OrangePrimary,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = Strings.documentsVerified(selectedLanguage),
                                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = SlatePrimary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = Strings.frontAndBackScanned(selectedLanguage),
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(40.dp))

                    // Botones de acción
                    Column(
                        modifier = Modifier.fillMaxWidth(0.95f),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Botón Confirmar (primero, más prominente)
                        Button(
                            onClick = { showSuccessDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = OrangePrimary
                            ),
                            shape = RoundedCornerShape(16.dp),
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = 6.dp,
                                pressedElevation = 8.dp
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = Strings.confirmRegistration(selectedLanguage),
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Botón Editar (secundario)
                        OutlinedButton(
                            onClick = onEdit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = SlatePrimary
                            ),
                            border = BorderStroke(2.dp, SlatePrimary.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = Strings.editInformation(selectedLanguage),
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // ===== COLUMNA DERECHA (60%) - Card de Datos del Visitante =====
                Column(
                    modifier = Modifier
                        .weight(6f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Card de resumen con foto
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(
                            defaultElevation = 8.dp
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(32.dp)
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // QR Code generado
                            if (qrBitmap != null) {
                                Card(
                                    modifier = Modifier.size(200.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    ),
                                    elevation = CardDefaults.cardElevation(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Image(
                                            bitmap = qrBitmap.asImageBitmap(),
                                            contentDescription = "Visit QR Code",
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text = if (selectedLanguage == "es")
                                        "Código QR de Visita"
                                    else
                                        "Visit QR Code",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = OrangePrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            } else {
                                // Foto del visitante (simulada)
                                Box(
                                    modifier = Modifier
                                        .size(140.dp)
                                        .background(
                                            color = OrangePrimary.copy(alpha = 0.15f),
                                            shape = CircleShape
                                        )
                                        .border(4.dp, OrangePrimary, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = OrangePrimary,
                                        modifier = Modifier.size(70.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )

                            // Información del visitante
                            InfoRow(
                                label = Strings.fullNameLabel(selectedLanguage),
                                value = personName ?: "Juan Carlos Pérez Martínez"
                            )

                            InfoRow(
                                label = Strings.visitTo(selectedLanguage),
                                value = visitingPerson ?: "María García"
                            )

                            InfoRow(
                                label = Strings.dateAndTime(selectedLanguage),
                                value = java.text.SimpleDateFormat(
                                    "d MMM yyyy, h:mm a",
                                    java.util.Locale.getDefault()
                                ).format(java.util.Date()),
                                isLast = true
                            )
                        }
                    }
                }
            }

            // Diálogo de éxito
            if (showSuccessDialog) {
                AlertDialog(
                    onDismissRequest = { },
                    icon = {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .background(
                                    color = OrangePrimary.copy(alpha = 0.1f),
                                    shape = CircleShape
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
                            text = Strings.registrationSuccess(selectedLanguage),
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
                            Text(
                                text = Strings.visitorRegisteredCorrectly(selectedLanguage),
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )

                            if (!printerAvailable) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = Strings.noPrinterFound(selectedLanguage),
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showSuccessDialog = false
                                onConfirm()
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

@Composable
private fun InfoRow(
    label: String,
    value: String,
    isLast: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = SlatePrimary,
            fontWeight = FontWeight.SemiBold
        )

        if (!isLast) {
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}




