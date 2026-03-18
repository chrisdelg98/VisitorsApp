package com.eflglobal.visitorsapp.core.printing

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Central printer dispatcher.
 *
 * Usage from a Composable:
 * ```kotlin
 * coroutineScope.launch {
 *     val badge  = BadgeBitmapRenderer.render(renderData)
 *     val result = PrinterManager.printBadge(context, badge)
 *     badge.recycle()
 * }
 * ```
 *
 * The config is loaded once per print job from [PrinterConfigRepository].
 * Rendering + sending both happen on [Dispatchers.IO].
 */
object PrinterManager {

    /**
     * Renders [renderData] into a bitmap, reads the persisted config and
     * dispatches to the correct [PrinterAdapter].
     *
     * Must be called from a coroutine.
     */
    suspend fun printBadge(
        context: Context,
        renderData: BadgeBitmapRenderer.RenderData
    ): PrintResult = withContext(Dispatchers.IO) {
        val config = PrinterConfigRepository.getConfig(context).first()
        val badge  = BadgeBitmapRenderer.render(renderData)
        val result = printBitmap(context, badge, config)
        badge.recycle()
        result
    }

    /**
     * Sends an already-rendered [badge] bitmap to the printer.
     * Use this overload when the bitmap is produced elsewhere (e.g. the AdminPanel).
     */
    suspend fun printBitmap(
        context: Context,
        badge: Bitmap,
        config: PrinterConfig
    ): PrintResult = withContext(Dispatchers.IO) {
        when (config.brand) {
            PrinterConfig.PrinterBrand.ZEBRA   -> ZebraPrinterAdapter.printBitmap(context, badge, config)
            PrinterConfig.PrinterBrand.BROTHER -> BrotherPrinterAdapter.printBitmap(context, badge, config)
            PrinterConfig.PrinterBrand.NONE    -> PrintResult.Error(
                "No printer configured. Open Admin Panel → Printer Settings."
            )
        }
    }

    /**
     * Tests connectivity without printing anything meaningful.
     * Returns **null** on success, error message on failure.
     */
    suspend fun testConnection(
        context: Context,
        config: PrinterConfig
    ): String? = withContext(Dispatchers.IO) {
        when (config.brand) {
            PrinterConfig.PrinterBrand.ZEBRA   -> ZebraPrinterAdapter.testConnection(context, config)
            PrinterConfig.PrinterBrand.BROTHER -> BrotherPrinterAdapter.testConnection(context, config)
            PrinterConfig.PrinterBrand.NONE    -> "No printer brand selected"
        }
    }

    /**
     * Full diagnostic probe: connects, queries status, and returns structured
     * [PrinterDiagnostics] with individual check items for the UI panel.
     */
    suspend fun diagnose(
        context: Context,
        config: PrinterConfig
    ): PrinterDiagnostics = withContext(Dispatchers.IO) {
        when (config.brand) {
            PrinterConfig.PrinterBrand.ZEBRA   -> ZebraPrinterAdapter.diagnose(context, config)
            PrinterConfig.PrinterBrand.BROTHER -> BrotherPrinterAdapter.diagnose(context, config)
            PrinterConfig.PrinterBrand.NONE    -> PrinterDiagnostics(
                isConnected = false,
                summary = "No printer brand selected",
                checks = listOf(
                    DiagnosticItem("Brand", "Not configured", DiagStatus.ERROR)
                )
            )
        }
    }
}

