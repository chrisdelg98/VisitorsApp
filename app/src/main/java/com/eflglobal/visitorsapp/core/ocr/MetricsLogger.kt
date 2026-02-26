package com.eflglobal.visitorsapp.core.ocr

import com.eflglobal.visitorsapp.data.local.dao.OcrMetricDao
import com.eflglobal.visitorsapp.data.local.entity.OcrMetricEntity
import com.eflglobal.visitorsapp.domain.model.DocumentClassification
import com.eflglobal.visitorsapp.domain.model.ExtractedField
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * ═══════════════════════════════════════════════════════════════════════════════
 * MetricsLogger
 *
 * Persists OCR pipeline metrics to Room for data-driven threshold tuning.
 *
 * Usage:
 *   // At scan time (non-blocking fire-and-forget):
 *   MetricsLogger.logScan(dao, classification, firstName, lastName, docNumber, ocrText)
 *
 *   // When user submits the form after potentially correcting fields:
 *   MetricsLogger.logCorrections(dao, metricId, wasFirstNameChanged, wasLastNameChanged)
 * ═══════════════════════════════════════════════════════════════════════════════
 */
object MetricsLogger {

    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Logs a completed OCR pipeline run. Fire-and-forget — never blocks the UI.
     * Returns the inserted row ID so callers can log corrections later.
     */
    fun logScan(
        dao: OcrMetricDao,
        classification: DocumentClassification,
        firstName: ExtractedField,
        lastName: ExtractedField,
        docNumber: ExtractedField,
        ocrText: String,
        hasMrz: Boolean
    ): Long {
        // We use a shared result holder; logScan is fire-and-forget in practice
        var insertedId = -1L
        scope.launch {
            try {
                val entity = OcrMetricEntity(
                    timestamp                  = System.currentTimeMillis(),
                    detectedCountry            = classification.country,
                    detectedDocType            = classification.documentType,
                    classificationConfidence   = classification.confidence,
                    firstNameConfidence        = firstName.confidence,
                    lastNameConfidence         = lastName.confidence,
                    docNumberConfidence        = docNumber.confidence,
                    firstNameSource            = firstName.source.name,
                    lastNameSource             = lastName.source.name,
                    docNumberSource            = docNumber.source.name,
                    firstNameAutoFilled        = firstName.isAutoFillable,
                    lastNameAutoFilled         = lastName.isAutoFillable,
                    docNumberAutoFilled        = docNumber.isAutoFillable,
                    firstNameCorrected         = false,
                    lastNameCorrected          = false,
                    docNumberCorrected         = false,
                    ocrCharCount               = ocrText.length,
                    ocrLineCount               = ocrText.lines().count { it.isNotBlank() },
                    hasMrz                     = hasMrz
                )
                insertedId = dao.insert(entity)
                log("scan logged id=$insertedId country=${classification.country} " +
                    "fnConf=${"%.2f".format(firstName.confidence)} " +
                    "lnConf=${"%.2f".format(lastName.confidence)} " +
                    "docConf=${"%.2f".format(docNumber.confidence)}")

                // Auto-prune: keep only the last 1000 records
                pruneIfNeeded(dao)
            } catch (e: Exception) {
                log("logScan failed: ${e.message}")
            }
        }
        return insertedId
    }

    /**
     * Updates an existing metric row to mark which fields the user corrected.
     * Call this when the user taps "Continue" on PersonDataScreen.
     *
     * @param metricId  The id returned by [logScan].
     * @param dao       OcrMetricDao instance.
     * @param fnChanged True if user changed the auto-filled first name.
     * @param lnChanged True if user changed the auto-filled last name.
     * @param docChanged True if user changed the auto-filled document number.
     */
    fun logCorrections(
        dao: OcrMetricDao,
        metricId: Long,
        fnChanged: Boolean,
        lnChanged: Boolean,
        docChanged: Boolean
    ) {
        if (metricId <= 0L) return
        scope.launch {
            try {
                // Load, mutate, re-insert (Room autoGenerate PK = replace strategy)
                val metrics = dao.recentMetrics(1000)
                val target  = metrics.firstOrNull { it.id == metricId } ?: return@launch
                dao.insert(
                    target.copy(
                        firstNameCorrected  = fnChanged,
                        lastNameCorrected   = lnChanged,
                        docNumberCorrected  = docChanged
                    )
                )
                log("corrections logged id=$metricId fn=$fnChanged ln=$lnChanged doc=$docChanged")
            } catch (e: Exception) {
                log("logCorrections failed: ${e.message}")
            }
        }
    }

    /**
     * Returns a human-readable summary of accumulated metrics for debugging.
     * Safe to call from any coroutine.
     */
    suspend fun summary(dao: OcrMetricDao): String {
        return try {
            val total       = dao.count()
            val avgFn       = dao.avgFirstNameConfidence() ?: 0f
            val avgLn       = dao.avgLastNameConfidence()  ?: 0f
            val fnCorr      = dao.firstNameCorrectionCount()
            val lnCorr      = dao.lastNameCorrectionCount()
            val byCountry   = dao.countryBreakdown()

            buildString {
                appendLine("=== OCR Metrics Summary ===")
                appendLine("Total scans : $total")
                appendLine("Avg FN conf : ${"%.2f".format(avgFn)}")
                appendLine("Avg LN conf : ${"%.2f".format(avgLn)}")
                appendLine("FN corrections: $fnCorr / $total (${"%.0f".format(if (total > 0) fnCorr * 100f / total else 0f)}%)")
                appendLine("LN corrections: $lnCorr / $total (${"%.0f".format(if (total > 0) lnCorr * 100f / total else 0f)}%)")
                appendLine("By country:")
                byCountry.forEach { appendLine("  ${it.detectedCountry}: ${it.cnt}") }
            }
        } catch (e: Exception) {
            "MetricsLogger.summary error: ${e.message}"
        }
    }

    private suspend fun pruneIfNeeded(dao: OcrMetricDao) {
        val count = dao.count()
        if (count > 1000) {
            val cutoff = System.currentTimeMillis() - (90L * 24 * 60 * 60 * 1000) // 90 days
            dao.deleteOlderThan(cutoff)
        }
    }

    private fun log(msg: String) = android.util.Log.d("MetricsLogger", msg)
}

