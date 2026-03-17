package com.eflglobal.visitorsapp.core.printing

/**
 * Configuration for the Zebra label printer.
 * Persisted via DataStore — survives app restarts.
 */
data class PrinterConfig(
    val connectionType: ConnectionType = ConnectionType.USB,
    /** IP address or hostname — only used when connectionType == NETWORK. */
    val networkHost: String? = null,
    /** TCP port — Zebra default is 9100. */
    val networkPort: Int = DEFAULT_PORT
) {
    enum class ConnectionType { USB, NETWORK }

    companion object {
        const val DEFAULT_PORT = 9100
    }
}

