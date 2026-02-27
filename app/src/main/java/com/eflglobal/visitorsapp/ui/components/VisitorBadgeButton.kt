package com.eflglobal.visitorsapp.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.eflglobal.visitorsapp.R
import java.text.SimpleDateFormat
import java.util.*

/** Convert any Bitmap to a SQUARE, grayscale crop centered on the image. */
private fun toSquareGrayscale(src: Bitmap): Bitmap {
    val size = minOf(src.width, src.height)
    val xOff = (src.width  - size) / 2
    val yOff = (src.height - size) / 2
    val cropped = Bitmap.createBitmap(src, xOff, yOff, size, size)
    val result  = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas  = Canvas(result)
    val paint   = Paint().apply {
        colorFilter = ColorMatrixColorFilter(
            android.graphics.ColorMatrix().also { it.setSaturation(0f) }
        )
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
    visitorType: String = "VISITOR",
    selectedLanguage: String = "es",
    modifier: Modifier = Modifier
) {
    var showBadge by remember { mutableStateOf(false) }

    val grayscaleBitmap: Bitmap? = remember(profileBitmap) {
        profileBitmap?.let { toSquareGrayscale(it) }
    }

    // Resolve all strings here in the correct locale context
    val strBadgeTitle   = stringResource(R.string.visitor_badge_title)
    val strViewBadge    = stringResource(R.string.view_visitor_badge)
    val strVisiting     = stringResource(R.string.visiting_colon2)
    val strValid        = stringResource(R.string.valid_colon)
    val strPrinted      = stringResource(R.string.printed_colon)
    val strBadgeNote    = stringResource(R.string.badge_note)
    val strCompany      = stringResource(R.string.company_colon)

    // Map visitorType key → localized label using existing string resources
    val strVisitorTypeLabel = when (visitorType) {
        "VISITOR"         -> stringResource(R.string.visitor_type)
        "CONTRACTOR"      -> stringResource(R.string.contractor)
        "VENDOR"          -> stringResource(R.string.vendor)
        "DELIVERY"        -> stringResource(R.string.delivery)
        "DRIVER"          -> stringResource(R.string.driver)
        "TEMPORARY_STAFF" -> stringResource(R.string.temporary_staff)
        "OTHER"           -> stringResource(R.string.other)
        else              -> stringResource(R.string.visitor_type)
    }

    OutlinedButton(
        onClick  = { showBadge = true },
        modifier = modifier.height(48.dp),
        shape    = RoundedCornerShape(12.dp),
        colors   = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF273647)),
        border   = BorderStroke(1.5.dp, Color(0xFF273647))
    ) {
        Icon(Icons.Default.Badge, null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(strViewBadge, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }

    if (showBadge) {
        Dialog(onDismissRequest = { showBadge = false }) {
            VisitorBadgeCard(
                visitorName         = visitorName,
                company             = company,
                visitingPerson      = visitingPerson,
                visitDate           = visitDate,
                printedDate         = printedDate,
                qrBitmap            = qrBitmap,
                grayscaleBitmap     = grayscaleBitmap,
                visitorTypeLabel    = strVisitorTypeLabel,
                strBadgeTitle       = strBadgeTitle,
                strCompany          = strCompany,
                strVisiting         = strVisiting,
                strValid            = strValid,
                strPrinted          = strPrinted,
                strBadgeNote        = strBadgeNote,
                onDismiss           = { showBadge = false },
                onPrint             = { showBadge = false }
            )
        }
    }
}

@Composable
private fun VisitorBadgeCard(
    visitorName: String,
    company: String?,
    visitingPerson: String,
    visitDate: Long,
    printedDate: Long,
    qrBitmap: Bitmap?,
    grayscaleBitmap: Bitmap?,
    visitorTypeLabel: String,
    strBadgeTitle: String,
    strCompany: String,
    strVisiting: String,
    strValid: String,
    strPrinted: String,
    strBadgeNote: String,
    onDismiss: () -> Unit,
    onPrint: () -> Unit
) {
    val dateFormat     = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    val dateTimeFormat = SimpleDateFormat("dd/MM/yyyy h:mm a", Locale.getDefault())

    Card(
        modifier  = Modifier.fillMaxWidth(0.96f).wrapContentHeight(),
        shape     = RoundedCornerShape(20.dp),
        colors    = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {

            // ── Action row (top) ─────────────────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onPrint,
                    modifier = Modifier.size(36.dp),
                    colors   = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFFE0E0E0))
                ) {
                    Icon(Icons.Default.Print, null, tint = Color(0xFF424242), modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(6.dp))
                IconButton(
                    onClick  = onDismiss,
                    modifier = Modifier.size(36.dp),
                    colors   = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFFE0E0E0))
                ) {
                    Icon(Icons.Default.Close, null, tint = Color(0xFF424242), modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Badge card ───────────────────────────────────────────────────
            Card(
                modifier  = Modifier.fillMaxWidth(),
                shape     = RoundedCornerShape(14.dp),
                colors    = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {

                    // ── Header: Logo | Title ─────────────────────────────────
                    Row(
                        modifier          = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Logo in grayscale
                        Image(
                            painter     = painterResource(R.drawable.logo),
                            contentDescription = null,
                            modifier    = Modifier.height(36.dp).widthIn(max = 80.dp),
                            contentScale = ContentScale.Fit,
                            colorFilter  = ColorFilter.colorMatrix(
                                androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(0f) }
                            )
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text       = strBadgeTitle,
                            fontSize   = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color      = Color(0xFF212121),
                            modifier   = Modifier.weight(1f)
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = Color(0xFFBDBDBD), thickness = 1.dp)
                    Spacer(Modifier.height(12.dp))

                    // ── Main row: Photo | Data ───────────────────────────────
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment     = Alignment.Top
                    ) {
                        // Photo (grayscale square)
                        Box(
                            modifier = Modifier
                                .size(110.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFFBDBDBD), RoundedCornerShape(8.dp))
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
                                    modifier = Modifier.size(44.dp))
                            }
                        }

                        // Data column
                        Column(
                            modifier              = Modifier.weight(1f),
                            verticalArrangement   = Arrangement.spacedBy(6.dp)
                        ) {
                            // Full name (bold, large)
                            Text(
                                text       = visitorName.uppercase(),
                                fontSize   = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color      = Color(0xFF212121),
                                lineHeight = 16.sp,
                                maxLines   = 2,
                                overflow   = TextOverflow.Ellipsis
                            )

                            // Company — always visible, label is fully translatable
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(strCompany, fontSize = 11.sp, color = Color(0xFF9E9E9E))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text       = if (!company.isNullOrBlank()) company else "—",
                                    fontSize   = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color      = Color(0xFF424242),
                                    maxLines   = 1,
                                    overflow   = TextOverflow.Ellipsis
                                )
                            }

                            // Visiting
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(strVisiting, fontSize = 11.sp, color = Color(0xFF9E9E9E))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text       = visitingPerson,
                                    fontSize   = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color      = Color(0xFF212121),
                                    maxLines   = 1,
                                    overflow   = TextOverflow.Ellipsis
                                )
                            }

                            // ID chip (visit date as unique badge ID)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF0F0F0), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text       = "ID: ${visitDate.toString().takeLast(10)}",
                                    fontSize   = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color      = Color(0xFF424242)
                                )
                            }

                            // Valid until
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(strValid, fontSize = 10.sp, color = Color(0xFF9E9E9E))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text       = dateFormat.format(Date(visitDate)),
                                    fontSize   = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color      = Color(0xFF212121)
                                )
                            }

                            // Printed on
                            Text(
                                text     = "${strPrinted} ${dateTimeFormat.format(Date(printedDate))}",
                                fontSize = 9.sp,
                                color    = Color(0xFF9E9E9E)
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = Color(0xFFEEEEEE), thickness = 1.dp)
                    Spacer(Modifier.height(10.dp))

                    // ── Footer: Visitor type chip | QR ───────────────────────
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        // Visitor type pill
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFEEEEEE), RoundedCornerShape(50.dp))
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text       = visitorTypeLabel,
                                fontSize   = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color      = Color(0xFF424242)
                            )
                        }

                        // QR code
                        if (qrBitmap != null) {
                            Box(
                                modifier = Modifier
                                    .size(76.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .border(1.dp, Color(0xFFBDBDBD), RoundedCornerShape(6.dp))
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
            }

            Spacer(Modifier.height(12.dp))

            // ── Badge note ───────────────────────────────────────────────────
            Text(
                text       = strBadgeNote,
                fontSize   = 10.sp,
                color      = Color.Gray,
                textAlign  = TextAlign.Center,
                lineHeight = 14.sp,
                modifier   = Modifier.fillMaxWidth()
            )
        }
    }
}

