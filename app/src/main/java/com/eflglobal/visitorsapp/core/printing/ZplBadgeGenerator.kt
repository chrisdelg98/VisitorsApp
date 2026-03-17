package com.eflglobal.visitorsapp.core.printing

import android.graphics.Bitmap
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Generates ZPL II code for the visitor badge.
 *
 * Target hardware : Zebra ZT230 at 203 DPI
 * Label size      : 4" × 3"  →  812 × 609 dots
 *
 * Layout (approximate):
 * ┌─ header (solid, h=75) ──────────────────────────┐
 * │  VISITOR BADGE                    EFL Global    │
 * ├─────────────────────────────────────────────────┤
 * │ [Photo]   Name (large)           [QR code]     │
 * │ 155×190   Company                              │
 * │           Visiting: xxx                        │
 * │           Date                                 │
 * ├─────────────────────────────────────────────────┤
 * │ [Type]         Badge note                      │
 * ├─────────────────────────────────────────────────┤
 * │ Printed: dd/MM/yyyy HH:mm                      │
 * └─────────────────────────────────────────────────┘
 */
object ZplBadgeGenerator {

    data class BadgeData(
        val visitorName: String,
        val company: String?,
        val visitingPerson: String,
        val visitorTypeLabel: String,
        val qrValue: String,
        val entryDate: Long,
        val profileBitmap: Bitmap? = null,
        // Localized labels (populated from string resources before calling generate())
        val labelBadgeTitle: String  = "VISITOR BADGE",
        val labelVisiting: String    = "Visiting",
        val labelValidFor: String    = "Valid for 24 hours",
        val labelPrinted: String     = "Printed"
    )

    // ── Label dimensions at 203 DPI ──────────────────────────────────────────
    private const val LABEL_W = 812
    private const val LABEL_H = 609

    // ── Photo area ───────────────────────────────────────────────────────────
    private const val PHOTO_X = 20
    private const val PHOTO_Y = 85
    private const val PHOTO_W = 155
    private const val PHOTO_H = 190

    // ── Text block starts right of photo ─────────────────────────────────────
    private const val TEXT_X = 195

    // ── QR code ──────────────────────────────────────────────────────────────
    private const val QR_X   = 598   // right column
    private const val QR_Y   = 85
    private const val QR_MAG = 5     // magnification → ~155×155 dots

    fun generate(data: BadgeData): String {
        val dateFmt     = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val dateTimeFmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

        val name      = data.visitorName.take(22)
        val company   = data.company?.take(30) ?: ""
        val visiting  = data.visitingPerson.take(26)
        val dateStr   = dateFmt.format(Date(data.entryDate))
        val printedAt = dateTimeFmt.format(Date())

        return buildString {
            // ── Label setup ────────────────────────────────────────────────
            append("^XA")
            append("^PW$LABEL_W")
            append("^LL$LABEL_H")
            append("^CI28")          // UTF-8 encoding

            // ── Header bar (solid black, height 75) ────────────────────────
            append("^FO0,0^GB${LABEL_W},75,75^FS")
            // Title text in white (^FR = field reverse)
            append("^FO20,15^A0N,42,38^FR^FD${data.labelBadgeTitle}^FS")
            // Company name right side of header
            append("^FO570,23^A0N,26,22^FR^FDEFL Global^FS")

            // ── Visitor photo (or placeholder box) ─────────────────────────
            if (data.profileBitmap != null) {
                append("^FO${PHOTO_X},${PHOTO_Y}")
                append(bitmapToZplGrf(data.profileBitmap, PHOTO_W, PHOTO_H))
                append("^FS")
            } else {
                // Placeholder box with icon lines
                append("^FO${PHOTO_X},${PHOTO_Y}^GB${PHOTO_W},${PHOTO_H},2^FS")
                append("^FO${PHOTO_X + 50},${PHOTO_Y + 65}^A0N,28,28^FDN/A^FS")
            }

            // ── Text block ─────────────────────────────────────────────────
            append("^FO${TEXT_X},88^A0N,48,42^FD$name^FS")
            if (company.isNotBlank()) {
                append("^FO${TEXT_X},148^A0N,28,24^FD$company^FS")
            }
            append("^FO${TEXT_X},188^A0N,26,22^FD${data.labelVisiting}: $visiting^FS")
            append("^FO${TEXT_X},226^A0N,24,20^FD$dateStr^FS")

            // ── QR code ────────────────────────────────────────────────────
            // ^BQN,model=2,magnification=5  ^FD error_level,data ^FS
            append("^FO${QR_X},${QR_Y}^BQN,2,${QR_MAG}^FDqa,${data.qrValue}^FS")

            // ── Horizontal divider ─────────────────────────────────────────
            append("^FO20,296^GB772,2,2^FS")

            // ── Visitor type pill (outlined box + text) ────────────────────
            append("^FO20,306^GB260,46,2^FS")
            append("^FO34,316^A0N,28,24^FD${data.visitorTypeLabel}^FS")

            // ── Badge validity note ────────────────────────────────────────
            append("^FO295,313^A0N,24,20^FD${data.labelValidFor}^FS")

            // ── Bottom divider + print timestamp ───────────────────────────
            append("^FO20,370^GB772,2,2^FS")
            append("^FO20,382^A0N,20,18^FD${data.labelPrinted}: $printedAt^FS")

            append("^XZ")
        }
    }

    /**
     * Converts an Android [Bitmap] to ZPL ^GFA (ASCII Hex) format.
     *
     * Steps:
     *  1. Scale to [targetW] × [targetH] dots
     *  2. Convert each pixel to perceived luminance
     *  3. Threshold at 128 → 1 bit (1 = black / print, 0 = white / no ink)
     *  4. Pack 8 pixels per byte, MSB first
     *  5. Encode each byte as 2-char uppercase hex
     *
     * Returns the complete ^GFA command string (without trailing ^FS).
     */
    private fun bitmapToZplGrf(src: Bitmap, targetW: Int, targetH: Int): String {
        val scaled      = Bitmap.createScaledBitmap(src, targetW, targetH, true)
        val bytesPerRow = (targetW + 7) / 8
        val totalBytes  = bytesPerRow * targetH
        val hex         = StringBuilder(totalBytes * 2)

        for (y in 0 until targetH) {
            var byteVal  = 0
            var bitCount = 0
            for (x in 0 until targetW) {
                val px   = scaled.getPixel(x, y)
                val r    = (px shr 16) and 0xFF
                val g    = (px shr 8)  and 0xFF
                val b    =  px         and 0xFF
                val luma = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                val bit  = if (luma < 128) 1 else 0    // dark pixel = print ink
                byteVal  = (byteVal shl 1) or bit
                if (++bitCount == 8) {
                    hex.append(byteVal.toString(16).padStart(2, '0').uppercase())
                    byteVal = 0; bitCount = 0
                }
            }
            // Pad last partial byte in row
            if (bitCount > 0) {
                byteVal = byteVal shl (8 - bitCount)
                hex.append(byteVal.toString(16).padStart(2, '0').uppercase())
            }
        }

        if (scaled !== src) scaled.recycle()
        return "^GFA,$totalBytes,$totalBytes,$bytesPerRow,$hex"
    }
}

