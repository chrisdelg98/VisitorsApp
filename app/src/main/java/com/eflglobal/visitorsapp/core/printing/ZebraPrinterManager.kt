package com.eflglobal.visitorsapp.core.printing

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import com.zebra.sdk.comm.TcpConnection
import com.zebra.sdk.comm.UsbConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Handles all communication with the Zebra ZT230 printer.
 *
 * Supported transports:
 *  - USB  : via Android USB Host API + Zebra Link-OS SDK [UsbConnection]
 *  - Network (TCP/IP) : via [TcpConnection] on port 9100
 *
 * Both transports send the same ZPL string — only the connection mechanism differs.
 */
object ZebraPrinterManager {

    /** Zebra Technologies USB Vendor ID (0x0A5F = 2655 decimal). */
    private const val ZEBRA_VENDOR_ID = 0x0A5F

    private const val ACTION_USB_PERMISSION =
        "com.eflglobal.visitorsapp.USB_PERMISSION"

    // ── Result types ──────────────────────────────────────────────────────────

    sealed class PrintResult {
        object Success : PrintResult()
        /** USB permission dialog was shown — user should tap Print again after granting. */
        object PermissionRequested : PrintResult()
        data class Error(val message: String) : PrintResult()
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Sends [zpl] to the printer described by [config].
     * Must be called from a coroutine (suspending) — do NOT call from main thread.
     */
    suspend fun printZpl(
        context: Context,
        zpl: String,
        config: PrinterConfig
    ): PrintResult = withContext(Dispatchers.IO) {
        try {
            when (config.connectionType) {
                PrinterConfig.ConnectionType.USB     -> printViaUsb(context, zpl)
                PrinterConfig.ConnectionType.NETWORK -> printViaNetwork(zpl, config)
            }
        } catch (e: Exception) {
            PrintResult.Error(e.message ?: "Unexpected printing error")
        }
    }

    /**
     * Sends an empty label (^XA^XZ) just to verify the connection opens cleanly.
     * @return null on success, error message string on failure.
     */
    suspend fun testConnection(context: Context, config: PrinterConfig): String? =
        withContext(Dispatchers.IO) {
            val result = withTimeoutOrNull(8_000L) {
                printZpl(context, "^XA^XZ", config)
            } ?: return@withContext "Connection timed out (8 s)"

            return@withContext when (result) {
                is PrintResult.Success           -> null
                is PrintResult.PermissionRequested -> "USB permission requested — grant it and try again"
                is PrintResult.Error             -> result.message
            }
        }

    // ── USB ───────────────────────────────────────────────────────────────────

    private suspend fun printViaUsb(context: Context, zpl: String): PrintResult {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

        // Find the first connected Zebra printer
        val zebraDevice = usbManager.deviceList.values
            .firstOrNull { it.vendorId == ZEBRA_VENDOR_ID }
            ?: return PrintResult.Error(
                "No Zebra printer detected via USB. Check cable and printer power."
            )

        // Request permission if not already granted
        if (!usbManager.hasPermission(zebraDevice)) {
            val granted = requestUsbPermission(context, usbManager, zebraDevice)
            return if (granted) {
                // Permission just granted — retry the actual print
                sendZplViaUsb(usbManager, zebraDevice, zpl)
            } else {
                PrintResult.PermissionRequested
            }
        }

        return sendZplViaUsb(usbManager, zebraDevice, zpl)
    }

    private fun sendZplViaUsb(
        usbManager: UsbManager,
        device: android.hardware.usb.UsbDevice,
        zpl: String
    ): PrintResult {
        val connection = UsbConnection(usbManager, device)
        return try {
            connection.open()
            connection.write(zpl.toByteArray(Charsets.UTF_8))
            PrintResult.Success
        } catch (e: Exception) {
            PrintResult.Error("USB print failed: ${e.message}")
        } finally {
            try { connection.close() } catch (_: Exception) {}
        }
    }

    // ── Network (TCP/IP) ──────────────────────────────────────────────────────

    private fun printViaNetwork(zpl: String, config: PrinterConfig): PrintResult {
        val host = config.networkHost?.ifBlank { null }
            ?: return PrintResult.Error(
                "No printer IP address configured. Open Printer Settings in Admin Panel."
            )
        val port = config.networkPort

        val connection = TcpConnection(host, port)
        return try {
            connection.open()
            connection.write(zpl.toByteArray(Charsets.UTF_8))
            PrintResult.Success
        } catch (e: Exception) {
            PrintResult.Error("Network print failed ($host:$port) — ${e.message}")
        } finally {
            try { connection.close() } catch (_: Exception) {}
        }
    }

    // ── USB permission helper ─────────────────────────────────────────────────

    /**
     * Suspends until the OS USB-permission dialog is answered.
     * Returns true if the user granted permission, false if denied.
     */
    private suspend fun requestUsbPermission(
        context: Context,
        usbManager: UsbManager,
        device: android.hardware.usb.UsbDevice
    ): Boolean = suspendCancellableCoroutine { cont ->

        val permissionIntent = PendingIntent.getBroadcast(
            context, 0,
            Intent(ACTION_USB_PERMISSION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (ACTION_USB_PERMISSION == intent.action) {
                    ctx.unregisterReceiver(this)
                    val granted = intent.getBooleanExtra(
                        UsbManager.EXTRA_PERMISSION_GRANTED, false
                    )
                    if (cont.isActive) cont.resume(granted)
                }
            }
        }

        ContextCompat.registerReceiver(
            context, receiver,
            IntentFilter(ACTION_USB_PERMISSION),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        cont.invokeOnCancellation {
            runCatching { context.unregisterReceiver(receiver) }
        }

        usbManager.requestPermission(device, permissionIntent)
    }
}



