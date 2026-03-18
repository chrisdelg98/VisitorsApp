package com.eflglobal.visitorsapp.core.printing

/**
 * Configuration for the visitor-badge label printer.
 * Supports Zebra (ZT230 / Link-OS) and Brother (Mobile SDK).
 * Persisted via DataStore — survives app restarts.
 */
data class PrinterConfig(
    /** Which printer brand is connected. */
    val brand: PrinterBrand = PrinterBrand.NONE,
    val connectionType: ConnectionType = ConnectionType.USB,
    /** IP address or hostname — used when connectionType == NETWORK for both brands. */
    val networkHost: String? = null,
    /** TCP port — Zebra default 9100; Brother default 9100. */
    val networkPort: Int = DEFAULT_PORT,
    /** Brother printer model key (maps to PrinterInfo.Model enum in the Brother SDK). */
    val brotherModel: String = BrotherModel.QL_810W.name
) {
    enum class PrinterBrand { NONE, ZEBRA, BROTHER }
    enum class ConnectionType { USB, NETWORK }

    /**
     * Supported Brother label printer models.
     * [sdkName] must match the constant name in com.brother.ptouch.sdk.PrinterInfo.Model
     */
    enum class BrotherModel(val displayName: String, val sdkName: String) {
        QL_810W      ("QL-810W",       "QL_810W"),
        QL_1110NWB   ("QL-1110NWB",    "QL_1110NWB"),
        QL_820NWB    ("QL-820NWB",     "QL_820NWB"),
        TD_4550DNWB  ("TD-4550DNWB",   "TD_4550DNWB"),
        TD_4650TNWB  ("TD-4650TNWB",   "TD_4650TNWB"),
        TD_2135NWB   ("TD-2135NWB",    "TD_2135NWB"),
        RJ_4250WB    ("RJ-4250WB",     "RJ_4250WB"),
        RJ_3250WBL   ("RJ-3250WBL",    "RJ_3250WBL"),
    }

    companion object {
        const val DEFAULT_PORT = 9100
    }
}
