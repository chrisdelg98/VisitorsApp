package com.eflglobal.visitorsapp.core.ocr

import com.eflglobal.visitorsapp.data.local.dao.OcrMetricDao
import com.eflglobal.visitorsapp.domain.model.DocumentClassification
import com.eflglobal.visitorsapp.domain.model.ExtractedField
import com.eflglobal.visitorsapp.domain.model.FieldSource
import com.google.mlkit.vision.text.Text

/**
 * ═══════════════════════════════════════════════════════════════════════════════
 * DocumentProcessingPipeline
 *
 * Central orchestrator that replaces the ad-hoc logic in DocumentDataExtractor.
 *
 * Pipeline steps (in order):
 *
 *   1. DocumentClassifier  — classify country + doc type, get confidence
 *   2. MrzParser           — attempt MRZ extraction (highest reliability)
 *   3. EntityExtractionAdapter — async ML Kit entity pass (bonus signals)
 *   4. FieldScoringEngine  — probabilistic per-field scoring
 *   5. Confidence threshold gate — only auto-fill fields ≥ 0.60
 *   6. MetricsLogger       — fire-and-forget persistence
 *
 * Returns [PipelineResult] which replaces DocumentDataExtractor.ExtractionResult.
 * DocumentDataExtractor is kept as a compatibility shim that delegates here.
 * ═══════════════════════════════════════════════════════════════════════════════
 */
object DocumentProcessingPipeline {

    // ─── Result ───────────────────────────────────────────────────────────────

