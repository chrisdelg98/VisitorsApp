package com.eflglobal.visitorsapp.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.eflglobal.visitorsapp.ui.localization.Strings
import com.eflglobal.visitorsapp.ui.theme.OrangePrimary
import com.eflglobal.visitorsapp.ui.theme.SlatePrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurrentDocumentScanScreen(
    visitorName: String,
    documentNumber: String,
    company: String = "",
    email: String = "",
    phone: String = "",
    onContinue: () -> Unit,
    onBack: () -> Unit,
    selectedLanguage: String = "es"
) {
    var showFrontCamera by remember { mutableStateOf(false) }
    var showBackCamera by remember { mutableStateOf(false) }
    var frontImageCaptured by remember { mutableStateOf(false) }
    var backImageCaptured by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (selectedLanguage == "es") "Verificar Documento" else "Verify Document",
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 40.dp, vertical = 32.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Información del visitante
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = SlatePrimary.copy(alpha = 0.05f)
                    ),
                    border = BorderStroke(1.dp, SlatePrimary.copy(alpha = 0.15f))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        // Encabezado
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (selectedLanguage == "es")
                                    "Visitante Registrado"
                                else
                                    "Registered Visitor",
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                fontWeight = FontWeight.Medium
                            )

                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = OrangePrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Nombre
                        Text(
                            text = visitorName,
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                            fontWeight = FontWeight.Bold,
                            color = SlatePrimary
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Grid de información en dos columnas
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Fila 1: Documento y Empresa
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Documento
                                CompactInfoItem(
                                    label = if (selectedLanguage == "es") "Documento" else "Document",
                                    value = documentNumber,
                                    modifier = Modifier.weight(1f)
                                )

                                // Empresa (opcional)
                                if (company.isNotEmpty()) {
                                    CompactInfoItem(
                                        label = if (selectedLanguage == "es") "Empresa" else "Company",
                                        value = company,
                                        modifier = Modifier.weight(1f)
                                    )
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }

                            // Fila 2: Email y Teléfono
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Email
                                CompactInfoItem(
                                    label = if (selectedLanguage == "es") "Correo" else "Email",
                                    value = email,
                                    modifier = Modifier.weight(1f)
                                )

                                // Teléfono
                                CompactInfoItem(
                                    label = if (selectedLanguage == "es") "Teléfono" else "Phone",
                                    value = phone,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Título principal
                Text(
                    text = if (selectedLanguage == "es")
                        "Verificación de Identidad"
                    else
                        "Identity Verification",
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                    fontWeight = FontWeight.Bold,
                    color = SlatePrimary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (selectedLanguage == "es")
                        "Por motivos de certificación, necesitamos verificar su documento nuevamente."
                    else
                        "For certification purposes, we need to verify your document again.",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(horizontal = 40.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Contenedor de las dos columnas para escaneo
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Documento Frente
                    ScanDocumentCard(
                        title = if (selectedLanguage == "es") "Frente del Documento" else "Document Front",
                        isCaptured = frontImageCaptured,
                        onClick = { showFrontCamera = true },
                        selectedLanguage = selectedLanguage,
                        modifier = Modifier.weight(1f)
                    )

                    // Documento Reverso
                    ScanDocumentCard(
                        title = if (selectedLanguage == "es") "Reverso del Documento" else "Document Back",
                        isCaptured = backImageCaptured,
                        onClick = { showBackCamera = true },
                        selectedLanguage = selectedLanguage,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Botón continuar
                Button(
                    onClick = onContinue,
                    enabled = frontImageCaptured && backImageCaptured,
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = OrangePrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(14.dp),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 4.dp
                    )
                ) {
                    Text(
                        text = Strings.continueBtn(selectedLanguage),
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                        fontWeight = FontWeight.Bold
                    )
                }

                if (!frontImageCaptured || !backImageCaptured) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (selectedLanguage == "es")
                            "Escanee ambos lados del documento para continuar"
                        else
                            "Scan both sides of the document to continue",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Modal para escanear frente
            if (showFrontCamera) {
                CameraDialog(
                    title = if (selectedLanguage == "es") "Escanear Frente" else "Scan Front",
                    instruction = if (selectedLanguage == "es")
                        "Coloque el frente del documento dentro del marco"
                    else
                        "Place the front of the document inside the frame",
                    onDismiss = { showFrontCamera = false },
                    onCapture = {
                        frontImageCaptured = true
                        showFrontCamera = false
                    },
                    selectedLanguage = selectedLanguage
                )
            }

            // Modal para escanear reverso
            if (showBackCamera) {
                CameraDialog(
                    title = if (selectedLanguage == "es") "Escanear Reverso" else "Scan Back",
                    instruction = if (selectedLanguage == "es")
                        "Coloque el reverso del documento dentro del marco"
                    else
                        "Place the back of the document inside the frame",
                    onDismiss = { showBackCamera = false },
                    onCapture = {
                        backImageCaptured = true
                        showBackCamera = false
                    },
                    selectedLanguage = selectedLanguage
                )
            }
        }
    }
}

@Composable
private fun ScanDocumentCard(
    title: String,
    isCaptured: Boolean,
    onClick: () -> Unit,
    selectedLanguage: String,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCaptured)
                OrangePrimary.copy(alpha = 0.08f)
            else
                MaterialTheme.colorScheme.surface
        ),
        border = if (isCaptured)
            BorderStroke(2.dp, OrangePrimary)
        else
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp,
            pressedElevation = 6.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Icono o check
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(
                        color = if (isCaptured)
                            OrangePrimary.copy(alpha = 0.15f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(16.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isCaptured) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = OrangePrimary,
                        modifier = Modifier.size(48.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            // Título
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                fontWeight = FontWeight.SemiBold,
                color = if (isCaptured) OrangePrimary else SlatePrimary,
                textAlign = TextAlign.Center
            )

            // Estado
            Text(
                text = if (isCaptured) {
                    if (selectedLanguage == "es") "✓ Capturado" else "✓ Captured"
                } else {
                    if (selectedLanguage == "es") "Toque para escanear" else "Tap to scan"
                },
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurface.copy(
                    alpha = if (isCaptured) 0.8f else 0.5f
                ),
                fontWeight = if (isCaptured) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun CameraDialog(
    title: String,
    instruction: String,
    onDismiss: () -> Unit,
    onCapture: () -> Unit,
    selectedLanguage: String
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Título
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                    fontWeight = FontWeight.Bold,
                    color = SlatePrimary
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Área de cámara (placeholder)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .border(
                            width = 3.dp,
                            color = OrangePrimary,
                            shape = RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            text = instruction,
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                        Text(
                            text = if (selectedLanguage == "es")
                                "La captura se realizará automáticamente"
                            else
                                "Capture will be done automatically",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Botones
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.5.dp, SlatePrimary.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = Strings.cancel(selectedLanguage),
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.sp),
                            fontWeight = FontWeight.SemiBold,
                            color = SlatePrimary
                        )
                    }

                    Button(
                        onClick = onCapture,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OrangePrimary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (selectedLanguage == "es") "Capturar" else "Capture",
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.sp),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactInfoItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
            fontWeight = FontWeight.SemiBold
        )
    }
}
