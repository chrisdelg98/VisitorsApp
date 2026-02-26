package com.eflglobal.visitorsapp.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.eflglobal.visitorsapp.R
import java.text.SimpleDateFormat
import java.util.*

/** Convert any Bitmap to a SQUARE, grayscale crop centered on the image. */
private fun toSquareGrayscale(src: Bitmap): Bitmap {
    // 1 — Square crop from centre
    val size = minOf(src.width, src.height)
    val xOff = (src.width  - size) / 2
    val yOff = (src.height - size) / 2
    val cropped = Bitmap.createBitmap(src, xOff, yOff, size, size)

    // 2 — Grayscale via ColorMatrix
    val result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)
    val paint  = Paint().apply {
        colorFilter = ColorMatrixColorFilter(ColorMatrix().also { it.setSaturation(0f) })
    }
    canvas.drawBitmap(cropped, 0f, 0f, paint)
    if (cropped !== src) cropped.recycle()
    return result
}

@Composable
fun VisitorBadgeButton(
    visitorName: String,
    company: String?,
    visitingPerson: String,
    visitDate: Long,
    printedDate: Long,
    qrBitmap: Bitmap?,
    profileBitmap: Bitmap? = null,
    selectedLanguage: String = "es",
    modifier: Modifier = Modifier
) {
    var showBadge by remember { mutableStateOf(false) }

    // Pre-process photo once
    val grayscaleBitmap: Bitmap? = remember(profileBitmap) {
        profileBitmap?.let { toSquareGrayscale(it) }
    }

    // Botón elegante para mostrar el carnet
    OutlinedButton(
        onClick = { showBadge = true },
        modifier = modifier.height(48.dp),
        shape    = RoundedCornerShape(12.dp),
        colors   = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF273647)),
        border   = BorderStroke(1.5.dp, Color(0xFF273647))
    ) {
        Icon(Icons.Default.Badge, null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.view_visitor_badge), fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }

    // Modal del carnet
    if (showBadge) {
        Dialog(onDismissRequest = { showBadge = false }) {
            VisitorBadgeCard(
                visitorName     = visitorName,
                company         = company,
                visitingPerson  = visitingPerson,
                visitDate       = visitDate,
                printedDate     = printedDate,
                qrBitmap        = qrBitmap,
                grayscaleBitmap = grayscaleBitmap,
                onDismiss       = { showBadge = false },
                onPrint         = { showBadge = false }
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
    grayscaleBitmap: Bitmap?,
    onDismiss: () -> Unit,
    onPrint: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    val timeFormat = SimpleDateFormat("h:mm a",     Locale.getDefault())

    Card(
        modifier  = Modifier.fillMaxWidth(0.92f).wrapContentHeight(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {

            // ── Header ──────────────────────────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    text       = stringResource(R.string.visitor_badge_title),
                    style      = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color      = Color(0xFF273647)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onPrint,
                        colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFF273647))) {
                        Icon(Icons.Default.Print, "Print", tint = Color.White)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close", tint = Color(0xFF273647))
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Badge card body (3:2 aspect ratio) ───────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth().aspectRatio(1.5f),
                shape    = RoundedCornerShape(10.dp),
                colors   = CardDefaults.cardColors(containerColor = Color.White),
                border   = BorderStroke(2.dp, Color.Black)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {

                    // ── Main content ──────────────────────────────────────────
                    Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {

                        // Top black bar — ID
                        Box(
                            modifier = Modifier.fillMaxWidth()
                                .background(Color.Black, RoundedCornerShape(4.dp))
                                .padding(vertical = 5.dp, horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text       = "ID: ${System.currentTimeMillis().toString().takeLast(10)}",
                                color      = Color.White,
                                fontSize   = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        // Photo + data row
                        Row(
                            modifier              = Modifier.weight(1f).fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment     = Alignment.Top
                        ) {
                            // ── Photo (grayscale square) ──────────────────────
                            Box(
                                modifier = Modifier
                                    .size(90.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .border(1.5.dp, Color.Black, RoundedCornerShape(6.dp))
                                    .background(Color(0xFFEEEEEE)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (grayscaleBitmap != null) {
                                    Image(
                                        bitmap       = grayscaleBitmap.asImageBitmap(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier     = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(Icons.Default.Person, null,
                                        tint     = Color.Gray,
                                        modifier = Modifier.size(48.dp))
                                }
                            }

                            // ── Text data ─────────────────────────────────────
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text       = visitorName.uppercase(),
                                    fontSize   = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color      = Color.Black,
                                    lineHeight = 15.sp,
                                    maxLines   = 2
                                )

                                Spacer(Modifier.height(6.dp))

                                if (!company.isNullOrBlank()) {
                                    BadgeField(
                                        label = stringResource(R.string.company_colon),
                                        value = company
                                    )
                                }

                                BadgeField(
                                    label = stringResource(R.string.visiting_colon2),
                                    value = visitingPerson
                                )

                                Spacer(Modifier.height(6.dp))

                                // Valid date bar
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.Black, RoundedCornerShape(4.dp))
                                        .padding(vertical = 3.dp, horizontal = 8.dp)
                                ) {
                                    Text(
                                        text       = stringResource(R.string.valid_colon) + " ${dateFormat.format(Date(visitDate))}",
                                        color      = Color.White,
                                        fontSize   = 8.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(Modifier.height(4.dp))

                                Text(
                                    text     = stringResource(R.string.printed_colon) + " ${dateFormat.format(Date(printedDate))} ${timeFormat.format(Date(printedDate))}",
                                    fontSize = 7.sp,
                                    color    = Color.Gray
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        // ── Footer ─────────────────────────────────────────────
                        HorizontalDivider(color = Color.Black, thickness = 1.dp)
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Text("EFL GLOBAL", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = Color.Black)
                            Text(stringResource(R.string.access_control), fontSize = 7.sp, color = Color.Gray)
                        }
                    }

                    // ── QR in bottom-right corner ─────────────────────────────
                    if (qrBitmap != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 14.dp, bottom = 28.dp)   // above footer
                                .size(70.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .border(1.dp, Color.Black, RoundedCornerShape(6.dp))
                                .background(Color.White)
                                .padding(3.dp)
                        ) {
                            Image(
                                bitmap             = qrBitmap.asImageBitmap(),
                                contentDescription = null,
                                modifier           = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // ── Footer note ──────────────────────────────────────────────────
            Text(
                text      = stringResource(R.string.badge_note),
                fontSize  = 10.sp,
                color     = Color.Gray,
                textAlign = TextAlign.Center,
                lineHeight = 13.sp,
                modifier  = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun BadgeField(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(label, fontSize = 8.sp,  fontWeight = FontWeight.Bold,   color = Color.Gray)
        Text(value, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = Color.Black, maxLines = 1)
    }
}
