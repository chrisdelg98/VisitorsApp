package com.eflglobal.visitorsapp.core.printing

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Printer adapter for Brother label printers (QL-810W, QL-820NWB, etc.)
 * using the Brother Mobile SDK (BrotherPrintLibrary.aar).
 *
 * Key features:
 *  - testConnection only queries status — never prints
 *  - Smart label strategy: handles both genuine DK and third-party rolls
 *  - Detailed diagnostics on every error
 */
object BrotherPrinterAdapter : PrinterAdapter {

    private const val TAG = "BrotherPrint"
    private const val BROTHER_ERROR_NONE  = "ERROR_NONE"
    private const val BROTHER_WRONG_LABEL = "ERROR_WRONG_LABEL"

    /**
     * Label strategies tried in order when printing.
     *
     * For generic / third-party rolls (roll ID 65535) the SDK cannot read the DK
     * chip, so standard label names fail.  We try multiple approaches:
     *
     * 1. CustomPaperInfo API — the official Brother SDK way for custom/generic rolls
     * 2. Dynamic scan of ALL LabelName enum constants matching "62"
     * 3. Manual custom paper fields
     * 4. Auto / named fallbacks
     */
    private sealed class LabelStrategy(val tag: String) {
        /** Let the SDK / printer auto-decide — do not set labelNameIndex. */
        object Auto : LabelStrategy("AUTO")
        /** Set a named label constant (e.g. W62, W62RB). */
        class Named(val name: String) : LabelStrategy(name)
        /** Use CustomPaperInfo API — the official way for generic rolls. */
        class CustomPaperApi(val widthMm: Float) : LabelStrategy("CustomPaperAPI_${widthMm.toInt()}mm")
        /** Set custom paper fields + try CUSTOM/W62 label. */
        class CustomFields(
            val widthMm: Int,
            val lengthMm: Int = 0,
            val feedMm: Int = 0,
            val labelHint: String = "W62"
        ) : LabelStrategy("CustomFields_${widthMm}mm_$labelHint")
        /** Dynamically try ALL LabelName enum constants that match a filter. */
        class DynamicScan(val filter: String) : LabelStrategy("DynScan_$filter")
        /** Try EVERY single LabelName enum constant as a last resort. */
        object BruteForceAll : LabelStrategy("BruteForceAll")
    }

    private val LABEL_STRATEGIES: List<LabelStrategy> = listOf(
        // 1. CustomPaperInfo API — must be called AFTER setPrinterInfo (official for generic rolls)
        LabelStrategy.CustomPaperApi(widthMm = 62f),
        // 2. Custom paper fields with W62 hint
        LabelStrategy.CustomFields(widthMm = 62, lengthMm = 0, feedMm = 0, labelHint = "W62"),
        // 3. Standard W62 (genuine DK-22205 62mm continuous)
        LabelStrategy.Named("W62"),
        // 4. Auto — let the printer decide
        LabelStrategy.Auto,
        // 5. Custom paper fields with explicit length matching ~9cm badge
        LabelStrategy.CustomFields(widthMm = 62, lengthMm = 90, feedMm = 3, labelHint = "W62"),
        // 6. Dynamic scan: try all LabelName constants containing "62"
        LabelStrategy.DynamicScan("62"),
        // 7. W62RB (62mm red/black)
        LabelStrategy.Named("W62RB"),
        // 8. Last resort: try EVERY available LabelName constant
        LabelStrategy.BruteForceAll,
    )

    // ══════════════════════════════════════════════════════════════════════════
    //  PUBLIC API
    // ══════════════════════════════════════════════════════════════════════════

    override suspend fun printBitmap(
        context: Context,
        badge: Bitmap,
        config: PrinterConfig
    ): PrintResult = withContext(Dispatchers.IO) {
        try {
            doPrintWithRetry(badge, config)
        } catch (e: Exception) {
            Log.e(TAG, "printBitmap exception", e)
            PrintResult.Error("Brother: ${e.message ?: "Unknown error"}")
        }
    }

    override suspend fun testConnection(
        context: Context,
        config: PrinterConfig
    ): String? = withContext(Dispatchers.IO) {
        val result = withTimeoutOrNull(15_000L) {
            doTestConnection(config)
        } ?: return@withContext "⏱ Connection timed out (15 s).\nCheck that the printer is ON and reachable at ${config.networkHost ?: "USB"}."
        result
    }

