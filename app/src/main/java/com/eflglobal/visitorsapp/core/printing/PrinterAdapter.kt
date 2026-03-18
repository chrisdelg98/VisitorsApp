package com.eflglobal.visitorsapp.core.printing

import android.content.Context
import android.graphics.Bitmap

/**
 * Unified print result shared by every adapter.
 */
sealed class PrintResult {
    /** Print job sent successfully. */
    object Success : PrintResult()
    /** USB permission dialog was shown — user must tap Print again after granting. */
    object PermissionRequested : PrintResult()
    /** Something went wrong — [message] contains the human-readable error. */
    data class Error(val message: String) : PrintResult()
}

/**
 * Contract every printer adapter must fulfil.
 *
 * All suspend functions must be called from a background coroutine (Dispatchers.IO).
 */
interface PrinterAdapter {

    /**
     * Print [badge] bitmap on the printer described by [config].
     *
     * The bitmap is assumed to be 812 × 609 px (4 × 3 in at 203 DPI).
     * Each adapter is responsible for converting / sending it appropriately.
     */
    suspend fun printBitmap(
        context: Context,
        badge: Bitmap,
        config: PrinterConfig
    ): PrintResult

    /**
     * Probe the connection without printing anything.
     * Returns **null** on success, error message on failure.
     */
    suspend fun testConnection(
        context: Context,
        config: PrinterConfig
    ): String?
}

