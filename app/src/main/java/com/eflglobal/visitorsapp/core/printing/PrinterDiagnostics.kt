package com.eflglobal.visitorsapp.core.printing

/**
 * Structured diagnostics returned by the printer test.
 * Each [DiagnosticItem] represents one check with a status icon.
 */
data class PrinterDiagnostics(
    /** Overall success — true if the printer is reachable and ready. */
    val isConnected: Boolean,
    /** Short summary: "Connected" / "Connection failed" etc. */
    val summary: String,
    /** Ordered list of individual checks performed. */
    val checks: List<DiagnosticItem>
)

data class DiagnosticItem(
    /** Human-readable label, e.g. "Network reachable" */
    val label: String,
    /** Detected value or detail, e.g. "192.168.1.50:9100" */
    val value: String,
    /** Status of this check */
    val status: DiagStatus
)

enum class DiagStatus {
    /** Check passed */
    OK,
    /** Check had a non-critical warning */
    WARNING,
    /** Check failed */
    ERROR,
    /** Informational — not pass/fail */
    INFO
}

