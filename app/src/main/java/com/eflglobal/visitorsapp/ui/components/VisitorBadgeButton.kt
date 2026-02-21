package com.eflglobal.visitorsapp.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.text.SimpleDateFormat
import java.util.*

/**
 * Composable que muestra un botón para ver el carnet de visitante.
 */
@Composable
fun VisitorBadgeButton(
    visitorName: String,
    company: String?,
    visitingPerson: String,
    visitDate: Long,
    printedDate: Long,
    qrBitmap: Bitmap?,
    selectedLanguage: String = "es",
    modifier: Modifier = Modifier
) {
    var showBadge by remember { mutableStateOf(false) }

    // Botón elegante para mostrar el carnet
    OutlinedButton(
        onClick = { showBadge = true },
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = Color(0xFF273647)
        ),
        border = BorderStroke(1.5.dp, Color(0xFF273647))
    ) {
        Icon(
            imageVector = Icons.Default.Badge,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (selectedLanguage == "es")
                "Ver Carnet de Visitante"
            else
                "View Visitor Badge",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }

    // Modal del carnet
    if (showBadge) {
        Dialog(onDismissRequest = { showBadge = false }) {
            VisitorBadgeCard(
                visitorName = visitorName,
                company = company,
                visitingPerson = visitingPerson,
                visitDate = visitDate,
                printedDate = printedDate,
                qrBitmap = qrBitmap,
                selectedLanguage = selectedLanguage,
                onDismiss = { showBadge = false },
                onPrint = {
                    // TODO: Implementar lógica de impresión
                    showBadge = false
                }
            )
        }
    }
}

/**
 * Carnet de visitante con diseño profesional en blanco y negro.
 * Tamaño similar a un carnet estándar (proporción 3:2).
 */
@Composable
private fun VisitorBadgeCard(
    visitorName: String,
    company: String?,
    visitingPerson: String,
    visitDate: Long,
    printedDate: Long,
    qrBitmap: Bitmap?,
    selectedLanguage: String,
    onDismiss: () -> Unit,
    onPrint: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

    Card(
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .wrapContentHeight(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 8.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            // Header con botones
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (selectedLanguage == "es")
                        "Carnet de Visitante"
                    else
                        "Visitor Badge",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF273647)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Botón imprimir
                    IconButton(
                        onClick = onPrint,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color(0xFF273647)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = "Print",
                            tint = Color.White
                        )
                    }

                    // Botón cerrar
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF273647)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Carnet visual (proporción 3:2 - tamaño carnet)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.5f),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                ),
                border = BorderStroke(2.dp, Color.Black)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Header del carnet con ID único
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black, RoundedCornerShape(4.dp))
                            .padding(vertical = 6.dp, horizontal = 12.dp)
                    ) {
                        Text(
                            text = "ID: ${generateVisitorId()}",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Contenido principal
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Columna izquierda: Datos
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            // Nombre (destacado)
                            Text(
                                text = visitorName.uppercase(),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.Black,
                                lineHeight = 16.sp,
                                maxLines = 2
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Empresa
                            if (!company.isNullOrBlank()) {
                                BadgeField(
                                    label = if (selectedLanguage == "es") "Empresa:" else "Company:",
                                    value = company
                                )
                            }

                            // Visitando a
                            BadgeField(
                                label = if (selectedLanguage == "es") "Visitando:" else "Visiting:",
                                value = visitingPerson
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Fecha de validez (destacado)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.Black, RoundedCornerShape(4.dp))
                                    .padding(vertical = 4.dp, horizontal = 8.dp)
                            ) {
                                Text(
                                    text = if (selectedLanguage == "es")
                                        "Válido: ${dateFormat.format(Date(visitDate))}"
                                    else
                                        "Valid: ${dateFormat.format(Date(visitDate))}",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Fecha de impresión
                            Text(
                                text = if (selectedLanguage == "es")
                                    "Imprimido: ${dateFormat.format(Date(printedDate))} ${timeFormat.format(Date(printedDate))}"
                                else
                                    "Printed: ${dateFormat.format(Date(printedDate))} ${timeFormat.format(Date(printedDate))}",
                                fontSize = 7.sp,
                                color = Color.Gray
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Columna derecha: QR Code
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.width(100.dp)
                        ) {
                            if (qrBitmap != null) {
                                Card(
                                    modifier = Modifier.size(95.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, Color.Black)
                                ) {
                                    Image(
                                        bitmap = qrBitmap.asImageBitmap(),
                                        contentDescription = "QR Code",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(4.dp)
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(95.dp)
                                        .border(1.dp, Color.Black, RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "QR",
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Footer con branding
                    Divider(color = Color.Black, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "EFL GLOBAL",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.Black
                        )
                        Text(
                            text = if (selectedLanguage == "es")
                                "Control de Acceso"
                            else
                                "Access Control",
                            fontSize = 7.sp,
                            color = Color.Gray
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Nota informativa
            Text(
                text = if (selectedLanguage == "es")
                    "Este carnet es válido únicamente para la fecha indicada. Debe ser portado de manera visible durante toda la visita."
                else
                    "This badge is valid only for the indicated date. It must be worn visibly throughout the visit.",
                fontSize = 11.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                lineHeight = 14.sp,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Campo del carnet.
 */
@Composable
private fun BadgeField(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 3.dp)) {
        Text(
            text = label,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Gray
        )
        Text(
            text = value,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = Color.Black,
            maxLines = 1
        )
    }
}

/**
 * Genera un ID único para el visitante basado en timestamp.
 */
private fun generateVisitorId(): String {
    val timestamp = System.currentTimeMillis()
    return timestamp.toString().takeLast(10)
}

