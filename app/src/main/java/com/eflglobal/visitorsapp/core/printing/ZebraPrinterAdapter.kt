package com.eflglobal.visitorsapp.core.printing

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Printer adapter for Zebra printers (ZT230 and compatible ZPL devices).
 *
 * Approach: converts the universal badge [Bitmap] to ZPL ^GFA (hex bitmap) and
 * wraps it in a label preamble before sending via [ZebraPrinterManager].
 *
 * Label size assumed: 812 × 609 dots (4 in × 3 in @ 203 DPI).
 */
object ZebraPrinterAdapter : PrinterAdapter {

    override suspend fun printBitmap(
        context: Context,
        badge: Bitmap,
        config: PrinterConfig
    ): PrintResult = withContext(Dispatchers.IO) {
        try {
            val zpl = buildZpl(badge)
            ZebraPrinterManager.printZpl(context, zpl, config).toUnified()
        } catch (e: Exception) {
            PrintResult.Error("Zebra: ${e.message ?: "Unknown error"}")
        }
    }

    override suspend fun testConnection(
        context: Context,
        config: PrinterConfig
    ): String? = withContext(Dispatchers.IO) {
        val result = withTimeoutOrNull(8_000L) {
            ZebraPrinterManager.printZpl(context, "^XA^XZ", config).toUnified()
        } ?: return@withContext "Connection timed out (8 s)"

        return@withContext when (result) {
            is PrintResult.Success             -> null
            is PrintResult.PermissionRequested -> "USB permission requested — grant it and try again"
            is PrintResult.Error               -> result.message
        }
    }

    // ── ZPL builder ────────────────────────────────────────────────────────

    /**
     * Builds a ZPL II string that prints [bitmap] as a full-label graphic.
     *
     * ^PW / ^LL set the physical label dimensions.
     * ^FO 0,0 positions the graphic at the top-left corner.
     * ^GFA encodes the bitmap in ASCII hex, 1 bit per pixel (dark=ink).
     */
    private fun buildZpl(bitmap: Bitmap): String {
        val w   = BadgeBitmapRenderer.BADGE_W
        val h   = BadgeBitmapRenderer.BADGE_H
        val grf = bitmapToZplGrf(bitmap, w, h)

        return buildString {
            append("^XA")
            append("^PW$w")
            append("^LL$h")
            append("^CI28")        // UTF-8
            append("^FO0,0")
            append(grf)
            append("^FS")
            append("^XZ")
        }
    }

    // ── Bitmap → ZPL hex encoder ──────────────────────────────────────────

    /**
     * Converts [src] to the ZPL ^GFA command string.
     *
     * Algorithm:
     *  1. Scale bitmap to [targetW] × [targetH].
     *  2. Convert each pixel to perceived luminance (BT.601).
     *  3. Threshold at 128 → 1-bit (1 = black ink, 0 = white / no ink).
     *  4. Pack 8 pixels per byte, MSB first.
     *  5. Encode each byte as 2-char uppercase hex.
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
                val bit  = if (luma < 128) 1 else 0   // dark = print ink
                byteVal  = (byteVal shl 1) or bit
                if (++bitCount == 8) {
                    hex.append(byteVal.toString(16).padStart(2, '0').uppercase())
                    byteVal = 0; bitCount = 0
                }
            }
            // Flush partial byte at end of row
            if (bitCount > 0) {
                byteVal = byteVal shl (8 - bitCount)
                hex.append(byteVal.toString(16).padStart(2, '0').uppercase())
            }
        }

        if (scaled !== src) scaled.recycle()
        return "^GFA,$totalBytes,$totalBytes,$bytesPerRow,$hex"
    }

    // ── Result mapping ─────────────────────────────────────────────────────

    private fun ZebraPrinterManager.PrintResult.toUnified(): PrintResult = when (this) {
        is ZebraPrinterManager.PrintResult.Success             -> PrintResult.Success
        is ZebraPrinterManager.PrintResult.PermissionRequested -> PrintResult.PermissionRequested
        is ZebraPrinterManager.PrintResult.Error               -> PrintResult.Error(message)
    }
}

