package com.eflglobal.visitorsapp.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Portrait
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eflglobal.visitorsapp.ui.localization.Strings
import com.eflglobal.visitorsapp.ui.theme.OrangePrimary
import com.eflglobal.visitorsapp.ui.theme.SlatePrimary
import com.eflglobal.visitorsapp.ui.viewmodel.NewVisitViewModel
import com.eflglobal.visitorsapp.ui.viewmodel.NewVisitUiState
import com.eflglobal.visitorsapp.ui.viewmodel.ViewModelFactory
import kotlinx.coroutines.delay

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
    // Obtener nombre detectado del ViewModel
    val detectedName = viewModel.getDetectedName() ?: ""

    // Si hay un nombre detectado, usarlo como valor inicial
    var fullName by remember { mutableStateOf(detectedName) }
    var company by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var visitingPerson by remember { mutableStateOf("") }
    var photoTaken by remember { mutableStateOf(false) }
    var isCapturing by remember { mutableStateOf(false) }
    var countdown by remember { mutableStateOf(0) }

    // Indicador si el nombre fue detectado automáticamente
    val isNameAutoDetected = detectedName.isNotEmpty()

    // Observar estado del ViewModel
    val uiState by viewModel.uiState.collectAsState()

    // Manejar navegación cuando se crea exitosamente la visita
    LaunchedEffect(uiState) {
        if (uiState is NewVisitUiState.Success) {
            onContinue()
        }
    }

    LaunchedEffect(isCapturing) {
        if (isCapturing) {
            countdown = 5
            while (countdown > 0) {
                delay(1000)
                countdown--
            }
            if (countdown == 0) {
                photoTaken = true
                isCapturing = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        Strings.visitorInformation(selectedLanguage),
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            // Sección de datos personales
            Text(
                text = Strings.personalInformation(selectedLanguage),
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                fontWeight = FontWeight.SemiBold,
                color = SlatePrimary,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Primera fila: Nombre y Apellido (mitad) + Empresa (mitad)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Campo nombre y apellido (mitad del ancho)
                Column(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = fullName,
                        onValueChange = { fullName = it },
                        label = { Text(Strings.fullName(selectedLanguage), fontSize = 12.sp) },
                        placeholder = { Text(Strings.enterFullName(selectedLanguage), fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = if (isNameAutoDetected) OrangePrimary else SlatePrimary,
                            focusedLabelColor = if (isNameAutoDetected) OrangePrimary else SlatePrimary
                        ),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
                    )

                    // Indicador si fue detectado automáticamente
                    if (isNameAutoDetected) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "✓ ${Strings.detectedFromDocument(selectedLanguage)}",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                            color = OrangePrimary,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                }

                // Campo empresa (opcional)
                OutlinedTextField(
                    value = company,
                    onValueChange = { company = it },
                    label = { Text("${Strings.company(selectedLanguage)} (${Strings.optional(selectedLanguage)})", fontSize = 12.sp) },
                    placeholder = { Text(Strings.company(selectedLanguage), fontSize = 12.sp) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SlatePrimary,
                        focusedLabelColor = SlatePrimary
                    ),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
                )
            }

            // Segunda fila: Email y Teléfono
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Campo correo electrónico
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(Strings.email(selectedLanguage), fontSize = 12.sp) },
                    placeholder = { Text(Strings.enterEmail(selectedLanguage), fontSize = 12.sp) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SlatePrimary,
                        focusedLabelColor = SlatePrimary
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
                )

                // Campo teléfono
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text(Strings.phone(selectedLanguage), fontSize = 12.sp) },
                    placeholder = { Text(Strings.enterPhone(selectedLanguage), fontSize = 12.sp) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SlatePrimary,
                        focusedLabelColor = SlatePrimary
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
                )
            }

            // Campo a quién visita - ANCHO COMPLETO
            OutlinedTextField(
                value = visitingPerson,
                onValueChange = { visitingPerson = it },
                label = { Text(Strings.whoVisiting(selectedLanguage), fontSize = 12.sp) },
                placeholder = { Text(Strings.enterWhoVisiting(selectedLanguage), fontSize = 12.sp) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SlatePrimary,
                    focusedLabelColor = SlatePrimary
                ),
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(vertical = 16.dp)
            )

            // Sección de foto personal - Compacta
            Text(
                text = Strings.personalPhoto(selectedLanguage),
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                fontWeight = FontWeight.SemiBold,
                color = SlatePrimary,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Preview compacto y botón
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Preview de la foto
                Card(
                    modifier = Modifier.size(170.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (photoTaken)
                            OrangePrimary.copy(alpha = 0.1f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    ),
                    border = if (photoTaken)
                        BorderStroke(2.dp, OrangePrimary)
                    else
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (photoTaken) {
                            // Simulación de foto con inicial
                            if (fullName.isNotEmpty()) {
                                Text(
                                    text = fullName.first().uppercase(),
                                    style = MaterialTheme.typography.displayMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = OrangePrimary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = OrangePrimary,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Default.Portrait,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                }

                // Información y botón
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (photoTaken) {
                            "✓ ${Strings.photoTakenSuccessfully(selectedLanguage)}"
                        } else {
                            Strings.personalPhoto(selectedLanguage)
                        },
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                        color = if (photoTaken) OrangePrimary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        fontWeight = if (photoTaken) FontWeight.SemiBold else FontWeight.Normal
                    )

                    Button(
                        onClick = { isCapturing = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OrangePrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Portrait,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (photoTaken) {
                                Strings.retakePhoto(selectedLanguage)
                            } else {
                                Strings.takePhoto(selectedLanguage)
                            },
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Botón continuar
            Button(
                onClick = {
                    // TODO: Aquí se pasará el path real de la foto
                    val profilePhotoPath = if (photoTaken) "profile_${System.currentTimeMillis()}.jpg" else null

                    viewModel.createPersonAndVisit(
                        fullName = fullName,
                        email = email,
                        phoneNumber = phone,
                        company = company.ifBlank { null },
                        visitingPersonName = visitingPerson,
                        profilePhotoPath = profilePhotoPath
                    )
                },
                enabled = fullName.isNotBlank() && email.isNotBlank() && phone.isNotBlank() && visitingPerson.isNotBlank() && photoTaken && uiState !is NewVisitUiState.Loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = OrangePrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (uiState is NewVisitUiState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        text = Strings.continueBtn(selectedLanguage),
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Mostrar error si existe
            if (uiState is NewVisitUiState.Error) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = (uiState as NewVisitUiState.Error).message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Modal fullscreen para capturar foto
        if (isCapturing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header con título y botón cerrar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 32.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = Strings.personalPhoto(selectedLanguage),
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                            fontWeight = FontWeight.SemiBold,
                            color = SlatePrimary
                        )

                        TextButton(
                            onClick = {
                                isCapturing = false
                                countdown = 0
                            }
                        ) {
                            Text(
                                text = Strings.cancel(selectedLanguage),
                                fontSize = 14.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Área de captura centrada
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        // Preview de captura
                        Card(
                            modifier = Modifier
                                .size(300.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            border = BorderStroke(3.dp, OrangePrimary.copy(alpha = 0.5f))
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                if (countdown > 0) {
                                    // Mostrando countdown
                                    Box(
                                        modifier = Modifier.size(150.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            progress = { countdown / 5f },
                                            modifier = Modifier.size(150.dp),
                                            color = OrangePrimary,
                                            strokeWidth = 8.dp,
                                            trackColor = MaterialTheme.colorScheme.surface
                                        )

                                        Text(
                                            text = countdown.toString(),
                                            style = MaterialTheme.typography.displayLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = OrangePrimary
                                        )
                                    }
                                } else {
                                    // Guía de rostro
                                    Box(
                                        modifier = Modifier
                                            .size(200.dp)
                                            .background(
                                                color = MaterialTheme.colorScheme.surface,
                                                shape = CircleShape
                                            )
                                            .border(
                                                width = 4.dp,
                                                color = OrangePrimary.copy(alpha = 0.5f),
                                                shape = CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Portrait,
                                            contentDescription = null,
                                            tint = OrangePrimary.copy(alpha = 0.5f),
                                            modifier = Modifier.size(100.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Instrucciones
                        Text(
                            text = if (countdown > 0) {
                                Strings.lookAtCamera(selectedLanguage)
                            } else {
                                Strings.positionFace(selectedLanguage)
                            },
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                            color = SlatePrimary,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Botón de captura
                    if (countdown == 0) {
                        Button(
                            onClick = { countdown = 5 },
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = OrangePrimary
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Portrait,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = Strings.takePhoto(selectedLanguage),
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}






