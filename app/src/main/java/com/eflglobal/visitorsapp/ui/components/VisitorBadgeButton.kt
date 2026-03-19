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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.eflglobal.visitorsapp.R
import com.eflglobal.visitorsapp.core.printing.BadgeBitmapRenderer
import com.eflglobal.visitorsapp.core.printing.PrintResult
import com.eflglobal.visitorsapp.core.printing.PrinterManager
import kotlinx.coroutines.launch
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
    firstName: String,
    lastName: String,
    company: String?,
    visitingPerson: String,
    visitDate: Long,
    printedDate: Long,
    qrBitmap: Bitmap?,
    profileBitmap: Bitmap? = null,
    visitorType: String = "VISITOR",
    selectedLanguage: String = "es",
    /** Raw QR value (e.g. "VISIT-…") — used by ZPL to encode the barcode. */
    qrCode: String? = null,
    modifier: Modifier = Modifier
) {
    val context       = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showBadge by remember { mutableStateOf(false) }

    // Print status: null = idle, true = printing, false = result shown
    var isPrinting   by remember { mutableStateOf(false) }
    var printMessage by remember { mutableStateOf<String?>(null) }
    var printSuccess by remember { mutableStateOf(false) }

    val grayscaleBitmap: Bitmap? = remember(profileBitmap) {
        profileBitmap?.let { toSquareGrayscale(it) }
    }

    // Resolve localized strings in correct locale context
    val strBadgeTitle   = stringResource(R.string.visitor_badge_title)
    val strViewBadge    = stringResource(R.string.view_visitor_badge)
    val strVisiting     = stringResource(R.string.visiting_colon2)
    val strValid        = stringResource(R.string.valid_colon)
    val strPrinted      = stringResource(R.string.printed_colon)
    val strBadgeNote    = stringResource(R.string.badge_note)
    val strCompany      = stringResource(R.string.company_colon)
    val strPrinting     = stringResource(R.string.printing)
    val strPrintOk      = stringResource(R.string.print_success)

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

    // ── "Print" action ────────────────────────────────────────────────────────
    // Load logo bitmap once for printing
    val logoBitmap: Bitmap? = remember {
        try {
            android.graphics.BitmapFactory.decodeResource(context.resources, R.drawable.logo)
        } catch (_: Exception) { null }
    }

    val onPrintClicked: () -> Unit = {
        if (!isPrinting) {
            coroutineScope.launch {
                isPrinting   = true
                printMessage = strPrinting

                val renderData = BadgeBitmapRenderer.RenderData(
                    firstName        = firstName,
                    lastName         = lastName,
                    company          = company,
                    visitingPerson   = visitingPerson,
                    visitorTypeLabel = strVisitorTypeLabel,
                    entryDate        = visitDate,
                    profileBitmap    = profileBitmap,
                    qrBitmap         = qrBitmap,
                    logoBitmap       = logoBitmap,
                    labelBadgeTitle  = strBadgeTitle,
                    labelCompany     = strCompany,
                    labelVisiting    = strVisiting,
                    labelValid       = strValid,
                    labelValidFor    = strBadgeNote,
                    labelPrinted     = strPrinted
                )

                val result = PrinterManager.printBadge(context, renderData)

                isPrinting = false
                when (result) {
                    is PrintResult.Success -> {
                        printSuccess = true
                        printMessage = strPrintOk
                    }
                    is PrintResult.PermissionRequested -> {
                        printSuccess = false
                        printMessage = "USB permission requested. Tap Print again."
                    }
                    is PrintResult.Error -> {
                        printSuccess = false
                        printMessage = result.message
                    }
                }
                // Auto-clear message after 4 s
                kotlinx.coroutines.delay(4_000)
                printMessage = null
                printSuccess = false
            }
        }
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

    // ── Print status snackbar ─────────────────────────────────────────────────
    printMessage?.let { msg ->
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape  = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (printSuccess) Color(0xFF2E7D32) else
                        if (isPrinting) Color(0xFF1565C0) else Color(0xFFC62828)
                ),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isPrinting) CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.White, strokeWidth = 2.dp
                    )
                    else Icon(
                        if (printSuccess) Icons.Default.Badge else Icons.Default.Print,
                        null, tint = Color.White, modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(msg, color = Color.White, fontSize = 13.sp)
                }
            }
        }
    }

    if (showBadge) {
        Dialog(onDismissRequest = { showBadge = false }) {
            VisitorBadgeCard(
                firstName           = firstName,
                lastName            = lastName,
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
                onPrint             = onPrintClicked
            )
        }
    }
}

@Composable
private fun VisitorBadgeCard(
    firstName: String,
    lastName: String,
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

                    // ── Main content: Left column (Photo + QR) | Right column (Data) ──
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment     = Alignment.Top
                    ) {
                        // Left column: Photo + QR stacked
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
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

                            // QR code (same width as photo)
                            if (qrBitmap != null) {
                                Box(
                                    modifier = Modifier
                                        .size(110.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .border(1.dp, Color(0xFFBDBDBD), RoundedCornerShape(6.dp))
                                        .background(Color.White)
                                        .padding(4.dp)
                                ) {
                                    Image(
                                        bitmap             = qrBitmap.asImageBitmap(),
                                        contentDescription = null,
                                        modifier           = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }

                        // Right column: Data (starts from top) + visitor type pill at bottom
                        Column(
                            modifier              = Modifier.weight(1f),
                            verticalArrangement   = Arrangement.spacedBy(6.dp)
                        ) {
                            // First name (bold)
                            Text(
                                text       = firstName.uppercase(),
                                fontSize   = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color      = Color(0xFF212121),
                                lineHeight = 16.sp,
                                maxLines   = 1,
                                overflow   = TextOverflow.Ellipsis
                            )

                            // Last name (bold)
                            Text(
                                text       = lastName.uppercase(),
                                fontSize   = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color      = Color(0xFF212121),
                                lineHeight = 16.sp,
                                maxLines   = 1,
                                overflow   = TextOverflow.Ellipsis
                            )

                            // Company
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

                            Spacer(Modifier.weight(1f))

                            // Visitor type pill (bottom-right)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFFEEEEEE), RoundedCornerShape(50.dp))
                                        .padding(horizontal = 14.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text       = visitorTypeLabel,
                                        fontSize   = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        color      = Color(0xFF424242)
                                    )
                                }
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