    data class PipelineResult(
        val firstName: ExtractedField,
        val lastName: ExtractedField,
        val documentNumber: ExtractedField,
        val classification: DocumentClassification,
        val mrzData: MrzParser.MrzData?,
        val fullOcrText: String,
        /** Row ID of the metric entry — pass to MetricsLogger.logCorrections() later. */
        val metricId: Long = -1L
    ) {
        /** Convenience: auto-fillable first name or null */
        val autoFirstName: String? get() =
            firstName.value?.takeIf { firstName.isAutoFillable }

        /** Convenience: auto-fillable last name or null */
        val autoLastName: String? get() =
            lastName.value?.takeIf { lastName.isAutoFillable }

        /** Convenience: auto-fillable document number or null */
        val autoDocNumber: String? get() =
            documentNumber.value?.takeIf { documentNumber.isAutoFillable }
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Run the full pipeline on already-obtained OCR text.
     *
     * @param fullText      Raw OCR string from ML Kit.
     * @param visionText    Structured ML Kit Text (used for block-level hints).
     * @param ocrMetricDao  Optional DAO — when provided, metrics are persisted.
     */
    suspend fun process(
        fullText: String,
        visionText: Text?,
        ocrMetricDao: OcrMetricDao? = null
    ): PipelineResult {

        log("=== Pipeline START (${fullText.length} chars) ===")

        if (fullText.isBlank()) {
            log("Empty OCR text — returning empty result")
            return emptyResult(fullText)
        }

        val lines = fullText.split("\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        // ── STEP 1: Classify document ─────────────────────────────────────────
        val classification = DocumentClassifier.classify(fullText)
        log("STEP 1 classification → ${classification.country}/${classification.documentType} conf=${"%.2f".format(classification.confidence)}")

        // If confidence < 0.50 we still attempt extraction, but thresholds are stricter
        val isClassified = classification.isReliable

        // ── STEP 2: MRZ ───────────────────────────────────────────────────────
        val mrzData = MrzParser.parse(fullText)
        if (mrzData != null && mrzData.isReliable) {
            log("STEP 2 MRZ ✅ format=${mrzData.mrzFormat}")
            val fnField = ExtractedField(
                value      = mrzData.firstName.ifBlank { null },
                confidence = mrzData.confidence,
                source     = FieldSource.MRZ
            )
            val lnField = ExtractedField(
                value      = mrzData.lastName.ifBlank { null },
                confidence = mrzData.confidence,
                source     = FieldSource.MRZ
            )
            val docField = ExtractedField(
                value      = mrzData.documentNumber.ifBlank { null },
                confidence = mrzData.confidence,
                source     = FieldSource.MRZ
            )
            val metricId = ocrMetricDao?.let {
                MetricsLogger.logScan(it, classification, fnField, lnField, docField, fullText, hasMrz = true)
            } ?: -1L
            return PipelineResult(fnField, lnField, docField, classification, mrzData, fullText, metricId)
        }
        log("STEP 2 MRZ — not found or unreliable")

        // ── STEP 3: Entity extraction (async, optional) ────────────────────────
        val entities = try {
            EntityExtractionAdapter.extractEntities(fullText)
        } catch (e: Exception) {
            log("STEP 3 entity extraction failed silently: ${e.message}")
            EntityExtractionAdapter.EntityExtractionResult.EMPTY
        }
        log("STEP 3 entities → nonName=${entities.nonNameLines.size} dateLines=${entities.dateLines.size}")

        // ── STEP 4: Field scoring ─────────────────────────────────────────────
        // Lines confirmed as dates/phones by entity extraction are penalised
        // by passing them as context — FieldScoringEngine scores each line anyway
        // so we add the non-name lines as negative-boost set.
        val entityNonNameLines = entities.nonNameLines

        val rawFirstName = FieldScoringEngine.scoreFirstName(
            lines          = lines,
            classification = classification,
            entityBoostLines = emptySet() // no positive person-entity boost available in public SDK
        )
        val rawLastName = FieldScoringEngine.scoreLastName(
            lines          = lines,
            classification = classification,
            entityBoostLines = emptySet()
        )
        val rawDocNumber = FieldScoringEngine.scoreDocumentNumber(
            lines          = lines,
            classification = classification
        )

        log("STEP 4 scoring → fn='${rawFirstName.value}' conf=${"%.2f".format(rawFirstName.confidence)} | " +
            "ln='${rawLastName.value}' conf=${"%.2f".format(rawLastName.confidence)} | " +
            "doc='${rawDocNumber.value}' conf=${"%.2f".format(rawDocNumber.confidence)}")

        // ── STEP 5: Confidence threshold gate ────────────────────────────────
        // Fields below AUTOFILL_THRESHOLD (0.60) still carry their value so the
        // UI can show them with a warning badge, but isAutoFillable will be false.
        val firstName  = penaliseIfNonName(rawFirstName, entityNonNameLines)
        val lastName   = penaliseIfNonName(rawLastName, entityNonNameLines)

        // ── STEP 6: Fallback — if scoring found nothing, try legacy extractor ──
        val fnFinal  = if (firstName.value.isNullOrBlank())  legacyFallbackName(fullText, isFirst = true)  else firstName
        val lnFinal  = if (lastName.value.isNullOrBlank())   legacyFallbackName(fullText, isFirst = false) else lastName
        val docFinal = if (rawDocNumber.value.isNullOrBlank()) legacyFallbackDoc(fullText) else rawDocNumber

        log("STEP 5/6 final → fn='${fnFinal.value}' autoFill=${fnFinal.isAutoFillable} | " +
            "ln='${lnFinal.value}' autoFill=${lnFinal.isAutoFillable} | " +
            "doc='${docFinal.value}' autoFill=${docFinal.isAutoFillable}")

        // ── STEP 7: Metrics ───────────────────────────────────────────────────
        val metricId = ocrMetricDao?.let {
            MetricsLogger.logScan(it, classification, fnFinal, lnFinal, docFinal, fullText, hasMrz = false)
        } ?: -1L

        log("=== Pipeline END ===")

        return PipelineResult(
            firstName      = fnFinal,
            lastName       = lnFinal,
            documentNumber = docFinal,
            classification = classification,
            mrzData        = mrzData,
            fullOcrText    = fullText,
            metricId       = metricId
        )
    }

    // ─── Fallback to legacy extractor logic ───────────────────────────────────

    private fun legacyFallbackName(text: String, isFirst: Boolean): ExtractedField {
        // Delegate to DocumentDataExtractor's existing proven logic
        val result = DocumentDataExtractor.extractFromText(text, null)
        val value  = if (isFirst) result.firstName else result.lastName
        if (value.isNullOrBlank()) return ExtractedField.EMPTY
        return ExtractedField(
            value      = value,
            confidence = 0.55f, // below autofill threshold — shown as suggestion only
            source     = FieldSource.HEURISTIC
        )
    }

    private fun legacyFallbackDoc(text: String): ExtractedField {
        val num = DocumentDataExtractor.extractDocumentNumber(text) ?: return ExtractedField.EMPTY
        return ExtractedField(
            value      = num,
            confidence = 0.50f,
            source     = FieldSource.HEURISTIC
        )
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /** If the field value appears in entity non-name lines, penalise confidence by 0.20. */
    private fun penaliseIfNonName(field: ExtractedField, nonNameLines: Set<String>): ExtractedField {
        if (field.value == null) return field
        val isNonName = nonNameLines.any { field.value.contains(it, ignoreCase = true) }
        if (!isNonName) return field
        val penalised = (field.confidence - 0.20f).coerceAtLeast(0f)
        log("  penalise '${field.value}' → ${"%.2f".format(field.confidence)} → ${"%.2f".format(penalised)}")
        return field.copy(
            confidence     = penalised,
            scoreBreakdown = field.scoreBreakdown + mapOf("entity_nonname_penalty" to -0.20f)
        )
    }

    private fun emptyResult(fullText: String) = PipelineResult(
        firstName      = ExtractedField.EMPTY,
        lastName       = ExtractedField.EMPTY,
        documentNumber = ExtractedField.EMPTY,
        classification = DocumentClassification.UNKNOWN,
        mrzData        = null,
        fullOcrText    = fullText
    )

    private fun log(msg: String) = android.util.Log.d("DocPipeline", msg)
}

