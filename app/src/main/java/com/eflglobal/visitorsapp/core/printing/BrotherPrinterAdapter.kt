package com.eflglobal.visitorsapp.core.printing

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Printer adapter for Brother label printers using the Brother Mobile SDK
 * (BrotherPrintLibrary.aar — com.brother.ptouch.sdk).
 *
 * Supported transports:
 *  - WiFi / LAN  : PrinterInfo.Port.NET   (IP address required)
 *  - USB         : PrinterInfo.Port.USB   (handled internally by Brother SDK)
 *
 * The [badge] bitmap is sent directly via [com.brother.ptouch.sdk.Printer.printImage].
 * The Brother SDK scales it to fit the loaded media automatically when
 * [com.brother.ptouch.sdk.PrinterInfo.PrintMode] is set to FIT_PAGE.
 *
 * ⚠  Adjust [PrinterInfo.printerModel] and label/paper settings in the
 *    Admin Panel → Printer Settings to match your physical device.
 */
object BrotherPrinterAdapter : PrinterAdapter {

    /** Brother SDK error code for "no error". */
    private const val BROTHER_ERROR_NONE = "ERROR_NONE"

    override suspend fun printBitmap(
        context: Context,
        badge: Bitmap,
        config: PrinterConfig
    ): PrintResult = withContext(Dispatchers.IO) {
        try {
            doPrint(context, badge, config)
        } catch (e: Exception) {
            PrintResult.Error("Brother: ${e.message ?: "Unknown error"}")
        }
    }

    override suspend fun testConnection(
        context: Context,
        config: PrinterConfig
    ): String? = withContext(Dispatchers.IO) {
        val result = withTimeoutOrNull(10_000L) {
            // Create a minimal 1×1 white bitmap as a probe job (no ink consumed).
            val probe = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).also {
                it.eraseColor(android.graphics.Color.WHITE)
            }
            doPrint(context, probe, config).also { probe.recycle() }
        } ?: return@withContext "Connection timed out (10 s)"

        return@withContext when (result) {
            is PrintResult.Success             -> null
            is PrintResult.PermissionRequested -> "USB permission required"
            is PrintResult.Error               -> result.message
        }
    }

    // ── Core print logic ──────────────────────────────────────────────────

    private fun doPrint(context: Context, bitmap: Bitmap, config: PrinterConfig): PrintResult {
        // Reflective access so the code compiles even if the AAR is temporarily absent.
        // All calls are wrapped and will surface a clear error if the SDK is missing.
        return try {
            val printerClass      = Class.forName("com.brother.ptouch.sdk.Printer")
            val printerInfoClass  = Class.forName("com.brother.ptouch.sdk.PrinterInfo")
            val printerStatusClass = Class.forName("com.brother.ptouch.sdk.PrinterStatus")

            val printer = printerClass.getDeclaredConstructor().newInstance()

            // --- Build PrinterInfo ---
            val printerInfo = printerInfoClass.getDeclaredConstructor().newInstance()

            // Printer model
            val modelEnum = getModelEnum(printerInfoClass, config.brotherModel)
            if (modelEnum != null) {
                printerInfoClass.getField("printerModel").set(printerInfo, modelEnum)
            }

            // Connection port
            val portEnum  = getPortEnum(printerInfoClass, config)
            if (portEnum != null) {
                printerInfoClass.getField("port").set(printerInfo, portEnum)
            }

            // IP address (required for NETWORK)
            if (config.connectionType == PrinterConfig.ConnectionType.NETWORK) {
                val host = config.networkHost?.ifBlank { null }
                    ?: return PrintResult.Error(
                        "Brother: No IP address configured. Open Printer Settings."
                    )
                printerInfoClass.getField("ipAddress").set(printerInfo, host)
            }

            // Orientation: LANDSCAPE for our 812×609 badge
            trySetOrientation(printerInfoClass, printerInfo, landscape = true)

            // PrintMode: FIT_PAGE scales bitmap to label media
            trySetPrintMode(printerInfoClass, printerInfo)

            // numberOfCopies = 1
            trySetField(printerInfoClass, printerInfo, "numberOfCopies", 1)

            // Apply info to printer
            printerClass.getMethod("setPrinterInfo", printerInfoClass).invoke(printer, printerInfo)

            // --- Start communication ---
            printerClass.getMethod("startCommunication").invoke(printer)

            // --- Print image ---
            val status = try {
                printerClass.getMethod("printImage", Bitmap::class.java)
                    .invoke(printer, bitmap)
            } finally {
                // Always end communication, even if printImage throws
                runCatching {
                    printerClass.getMethod("endCommunication").invoke(printer)
                }
            }

            // --- Evaluate result ---
            if (status == null) return PrintResult.Error("Brother: null status returned")
            val errorCode = printerStatusClass.getField("errorCode").get(status)
            val errorName = errorCode?.javaClass?.getMethod("name")?.invoke(errorCode)?.toString()
                ?: "UNKNOWN"

            if (errorName == BROTHER_ERROR_NONE) {
                PrintResult.Success
            } else {
                PrintResult.Error("Brother error: $errorName")
            }

        } catch (cnf: ClassNotFoundException) {
            PrintResult.Error(
                "BrotherPrintLibrary.aar not found. Add it to app/libs/ and rebuild."
            )
        } catch (e: Exception) {
            PrintResult.Error("Brother: ${e.message ?: "Unexpected error"}")
        }
    }

    // ── Reflection helpers ────────────────────────────────────────────────

    private fun getModelEnum(printerInfoClass: Class<*>, modelName: String): Any? = runCatching {
        val modelClass  = printerInfoClass.getDeclaredClasses()
            .firstOrNull { it.simpleName == "Model" } ?: return@runCatching null
        // Map our BrotherModel.sdkName to the SDK enum constant
        val sdkName = PrinterConfig.BrotherModel.entries
            .firstOrNull { it.name == modelName }?.sdkName ?: modelName
        modelClass.getField(sdkName).get(null)
    }.getOrNull()

    private fun getPortEnum(printerInfoClass: Class<*>, config: PrinterConfig): Any? = runCatching {
        val portClass = printerInfoClass.getDeclaredClasses()
            .firstOrNull { it.simpleName == "Port" } ?: return@runCatching null
        val portName  = when (config.connectionType) {
            PrinterConfig.ConnectionType.USB     -> "USB"
            PrinterConfig.ConnectionType.NETWORK -> "NET"
        }
        portClass.getField(portName).get(null)
    }.getOrNull()

    private fun trySetOrientation(infoClass: Class<*>, info: Any, landscape: Boolean) {
        runCatching {
            val orientClass = infoClass.getDeclaredClasses()
                .firstOrNull { it.simpleName == "Orientation" } ?: return
            val value = orientClass.getField(if (landscape) "LANDSCAPE" else "PORTRAIT").get(null)
            infoClass.getField("orientation").set(info, value)
        }
    }

    private fun trySetPrintMode(infoClass: Class<*>, info: Any) {
        runCatching {
            val modeClass = infoClass.getDeclaredClasses()
                .firstOrNull { it.simpleName == "PrintMode" } ?: return
            // FIT_PAGE scales the bitmap to fill the loaded label media
            val value = runCatching { modeClass.getField("FIT_PAGE").get(null) }.getOrNull()
                ?: runCatching { modeClass.getField("FIT_TO_PAGE").get(null) }.getOrNull()
                ?: return
            infoClass.getField("printMode").set(info, value)
        }
    }

    private fun trySetField(infoClass: Class<*>, info: Any, fieldName: String, value: Any) {
        runCatching { infoClass.getField(fieldName).set(info, value) }
    }
}