    override suspend fun diagnose(
        context: Context,
        config: PrinterConfig
    ): PrinterDiagnostics = withContext(Dispatchers.IO) {
        val result = withTimeoutOrNull(15_000L) {
            doDiagnose(config)
        } ?: PrinterDiagnostics(
            isConnected = false,
            summary = "Connection timed out (15 s)",
            checks = listOf(
                DiagnosticItem("Connection", "Timed out", DiagStatus.ERROR),
                DiagnosticItem("Host", config.networkHost ?: "USB", DiagStatus.INFO)
            )
        )
        result
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  DIAGNOSE — full structured diagnostics
    // ══════════════════════════════════════════════════════════════════════════

    private fun doDiagnose(config: PrinterConfig): PrinterDiagnostics {
        val checks = mutableListOf<DiagnosticItem>()

        // Check 1: SDK available
        val sdk = loadSdk()
        if (sdk == null) {
            checks.add(DiagnosticItem("SDK", "BrotherPrintLibrary.aar not found", DiagStatus.ERROR))
            return PrinterDiagnostics(false, "SDK not available", checks)
        }
        checks.add(DiagnosticItem("SDK", "Brother SDK loaded", DiagStatus.OK))

        // Check 2: Model configuration
        val modelDisplay = PrinterConfig.BrotherModel.entries
            .firstOrNull { it.name == config.brotherModel }?.displayName ?: config.brotherModel
        checks.add(DiagnosticItem("Model", modelDisplay, DiagStatus.OK))

        // Check 3: Connection type
        checks.add(DiagnosticItem("Connection type",
            if (config.connectionType == PrinterConfig.ConnectionType.USB) "USB" else "Network (TCP/IP)",
            DiagStatus.OK))

        // Check 4: Network config (if applicable)
        if (config.connectionType == PrinterConfig.ConnectionType.NETWORK) {
            val host = config.networkHost?.ifBlank { null }
            if (host == null) {
                checks.add(DiagnosticItem("IP Address", "Not configured", DiagStatus.ERROR))
                return PrinterDiagnostics(false, "No IP address configured", checks)
            }
            checks.add(DiagnosticItem("IP Address", "$host:${config.networkPort}", DiagStatus.OK))
        }

        // Check 5: Connect and query status
        return try {
            val printer     = sdk.newPrinter()
            val printerInfo = sdk.newPrinterInfo()

            configureModel(sdk, printerInfo, config)
            configurePort(sdk, printerInfo, config)
            configureNetwork(sdk, printerInfo, config)
            trySetOrientation(sdk, printerInfo, landscape = true)
            trySetPrintMode(sdk, printerInfo)
            trySetLabelByName(sdk, printerInfo, "W62")
            trySetField(sdk, printerInfo, "isAutoCut", true)
            trySetField(sdk, printerInfo, "numberOfCopies", 1)
            sdk.applyInfo(printer, printerInfo)

            sdk.invoke(printer, "startCommunication")
            val status = try {
                sdk.invoke(printer, "getPrinterStatus")
            } finally {
                runCatching { sdk.invoke(printer, "endCommunication") }
            }

            if (status == null) {
                checks.add(DiagnosticItem("Communication", "No response from printer", DiagStatus.ERROR))
                return PrinterDiagnostics(false, "No response from printer", checks)
            }

            checks.add(DiagnosticItem("Communication", "Printer responded", DiagStatus.OK))

            // Parse all available status fields
            val diag = parseStatus(sdk, status)

            val err = diag["errorCode"] ?: "UNKNOWN"
            val labelId = diag["labelId"]
            val batteryLevel = diag["batteryLevel"]
            val mediaType = diag["mediaType"]

            // Error code check
            when {
                err == BROTHER_ERROR_NONE -> {
                    checks.add(DiagnosticItem("Printer status", "Ready", DiagStatus.OK))
                }
                err == BROTHER_WRONG_LABEL -> {
                    // Connection works — label mismatch is normal for generic rolls
                    checks.add(DiagnosticItem("Printer status", "Ready (label mismatch — normal for generic rolls)", DiagStatus.WARNING))
                }
                err == "ERROR_PAPER_EMPTY" -> {
                    checks.add(DiagnosticItem("Printer status", "Paper empty / not detected", DiagStatus.ERROR))
                }
                err == "ERROR_COVER_OPEN" -> {
                    checks.add(DiagnosticItem("Printer status", "Cover is open", DiagStatus.ERROR))
                }
                err == "ERROR_BUSY" -> {
                    checks.add(DiagnosticItem("Printer status", "Printer busy", DiagStatus.WARNING))
                }
                else -> {
                    checks.add(DiagnosticItem("Printer status", err, DiagStatus.ERROR))
                }
            }

            // Label / media info
            if (!labelId.isNullOrBlank() && labelId != "null") {
                val isGeneric = labelId == "65535" || labelId == "0"
                val labelDesc = if (isGeneric) "Generic / third-party roll (ID: $labelId)" else "DK roll (ID: $labelId)"
                checks.add(DiagnosticItem("Label roll",
                    labelDesc,
                    if (isGeneric) DiagStatus.WARNING else DiagStatus.OK
                ))
            }

            if (!mediaType.isNullOrBlank() && mediaType != "null") {
                checks.add(DiagnosticItem("Media type", mediaType, DiagStatus.INFO))
            }

            if (!batteryLevel.isNullOrBlank() && batteryLevel != "null") {
                val level = batteryLevel.toIntOrNull()
                val (desc, status) = when (level) {
                    4    -> "Full (4/4)" to DiagStatus.OK
                    3    -> "High (3/4)" to DiagStatus.OK
                    2    -> "Medium (2/4)" to DiagStatus.WARNING
                    1    -> "Low (1/4)" to DiagStatus.WARNING
                    0    -> "Critical (0/4) — charge soon" to DiagStatus.ERROR
                    else -> "$batteryLevel" to DiagStatus.INFO
                }
                checks.add(DiagnosticItem("Battery", desc, status))
            }

            // Compatibility check
            val isReachable = err == BROTHER_ERROR_NONE || err == BROTHER_WRONG_LABEL
            val configOk = err == BROTHER_ERROR_NONE
            checks.add(DiagnosticItem("Configuration compatible",
                if (configOk) "Yes" else if (isReachable) "Partial — label roll may need adjustment" else "No",
                if (configOk) DiagStatus.OK else if (isReachable) DiagStatus.WARNING else DiagStatus.ERROR
            ))

            PrinterDiagnostics(
                isConnected = isReachable,
                summary = if (isReachable) "Connected — $modelDisplay" else "Error: $err",
                checks = checks
            )
        } catch (e: Exception) {
            Log.e(TAG, "diagnose exception", e)
            checks.add(DiagnosticItem("Communication", e.message ?: "Connection failed", DiagStatus.ERROR))
            PrinterDiagnostics(false, "Connection failed: ${e.message}", checks)
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  TEST CONNECTION — getPrinterStatus(), NO printing
    // ══════════════════════════════════════════════════════════════════════════

    private fun doTestConnection(config: PrinterConfig): String? {
        return try {
            val sdk = loadSdk() ?: return "BrotherPrintLibrary.aar not found."
            val printer     = sdk.newPrinter()
            val printerInfo = sdk.newPrinterInfo()

            // ── Full configuration (same as printing — prevents SDK from hanging) ──
            configureModel(sdk, printerInfo, config)
            configurePort(sdk, printerInfo, config)
            if (!configureNetwork(sdk, printerInfo, config))
                return "Brother: No IP address configured."

            trySetOrientation(sdk, printerInfo, landscape = true)
            trySetPrintMode(sdk, printerInfo)
            trySetLabelByName(sdk, printerInfo, "W62")   // safe default for status query
            trySetField(sdk, printerInfo, "isAutoCut", true)
            trySetField(sdk, printerInfo, "numberOfCopies", 1)

            sdk.applyInfo(printer, printerInfo)

            // ── Connect & query ──
            Log.i(TAG, "testConnection → startCommunication…")
            sdk.invoke(printer, "startCommunication")

            val status = try {
                sdk.invoke(printer, "getPrinterStatus")
            } finally {
                runCatching { sdk.invoke(printer, "endCommunication") }
            }

            if (status == null)
                return "Brother: null status — printer may not support status queries over this port."

            val diag = parseStatus(sdk, status)
            Log.i(TAG, "testConnection diag: $diag")

            val err = diag["errorCode"] ?: "UNKNOWN"
            when {
                err == BROTHER_ERROR_NONE -> {
                    Log.i(TAG, "✅ Test OK")
                    null  // success
                }
                err == BROTHER_WRONG_LABEL -> {
                    // Connection IS working — label mismatch is expected for generic rolls
                    val lid = diag["labelId"] ?: "?"
                    Log.i(TAG, "✅ Connection OK (label mismatch is normal for generic rolls, id=$lid)")
                    null  // still report success — the printer is reachable
                }
                else -> buildDiagnosticMessage(err, diag, config)
            }

        } catch (cnf: ClassNotFoundException) {
            "BrotherPrintLibrary.aar not found. Add it to app/libs/ and rebuild."
        } catch (e: Exception) {
            Log.e(TAG, "testConnection ex", e)
            "Brother: ${e.message ?: "Unexpected error"}"
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  PRINT — tries raw raster first, then SDK label strategies
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Printing pipeline:
     *
     * 1. **Raw Raster** (network only) — sends raster data directly via TCP
     *    using the Brother QL raster protocol.  This bypasses ALL SDK chip
     *    validation and works with genuine, compatible, and generic rolls.
     *
     * 2. **SDK strategies** — fallback using the Brother Mobile SDK with
     *    multiple label configurations in case raw raster is unavailable
     *    (e.g. USB connection) or fails unexpectedly.
     */
    private fun doPrintWithRetry(bitmap: Bitmap, config: PrinterConfig): PrintResult {

        // ── Attempt 1: Raw raster over TCP (bypasses chip validation) ──
        if (config.connectionType == PrinterConfig.ConnectionType.NETWORK) {
            Log.i(TAG, "═══ Attempt 1: Raw Raster over TCP ═══")
            val rawResult = printRawRaster(bitmap, config)
            if (rawResult is PrintResult.Success) {
                Log.i(TAG, "✅ Raw raster print SUCCESS")
                return rawResult
            }
            Log.w(TAG, "⚠ Raw raster failed: ${(rawResult as? PrintResult.Error)?.message}. Falling back to SDK strategies…")
        }

        // ── Attempt 2: SDK strategies ──
        val sdk = loadSdk()
            ?: return PrintResult.Error("BrotherPrintLibrary.aar not found. Add it to app/libs/.")

        var lastDiag: Map<String, String> = emptyMap()
        val triedTags = mutableListOf<String>("RawRaster")
        val alreadyTriedLabels = mutableSetOf<String>()  // avoid duplicate Named attempts

        // Expand strategies: DynamicScan/BruteForceAll become multiple Named attempts
        val expandedStrategies = mutableListOf<LabelStrategy>()
        for (strategy in LABEL_STRATEGIES) {
            when (strategy) {
                is LabelStrategy.DynamicScan -> {
                    val dynamicLabels = findAllLabelNames(sdk, strategy.filter)
                    for (label in dynamicLabels) {
                        if (label !in alreadyTriedLabels) {
                            expandedStrategies.add(LabelStrategy.Named(label))
                            alreadyTriedLabels.add(label)
                        }
                    }
                }
                is LabelStrategy.BruteForceAll -> {
                    // Try EVERY single LabelName constant as a last resort
                    val allLabels = findAllLabelNames(sdk, "")
                    Log.i(TAG, "BruteForce: ALL available LabelName constants (${allLabels.size}): $allLabels")
                    for (label in allLabels) {
                        if (label !in alreadyTriedLabels) {
                            expandedStrategies.add(LabelStrategy.Named(label))
                            alreadyTriedLabels.add(label)
                        }
                    }
                }
                else -> {
                    expandedStrategies.add(strategy)
                    if (strategy is LabelStrategy.Named) alreadyTriedLabels.add(strategy.name)
                }
            }
        }

        Log.i(TAG, "═══ Print: ${expandedStrategies.size} strategies to try ═══")

        for ((idx, strategy) in expandedStrategies.withIndex()) {
            Log.i(TAG, "── Print attempt ${idx + 1}/${expandedStrategies.size}: strategy=${strategy.tag} ──")
            triedTags.add(strategy.tag)

            val (errorName, diag) = tryPrintOnce(sdk, bitmap, config, strategy)
            lastDiag = diag

            when {
                errorName == BROTHER_ERROR_NONE -> {
                    Log.i(TAG, "✅ Print SUCCESS with strategy=${strategy.tag}")
                    return PrintResult.Success
                }
                errorName == BROTHER_WRONG_LABEL -> {
                    Log.w(TAG, "⚠ WRONG_LABEL with strategy=${strategy.tag} — will retry next strategy")
                    // continue to next strategy
                }
                else -> {
                    // Different error (COMMUNICATION, PAPER_EMPTY, etc.) — no point retrying
                    val msg = buildDiagnosticMessage(errorName, diag, config, triedTags)
                    Log.w(TAG, "❌ Non-retryable error: $msg")
                    return PrintResult.Error(msg)
                }
            }
        }

        // All strategies exhausted with WRONG_LABEL
        val msg = buildDiagnosticMessage(BROTHER_WRONG_LABEL, lastDiag, config, triedTags)
        Log.w(TAG, "❌ All label strategies failed: $msg")
        return PrintResult.Error(msg)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  RAW RASTER — bypasses SDK label chip validation entirely
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Sends raster data directly to the Brother QL printer via TCP socket
     * using the Brother QL raster command protocol.
     *
     * This completely bypasses the Brother Mobile SDK's label chip validation,
     * allowing printing on:
     *  - Genuine DK rolls with worn/unreadable chips (ID 65535)
     *  - Third-party / compatible rolls without DK chips
     *  - Any 62mm continuous roll
     *
     * Protocol: Brother QL-800 series raster commands.
     * Resolution: 300 × 300 DPI.
     * Printable width: 720 dots (≈ 61 mm on a 62 mm roll).
     */
    private fun printRawRaster(bitmap: Bitmap, config: PrinterConfig): PrintResult {
        val host = config.networkHost?.ifBlank { null }
            ?: return PrintResult.Error("No IP address for raw raster printing")
        val port = config.networkPort

        // ── Scale bitmap to 720 pixels wide (printable area for 62mm @ 300 DPI) ──
        val targetW = 720
        val scale = targetW.toFloat() / bitmap.width
        val targetH = (bitmap.height * scale).toInt()
        val scaled = android.graphics.Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
        val bytesPerLine = targetW / 8  // 720 / 8 = 90 bytes per raster line
        val numLines = targetH

        Log.i(TAG, "  Raw raster: ${bitmap.width}×${bitmap.height} → ${targetW}×${targetH}, $numLines lines, $bytesPerLine bytes/line")

        val socket = java.net.Socket()
        try {
            socket.connect(java.net.InetSocketAddress(host, port), 10_000)
            socket.soTimeout = 10_000
            val out = socket.getOutputStream()

            // ── 1. Invalidate — clear any previous incomplete job ──
            out.write(ByteArray(200))

            // ── 2. Initialize (ESC @) ──
            out.write(byteArrayOf(0x1B, 0x40))

            // ── 3. Switch to raster mode (ESC i a 1) ──
            out.write(byteArrayOf(0x1B, 0x69, 0x61, 0x01))

            // ── 4. Print information command (ESC i z ...) ──
            //    Flags: 0x86 = valid_media_type | valid_media_width | recover
            //    Media type: 0x0A = continuous length tape
            //    Media width: 62 mm (0x3E)
            //    Media length: 0 (continuous)
            //    Number of raster lines: [4 bytes LE]
            //    Page: 0, Padding: 0
            val printInfo = byteArrayOf(
                0x1B, 0x69, 0x7A,
                0x86.toByte(),              // flags
                0x0A,                       // media type: continuous
                62,                         // media width mm
                0,                          // media length (0 = continuous)
                (numLines and 0xFF).toByte(),
                ((numLines shr 8) and 0xFF).toByte(),
                ((numLines shr 16) and 0xFF).toByte(),
                ((numLines shr 24) and 0xFF).toByte(),
                0x00,                       // starting page
                0x00                        // padding
            )
            out.write(printInfo)

            // ── 5. Auto cut on (ESC i M 0x40) ──
            out.write(byteArrayOf(0x1B, 0x69, 0x4D, 0x40))

            // ── 6. Cut each 1 label (ESC i A 1) ──
            out.write(byteArrayOf(0x1B, 0x69, 0x41, 0x01))

            // ── 7. Margin amount = 0 (ESC i d 0 0) ──
            out.write(byteArrayOf(0x1B, 0x69, 0x64, 0x00, 0x00))

            // ── 8. Compression mode off (M 0) ──
            out.write(byteArrayOf(0x4D, 0x00))

            // ── 9. Send raster data, line by line ──
            val lineHeader = byteArrayOf(
                0x67,                            // 'g' = raster graphics transfer
                0x00,                            // compression mode 0 (none)
                bytesPerLine.toByte()            // bytes in this line (90)
            )
            val lineData = ByteArray(bytesPerLine)

            for (y in 0 until numLines) {
                // Clear line buffer
                lineData.fill(0)

                // Convert pixel row to 1-bit monochrome (MSB first, dark = ink)
                for (x in 0 until targetW) {
                    val px = scaled.getPixel(x, y)
                    val r = (px shr 16) and 0xFF
                    val g = (px shr 8) and 0xFF
                    val b = px and 0xFF
                    val luma = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                    if (luma < 128) {  // dark pixel → print (black)
                        lineData[x / 8] = (lineData[x / 8].toInt() or (0x80 shr (x % 8))).toByte()
                    }
                }

                out.write(lineHeader)
                out.write(lineData)
            }

            // ── 10. Print with feeding (SUB = 0x1A) ──
            out.write(byteArrayOf(0x1A))
            out.flush()

            Log.i(TAG, "  Raw raster: all $numLines lines sent, waiting for printer…")

            // Give the printer a moment to process before closing
            Thread.sleep(800)

            Log.i(TAG, "  ✅ Raw raster print complete")
            return PrintResult.Success

        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "  Raw raster: connection timed out", e)
            return PrintResult.Error("Raw raster: connection timed out ($host:$port)")
        } catch (e: java.net.ConnectException) {
            Log.e(TAG, "  Raw raster: connection refused", e)
            return PrintResult.Error("Raw raster: connection refused ($host:$port)")
        } catch (e: Exception) {
            Log.e(TAG, "  Raw raster: failed", e)
            return PrintResult.Error("Raw raster: ${e.message}")
        } finally {
            runCatching { socket.close() }
            if (scaled !== bitmap) scaled.recycle()
        }
    }

    /**
     * Single print attempt with a specific label strategy.
     * Returns (errorCodeName, diagnosticsMap).
     *
     * IMPORTANT: The Brother SDK requires this call order:
     *   1. setPrinterInfo(printerInfo)
     *   2. setCustomPaperInfo(customPaperInfo)   ← AFTER setPrinterInfo!
     *   3. startCommunication()
     *   4. printImage(bitmap)
     *   5. endCommunication()
     */
    private fun tryPrintOnce(
        sdk: SdkClasses,
        bitmap: Bitmap,
        config: PrinterConfig,
        strategy: LabelStrategy
    ): Pair<String, Map<String, String>> {
        val printer     = sdk.newPrinter()
        val printerInfo = sdk.newPrinterInfo()

        // ── Step 1: Build PrinterInfo ──
        configureModel(sdk, printerInfo, config)
        configurePort(sdk, printerInfo, config)
        if (!configureNetwork(sdk, printerInfo, config))
            return "ERROR_NO_IP" to mapOf("errorCode" to "ERROR_NO_IP")

        trySetOrientation(sdk, printerInfo, landscape = true)
        trySetPrintMode(sdk, printerInfo)

        // Deferred: CustomPaperApi sets customPaper AFTER applyInfo (see step 2b)
        var deferCustomPaperApi = false
        var deferWidthMm = 0f

        // ── Apply label strategy to printerInfo ──
        when (strategy) {
            is LabelStrategy.Auto -> {
                Log.d(TAG, "  strategy=AUTO — no label set")
            }

            is LabelStrategy.Named -> {
                val set = trySetLabelByName(sdk, printerInfo, strategy.name)
                Log.d(TAG, "  strategy=Named(${strategy.name}) set=$set")
            }

            is LabelStrategy.CustomPaperApi -> {
                // We'll call setCustomPaperInfo AFTER setPrinterInfo (step 2b)
                // For now, try setting CUSTOM label or W62 as a hint
                val customSet = trySetLabelByName(sdk, printerInfo, "CUSTOM")
                if (!customSet) trySetLabelByName(sdk, printerInfo, "W62")
                deferCustomPaperApi = true
                deferWidthMm = strategy.widthMm
                Log.d(TAG, "  strategy=CustomPaperApi(${strategy.widthMm}mm) — deferred to after setPrinterInfo")
            }

            is LabelStrategy.CustomFields -> {
                setCustomPaperFields(sdk, printerInfo, strategy.widthMm, strategy.lengthMm, strategy.feedMm)
                val set = trySetLabelByName(sdk, printerInfo, strategy.labelHint)
                Log.d(TAG, "  strategy=CustomFields(${strategy.widthMm}mm, hint=${strategy.labelHint}) labelSet=$set")
            }

            is LabelStrategy.DynamicScan -> {
                Log.d(TAG, "  strategy=DynamicScan should not reach tryPrintOnce")
            }

            is LabelStrategy.BruteForceAll -> {
                Log.d(TAG, "  strategy=BruteForceAll should not reach tryPrintOnce")
            }
        }

        // Common fields
        trySetField(sdk, printerInfo, "isAutoCut", true)
        trySetField(sdk, printerInfo, "isCutAtEnd", true)
        trySetField(sdk, printerInfo, "isHalfCut", false)
        trySetField(sdk, printerInfo, "numberOfCopies", 1)
        // Bypass label validation if possible
        trySetField(sdk, printerInfo, "isCheckPrintEnd", false)
        trySetField(sdk, printerInfo, "skipStatusCheck", true)

        // ── Step 2a: Apply PrinterInfo ──
        sdk.applyInfo(printer, printerInfo)

        // ── Step 2b: Apply CustomPaperInfo AFTER setPrinterInfo ──
        if (deferCustomPaperApi) {
            val ok = tryConfigureCustomPaperApi(sdk, printer, printerInfo, config, deferWidthMm)
            Log.d(TAG, "  CustomPaperApi (post-setPrinterInfo): ok=$ok")
        }

        // ── Step 3: Communicate and print ──
        Log.d(TAG, "  bitmap ${bitmap.width}×${bitmap.height} → startCommunication…")
        sdk.invoke(printer, "startCommunication")

        val status = try {
            sdk.printerClass.getMethod("printImage", Bitmap::class.java)
                .invoke(printer, bitmap)
        } finally {
            runCatching { sdk.invoke(printer, "endCommunication") }
        }

        if (status == null) return "ERROR_NULL_STATUS" to mapOf("errorCode" to "ERROR_NULL_STATUS")

        val diag = parseStatus(sdk, status)
        val err  = diag["errorCode"] ?: "UNKNOWN"
        Log.d(TAG, "  result: $err | diag=$diag")
        return err to diag
    }

    // ── CustomPaperInfo API (official Brother SDK way for custom rolls) ─────

    /**
     * Tries to configure a custom roll via [CustomPaperInfo.newCustomRollPaper].
     * This is the officially supported way to handle generic / third-party rolls.
     * Returns true if successful, false if the API is not available in this SDK.
     */
    private fun tryConfigureCustomPaperApi(
        sdk: SdkClasses,
        printer: Any,
        info: Any,
        config: PrinterConfig,
        widthMm: Float
    ): Boolean {
        return try {
            val customPaperClass = Class.forName("com.brother.ptouch.sdk.CustomPaperInfo")

            // Resolve the Unit enum (could be inner class or standalone)
            val unitEnum = resolveCustomPaperUnit(customPaperClass)
            if (unitEnum == null) {
                Log.d(TAG, "  CustomPaperInfo.Unit.Mm not found")
                return false
            }

            // Resolve the Model enum value
            val modelEnum = resolveEnum(sdk.infoClass, "Model",
                PrinterConfig.BrotherModel.entries
                    .firstOrNull { it.name == config.brotherModel }?.sdkName ?: config.brotherModel)
            if (modelEnum == null) {
                Log.d(TAG, "  Model enum not resolved")
                return false
            }

            // Try newCustomRollPaper(Model, Unit, float)
            val rollPaperMethod = findNewCustomRollPaper(customPaperClass, modelEnum, unitEnum)
            if (rollPaperMethod != null) {
                val customPaperInfo = rollPaperMethod.invoke(null, modelEnum, unitEnum, widthMm)
                if (customPaperInfo != null) {
                    // Call printer.setCustomPaperInfo(customPaperInfo)
                    val setMethod = sdk.printerClass.methods.firstOrNull {
                        it.name == "setCustomPaperInfo" && it.parameterCount == 1
                    }
                    if (setMethod != null) {
                        setMethod.invoke(printer, customPaperInfo)
                        // Set label to CUSTOM
                        trySetLabelByName(sdk, info, "CUSTOM")
                        Log.d(TAG, "  ✅ CustomPaperInfo.newCustomRollPaper OK (${widthMm}mm)")
                        return true
                    }
                }
            }

            // Alternative: try newCustomRollPaper with different signatures
            // Some SDK versions: newCustomRollPaper(Model, Unit, int)
            val rollPaperInt = customPaperClass.methods.firstOrNull {
                it.name == "newCustomRollPaper" && it.parameterCount == 3 &&
                    it.parameterTypes[2] == Int::class.javaPrimitiveType
            }
            if (rollPaperInt != null) {
                val customPaperInfo = rollPaperInt.invoke(null, modelEnum, unitEnum, widthMm.toInt())
                if (customPaperInfo != null) {
                    sdk.printerClass.methods.firstOrNull { it.name == "setCustomPaperInfo" }
                        ?.invoke(printer, customPaperInfo)
                    trySetLabelByName(sdk, info, "CUSTOM")
                    Log.d(TAG, "  ✅ CustomPaperInfo.newCustomRollPaper (int) OK")
                    return true
                }
            }

            Log.d(TAG, "  CustomPaperInfo methods not matched")
            false
        } catch (e: ClassNotFoundException) {
            Log.d(TAG, "  CustomPaperInfo class not found: ${e.message}")
            false
        } catch (e: Exception) {
            Log.d(TAG, "  CustomPaperInfo failed: ${e.message}")
            false
        }
    }

    /**
     * Resolves the Unit.Mm enum constant from CustomPaperInfo's inner classes
     * or from a standalone com.brother.ptouch.sdk.Unit class.
     */
    private fun resolveCustomPaperUnit(customPaperClass: Class<*>): Any? {
        // Try inner class first: CustomPaperInfo$Unit.Mm
        for (inner in customPaperClass.declaredClasses) {
            if (inner.simpleName == "Unit" && inner.isEnum) {
                return try { inner.getField("Mm").get(null) } catch (_: Exception) {
                    try { inner.getField("mm").get(null) } catch (_: Exception) { null }
                }
            }
        }
        // Try standalone: com.brother.ptouch.sdk.Unit
        return try {
            val unitClass = Class.forName("com.brother.ptouch.sdk.Unit")
            unitClass.getField("Mm").get(null)
        } catch (_: Exception) { null }
    }

    /**
     * Finds the newCustomRollPaper static method with float width parameter.
     */
    private fun findNewCustomRollPaper(
        customPaperClass: Class<*>,
        modelSample: Any,
        unitSample: Any
    ): java.lang.reflect.Method? {
        return customPaperClass.methods.firstOrNull {
            it.name == "newCustomRollPaper" && it.parameterCount == 3 &&
                it.parameterTypes[0].isAssignableFrom(modelSample.javaClass) &&
                it.parameterTypes[1].isAssignableFrom(unitSample.javaClass) &&
                (it.parameterTypes[2] == Float::class.javaPrimitiveType ||
                 it.parameterTypes[2] == Float::class.javaObjectType)
        }
    }

    // ── Custom paper fields (fallback when CustomPaperInfo API isn't available) ─

    /**
     * Sets multiple custom paper dimension fields.
     * Different SDK versions use different field names — we try all known ones.
     */
    private fun setCustomPaperFields(sdk: SdkClasses, info: Any, widthMm: Int, lengthMm: Int, feedMm: Int) {
        // Try setting CUSTOM label first
        val customSet = trySetLabelByName(sdk, info, "CUSTOM")
        Log.d(TAG, "  LabelName.CUSTOM available: $customSet")

        // Width
        trySetField(sdk, info, "customPaperWidth", widthMm)
        trySetField(sdk, info, "customPaper", widthMm)

        // Length (0 = continuous)
        trySetField(sdk, info, "customPaperLength", lengthMm)
        trySetField(sdk, info, "customLength", lengthMm)

        // Feed
        trySetField(sdk, info, "customFeed", feedMm)
        trySetField(sdk, info, "customMargin", feedMm)

        // Paper type hints (some SDK versions)
        trySetField(sdk, info, "isLabelEndCut", true)
        trySetField(sdk, info, "isCutMark", false)
        trySetField(sdk, info, "isSpecialTape", false)

        Log.d(TAG, "  custom fields set: ${widthMm}×${lengthMm}mm feed=$feedMm")
    }

    // ── Dynamic label name scan ────────────────────────────────────────────

    /**
     * Returns available LabelName enum constants whose name contains [filter].
     * If [filter] is empty, returns ALL constants.
     * This allows us to try labels we didn't hardcode in LABEL_STRATEGIES.
     */
    private fun findAllLabelNames(sdk: SdkClasses, filter: String): List<String> {
        return try {
            val labelNameClass = sdk.infoClass.declaredClasses
                .firstOrNull { it.simpleName == "LabelName" && it.isEnum }
                ?: return emptyList()

            @Suppress("UNCHECKED_CAST")
            val constants = (labelNameClass as Class<out Enum<*>>).enumConstants ?: return emptyList()

            val filtered = if (filter.isBlank()) {
                constants.map { it.name }
            } else {
                constants.map { it.name }.filter { it.contains(filter, ignoreCase = true) }
            }
            Log.d(TAG, "  LabelName constants ${if (filter.isBlank()) "(ALL)" else "matching '$filter'"}: ${filtered.size} found → $filtered")
            filtered
        } catch (e: Exception) {
            Log.d(TAG, "  Failed to enumerate LabelName: ${e.message}")
            emptyList()
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  SDK class wrapper (avoids repeating Class.forName everywhere)
    // ══════════════════════════════════════════════════════════════════════════

    private class SdkClasses(
        val printerClass: Class<*>,
        val infoClass: Class<*>,
        val statusClass: Class<*>
    ) {
        fun newPrinter(): Any  = printerClass.getDeclaredConstructor().newInstance()
        fun newPrinterInfo(): Any = infoClass.getDeclaredConstructor().newInstance()

        fun applyInfo(printer: Any, info: Any) {
            printerClass.getMethod("setPrinterInfo", infoClass).invoke(printer, info)
        }

        fun invoke(target: Any, method: String): Any? =
            target.javaClass.getMethod(method).invoke(target)
    }

    private fun loadSdk(): SdkClasses? = try {
        SdkClasses(
            printerClass = Class.forName("com.brother.ptouch.sdk.Printer"),
            infoClass    = Class.forName("com.brother.ptouch.sdk.PrinterInfo"),
            statusClass  = Class.forName("com.brother.ptouch.sdk.PrinterStatus")
        )
    } catch (_: ClassNotFoundException) { null }

    // ══════════════════════════════════════════════════════════════════════════
    //  DIAGNOSTICS
    // ══════════════════════════════════════════════════════════════════════════

    private fun parseStatus(sdk: SdkClasses, status: Any): Map<String, String> {
        val m = mutableMapOf<String, String>()
        fun read(field: String) = runCatching {
            val v = sdk.statusClass.getField(field).get(status)
            val s = if (v != null && v.javaClass.isEnum)
                v.javaClass.getMethod("name").invoke(v)?.toString() ?: v.toString()
            else v?.toString() ?: "null"
            m[field] = s
        }
        read("errorCode")
        read("labelId")
        read("batteryLevel")
        for (f in listOf("labelWidth", "labelLength", "mediaType", "headColor"))
            read(f)
        return m
    }

    private fun buildDiagnosticMessage(
        errorName: String,
        diag: Map<String, String>,
        config: PrinterConfig,
        triedStrategies: List<String> = emptyList()
    ): String = buildString {
        append("Brother: $errorName")
        when (errorName) {
            BROTHER_WRONG_LABEL -> {
                append("\n\n⚠ Label mismatch — all strategies tried.")
                val lid = diag["labelId"]
                if (!lid.isNullOrBlank() && lid != "null")
                    append("\n• Roll ID detected: $lid")
                if (lid == "65535" || lid == "0")
                    append("\n  (generic / third-party roll — no DK chip)")
                if (triedStrategies.isNotEmpty())
                    append("\n• Strategies tried: ${triedStrategies.joinToString(", ")}")
                append("\n\n→ Verify the roll is 62 mm continuous and properly loaded.")
                append("\n→ Try reopening and closing the printer cover.")
                append("\n→ If using a third-party roll, ensure it is 62 mm width.")
            }
            "ERROR_COMMUNICATION" -> {
                append("\n\n⚠ Cannot reach the printer.")
                if (config.connectionType == PrinterConfig.ConnectionType.NETWORK)
                    append("\n• ${config.networkHost}:${config.networkPort}")
                append("\n\nCheck power, WiFi, and firewall.")
            }
            "ERROR_PAPER_EMPTY"  -> append("\n\nThe label roll is empty or not detected.")
            "ERROR_COVER_OPEN"   -> append("\n\nThe printer cover is open.")
            "ERROR_BUSY"         -> append("\n\nPrinter busy — wait and retry.")
            "ERROR_NO_IP"        -> append("\n\nNo IP address configured.")
            else -> {
                if (diag.isNotEmpty()) {
                    append("\n\nDiagnostics:")
                    diag.forEach { (k, v) -> append("\n  $k = $v") }
                }
            }
        }

        val model = PrinterConfig.BrotherModel.entries
            .firstOrNull { it.name == config.brotherModel }?.displayName ?: config.brotherModel
        append("\n\nModel: $model | ${config.connectionType.name}")
        if (config.connectionType == PrinterConfig.ConnectionType.NETWORK)
            append(" | ${config.networkHost}:${config.networkPort}")
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  CONFIGURATION HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    private fun configureModel(sdk: SdkClasses, info: Any, config: PrinterConfig) {
        val en = resolveEnum(sdk.infoClass, "Model",
            PrinterConfig.BrotherModel.entries
                .firstOrNull { it.name == config.brotherModel }?.sdkName ?: config.brotherModel)
        if (en != null) sdk.infoClass.getField("printerModel").set(info, en)
    }

    private fun configurePort(sdk: SdkClasses, info: Any, config: PrinterConfig) {
        val name = if (config.connectionType == PrinterConfig.ConnectionType.USB) "USB" else "NET"
        val en = resolveEnum(sdk.infoClass, "Port", name)
        if (en != null) sdk.infoClass.getField("port").set(info, en)
    }

    private fun configureNetwork(sdk: SdkClasses, info: Any, config: PrinterConfig): Boolean {
        if (config.connectionType != PrinterConfig.ConnectionType.NETWORK) return true
        val host = config.networkHost?.ifBlank { null } ?: return false
        sdk.infoClass.getField("ipAddress").set(info, host)
        return true
    }

    private fun trySetOrientation(sdk: SdkClasses, info: Any, landscape: Boolean) {
        val name = if (landscape) "LANDSCAPE" else "PORTRAIT"
        val en = resolveEnum(sdk.infoClass, "Orientation", name) ?: return
        runCatching { sdk.infoClass.getField("orientation").set(info, en) }
    }

    private fun trySetPrintMode(sdk: SdkClasses, info: Any) {
        val en = resolveEnum(sdk.infoClass, "PrintMode", "FIT_PAGE")
            ?: resolveEnum(sdk.infoClass, "PrintMode", "FIT_TO_PAGE")
            ?: return
        runCatching { sdk.infoClass.getField("printMode").set(info, en) }
    }

    /**
     * Sets labelNameIndex to the given [name] (e.g. "W62").
     * Returns true if the constant was found and set.
     */
    private fun trySetLabelByName(sdk: SdkClasses, info: Any, name: String): Boolean {
        val en = resolveEnum(sdk.infoClass, "LabelName", name) ?: return false
        runCatching { sdk.infoClass.getField("labelNameIndex").set(info, en) }
        return true
    }

    private fun trySetField(sdk: SdkClasses, info: Any, field: String, value: Any) {
        runCatching { sdk.infoClass.getField(field).set(info, value) }
    }

    // ── Reflection: resolve an inner enum constant by class-simpleName + constant name ──

    private fun resolveEnum(outer: Class<*>, enumName: String, constant: String): Any? =
        runCatching {
            val cls = outer.declaredClasses.firstOrNull { it.simpleName == enumName }
                ?: return@runCatching null
            cls.getField(constant).get(null)
        }.getOrNull()
}
