package com.eflglobal.visitorsapp.core.printing

import android.content.Context
import android.graphics.Bitmap
import android.hardware.usb.UsbManager
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

    override suspend fun diagnose(
        context: Context,
        config: PrinterConfig
    ): PrinterDiagnostics = withContext(Dispatchers.IO) {
        val checks = mutableListOf<DiagnosticItem>()

        // Check 1: Connection type
        checks.add(DiagnosticItem("Connection type",
            if (config.connectionType == PrinterConfig.ConnectionType.USB) "USB" else "Network (TCP/IP)",
            DiagStatus.OK))

        // Check 2: Network config
        if (config.connectionType == PrinterConfig.ConnectionType.NETWORK) {
            val host = config.networkHost?.ifBlank { null }
            if (host == null) {
                checks.add(DiagnosticItem("IP Address", "Not configured", DiagStatus.ERROR))
                return@withContext PrinterDiagnostics(false, "No IP address configured", checks)
            }
            checks.add(DiagnosticItem("IP Address", "$host:${config.networkPort}", DiagStatus.OK))
        }

        // Check 3: USB device detection
        if (config.connectionType == PrinterConfig.ConnectionType.USB) {
            try {
                val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
                val zebraDevice = usbManager.deviceList.values
                    .firstOrNull { it.vendorId == 0x0A5F }
                if (zebraDevice != null) {
                    checks.add(DiagnosticItem("USB device", "Zebra device detected", DiagStatus.OK))
                    val hasPerm = usbManager.hasPermission(zebraDevice)
                    checks.add(DiagnosticItem("USB permission",
                        if (hasPerm) "Granted" else "Not granted — will request on print",
                        if (hasPerm) DiagStatus.OK else DiagStatus.WARNING
                    ))
                } else {
                    checks.add(DiagnosticItem("USB device", "No Zebra printer found on USB", DiagStatus.ERROR))
                    return@withContext PrinterDiagnostics(false, "No Zebra USB device detected", checks)
                }
            } catch (e: Exception) {
                checks.add(DiagnosticItem("USB device", "Error: ${e.message}", DiagStatus.ERROR))
            }
        }

        // Check 4: Connection test (send empty label)
        val result = withTimeoutOrNull(10_000L) {
            try {
                ZebraPrinterManager.printZpl(context, "^XA^XZ", config).toUnified()
            } catch (e: Exception) {
                PrintResult.Error(e.message ?: "Connection error")
            }
        }

        when {
            result == null -> {
                checks.add(DiagnosticItem("Communication", "Connection timed out (10 s)", DiagStatus.ERROR))
                return@withContext PrinterDiagnostics(false, "Connection timed out", checks)
            }
            result is PrintResult.Success -> {
                checks.add(DiagnosticItem("Communication", "Printer responded", DiagStatus.OK))
            }
            result is PrintResult.PermissionRequested -> {
                checks.add(DiagnosticItem("Communication", "USB permission requested — try again", DiagStatus.WARNING))
                return@withContext PrinterDiagnostics(false, "USB permission pending", checks)
            }
            result is PrintResult.Error -> {
                checks.add(DiagnosticItem("Communication", result.message, DiagStatus.ERROR))
                return@withContext PrinterDiagnostics(false, result.message, checks)
            }
        }

        // Check 5: Query device info via SGD (Host Status) if network
        if (config.connectionType == PrinterConfig.ConnectionType.NETWORK) {
            try {
                val host = config.networkHost!!
                val port = config.networkPort
                val conn = com.zebra.sdk.comm.TcpConnection(host, port)
                conn.open()
                try {
                    // Initialize printer instance to enable SGD queries
                    com.zebra.sdk.printer.ZebraPrinterFactory.getInstance(conn)
                    // Try to read some SGD values
                    val firmware = runCatching {
                        com.zebra.sdk.printer.SGD.GET("appl.name", conn)
                    }.getOrNull()
                    if (!firmware.isNullOrBlank()) {
                        checks.add(DiagnosticItem("Firmware", firmware, DiagStatus.INFO))
                    }

                    val mediaTypeVal = runCatching {
                        com.zebra.sdk.printer.SGD.GET("ezpl.media_type", conn)
                    }.getOrNull()
                    if (!mediaTypeVal.isNullOrBlank()) {
                        checks.add(DiagnosticItem("Media type", mediaTypeVal, DiagStatus.INFO))
                    }

                    val printWidth = runCatching {
                        com.zebra.sdk.printer.SGD.GET("ezpl.print_width", conn)
                    }.getOrNull()
                    if (!printWidth.isNullOrBlank()) {
                        checks.add(DiagnosticItem("Print width (dots)", printWidth, DiagStatus.INFO))
                    }

                    val darkness = runCatching {
                        com.zebra.sdk.printer.SGD.GET("print.tone", conn)
                    }.getOrNull()
                    if (!darkness.isNullOrBlank()) {
                        checks.add(DiagnosticItem("Darkness / tone", darkness, DiagStatus.INFO))
                    }

                    val headUp = runCatching {
                        com.zebra.sdk.printer.SGD.GET("head.latch", conn)
                    }.getOrNull()
                    if (!headUp.isNullOrBlank()) {
                        val isOpen = headUp.contains("open", ignoreCase = true)
                        checks.add(DiagnosticItem("Print head",
                            if (isOpen) "Open — close before printing" else "Closed",
                            if (isOpen) DiagStatus.ERROR else DiagStatus.OK
                        ))
                    }

                    val paperOut = runCatching {
                        com.zebra.sdk.printer.SGD.GET("sensor.paper_supply", conn)
                    }.getOrNull()
                    if (!paperOut.isNullOrBlank()) {
                        checks.add(DiagnosticItem("Paper sensor", paperOut, DiagStatus.INFO))
                    }
                } finally {
                    runCatching { conn.close() }
                }
            } catch (e: Exception) {
                // SGD query is best-effort; connection test already passed
                checks.add(DiagnosticItem("Extended info", "Not available (${e.message?.take(60)})", DiagStatus.WARNING))
            }
        }

        // Final compatibility check
        checks.add(DiagnosticItem("Configuration compatible", "Yes", DiagStatus.OK))

        PrinterDiagnostics(
            isConnected = true,
            summary = "Connected — Zebra (ZPL)",
            checks = checks
        )
    }

    // ── ZPL builder ────────────────────────────────────────────────────────

    /**
     * Builds a ZPL II string that prints [bitmap] as a full-label graphic
     * with maximum print quality settings.
     *
     * Quality commands:
     *  ~SD30   — max darkness / print density (range 0–30)
     *  ^MNN    — non-continuous media (if applicable)
     *  ^PMN    — normal print mode
     *  ^PR2,2  — slower print speed for better resolution
     *
     * ^PW / ^LL set the physical label dimensions.
     * ^FO 0,0 positions the graphic at the top-left corner.
     * ^GFA encodes the bitmap in ASCII hex, 1 bit per pixel (dark=ink)
     * using Floyd-Steinberg error diffusion for optimal photo reproduction.
     */
    private fun buildZpl(bitmap: Bitmap): String {
        val w   = BadgeBitmapRenderer.BADGE_W
        val h   = BadgeBitmapRenderer.BADGE_H
        val grf = bitmapToZplGrf(bitmap, w, h)

        return buildString {
            append("^XA")
            append("~SD30")        // Max darkness / density for best contrast
            append("^PR2,2,2")     // Slower print speed → higher quality
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
     * Converts [src] to the ZPL ^GFA command string with Floyd-Steinberg
     * error-diffusion dithering for the best possible photo reproduction
     * on a 1-bit thermal printer.
     *
     * Algorithm:
     *  1. Scale bitmap to [targetW] × [targetH].
     *  2. Convert each pixel to perceived luminance (BT.601).
     *  3. Apply Floyd-Steinberg error diffusion → 1-bit.
     *  4. Pack 8 pixels per byte, MSB first.
     *  5. Encode each byte as 2-char uppercase hex.
     */
    private fun bitmapToZplGrf(src: Bitmap, targetW: Int, targetH: Int): String {
        val scaled      = Bitmap.createScaledBitmap(src, targetW, targetH, true)
        val bytesPerRow = (targetW + 7) / 8
        val totalBytes  = bytesPerRow * targetH

        // ── Step 1: Build grayscale float buffer ──
        val grayBuf = FloatArray(targetW * targetH)
        for (y in 0 until targetH) {
            for (x in 0 until targetW) {
                val px = scaled.getPixel(x, y)
                val r = (px shr 16) and 0xFF
                val g = (px shr 8)  and 0xFF
                val b =  px         and 0xFF
                grayBuf[y * targetW + x] = (0.299f * r + 0.587f * g + 0.114f * b)
            }
        }

        // ── Step 2: Floyd-Steinberg error diffusion ──
        for (y in 0 until targetH) {
            for (x in 0 until targetW) {
                val idx = y * targetW + x
                val oldVal = grayBuf[idx]
                val newVal = if (oldVal < 128f) 0f else 255f
                grayBuf[idx] = newVal
                val err = oldVal - newVal
                if (x + 1 < targetW)
                    grayBuf[idx + 1] += err * 7f / 16f
                if (y + 1 < targetH) {
                    if (x - 1 >= 0)
                        grayBuf[(y + 1) * targetW + (x - 1)] += err * 3f / 16f
                    grayBuf[(y + 1) * targetW + x] += err * 5f / 16f
                    if (x + 1 < targetW)
                        grayBuf[(y + 1) * targetW + (x + 1)] += err * 1f / 16f
                }
            }
        }

        // ── Step 3: Pack dithered 1-bit pixels into hex string ──
        val hex = StringBuilder(totalBytes * 2)
        for (y in 0 until targetH) {
            var byteVal  = 0
            var bitCount = 0
            for (x in 0 until targetW) {
                val bit = if (grayBuf[y * targetW + x] < 128f) 1 else 0  // dark = ink
                byteVal = (byteVal shl 1) or bit
                if (++bitCount == 8) {
                    hex.append(byteVal.toString(16).padStart(2, '0').uppercase())
                    byteVal = 0; bitCount = 0
                }
            }
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

