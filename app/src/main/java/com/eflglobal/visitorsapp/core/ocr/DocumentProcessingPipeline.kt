package com.eflglobal.visitorsapp.core.ocr

import com.eflglobal.visitorsapp.data.local.dao.OcrMetricDao
import com.eflglobal.visitorsapp.domain.model.DocumentClassification
import com.eflglobal.visitorsapp.domain.model.DocumentTemplate
import com.eflglobal.visitorsapp.domain.model.ExtractedField
import com.eflglobal.visitorsapp.domain.model.FieldSource
import com.google.mlkit.vision.text.Text

/**
 * ═══════════════════════════════════════════════════════════════════════════════
 * DocumentProcessingPipeline
 *
 * Central orchestrator. Pipeline steps (in order):
 *
 *   1. DocumentClassifier   — generic country/type guess (fallback signal)
 *   2. MrzParser            — MRZ extraction
 *   3. TemplateMatcher      — data-driven identification from the local catalog
 *   4. Barcode fields       — AAMVA/known parse from the back-side barcode
 *   5. TemplateFieldExtractor — anchor/zone field extraction for the matched template
 *   6. EntityExtractionAdapter — async ML Kit entity pass (bonus signals)
 *   7. FieldScoringEngine   — probabilistic per-field scoring (generic FALLBACK)
 *   8. Priority merge       — BARCODE > MRZ > TEMPLATE > generic > legacy
 *   9. Failure reporting    — unrecognised / low-confidence / unknown barcode
 *  10. MetricsLogger        — fire-and-forget persistence
 *
 * Offline-first: with an empty catalog and no barcode, steps 3–5 no-op and the
 * behaviour is identical to the previous generic-only pipeline.
 * ═══════════════════════════════════════════════════════════════════════════════
 */
object DocumentProcessingPipeline {

    // Confidence assigned to AAMVA barcode fields (US/CA — standard & reliable).
    private const val BARCODE_CONFIDENCE = 0.92f

    // ─── Result ───────────────────────────────────────────────────────────────

    data class PipelineResult(
        val firstName: ExtractedField,
        val lastName: ExtractedField,
        val documentNumber: ExtractedField,
        val classification: DocumentClassification,
        val mrzData: MrzParser.MrzData?,
        val fullOcrText: String,
        /** Row ID of the metric entry — pass to MetricsLogger.logCorrections() later. */
        val metricId: Long = -1L,
        /** The catalog template that identified this document, if any. */
        val matchedTemplate: DocumentTemplate? = null,
        /** The decoded/parsed barcode for this side, if any. */
        val barcode: BarcodeReader.BarcodeReadResult? = null
    ) {
        val autoFirstName: String? get() =
            firstName.value?.takeIf { firstName.isAutoFillable }
        val autoLastName: String? get() =
            lastName.value?.takeIf { lastName.isAutoFillable }
        val autoDocNumber: String? get() =
            documentNumber.value?.takeIf { documentNumber.isAutoFillable }
    }

    /**
     * Called by the pipeline when a document could not be reliably read, so the
     * caller (which owns Context/network) can enqueue a failure report. The
     * pipeline itself stays free of the data layer.
     *
     * @param barcodeRaw  Raw barcode content when a code was present but not parsed.
     */
    fun interface UnrecognisedSink {
        suspend fun onUnrecognised(
            detectedType: String?,
            detectedConfidence: Float?,
            barcodeRaw: String?
        )
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Run the full pipeline.
     *
     * @param fullText      Raw OCR string from ML Kit.
     * @param visionText    Structured ML Kit Text (block boxes for template extraction).
     * @param ocrMetricDao  Optional DAO — when provided, metrics are persisted.
     * @param templates     Local OCR template catalog (empty → generic pipeline).
     * @param barcode       Decoded/parsed barcode for this side (null → none).
     * @param imageWidth    Pixel width of the OCR'd bitmap (for zone/aspect); 0 = unknown.
     * @param imageHeight   Pixel height of the OCR'd bitmap.
     * @param isBackSide    True when the scanned side is the reverse.
     * @param unrecognisedSink  Optional callback to report unreadable documents.
     */
    suspend fun process(
        fullText: String,
        visionText: Text?,
        ocrMetricDao: OcrMetricDao? = null,
        templates: List<DocumentTemplate> = emptyList(),
        barcode: BarcodeReader.BarcodeReadResult? = null,
        imageWidth: Int = 0,
        imageHeight: Int = 0,
        isBackSide: Boolean = false,
        unrecognisedSink: UnrecognisedSink? = null
    ): PipelineResult {

        log("=== Pipeline START (${fullText.length} chars, templates=${templates.size}, barcode=${barcode != null}) ===")

        if (fullText.isBlank() && barcode == null) {
            log("Empty OCR text and no barcode — returning empty result")
            return emptyResult(fullText)
        }

        val lines = fullText.split("\n").map { it.trim() }.filter { it.isNotBlank() }
        val aspectRatio = if (imageWidth > 0 && imageHeight > 0)
            imageWidth.toFloat() / imageHeight else null

        // ── STEP 1: Generic classification (fallback signal) ──────────────────
        val genericClassification = DocumentClassifier.classify(fullText)

        // ── STEP 2: MRZ ───────────────────────────────────────────────────────
        val mrzData = MrzParser.parse(fullText)
        val mrzReliable = mrzData != null && mrzData.isReliable
        val hasMrz = mrzData != null

        // ── STEP 3: Template identification (data-driven) ─────────────────────
        val templateMatch = TemplateMatcher.identify(
            templates   = templates,
            ocrText     = fullText,
            hasMrz      = hasMrz,
            hasBarcode  = barcode != null,
            aspectRatio = aspectRatio,
            isBackSide  = isBackSide
        )
        if (templateMatch != null) {
            log("STEP 3 template ✅ '${templateMatch.template.code}' score=${"%.2f".format(templateMatch.score)}")
        }

        // Prefer template-derived classification over the hardcoded classifier.
        val classification = templateMatch?.let { m ->
            DocumentClassification(
                country      = m.template.countryId ?: genericClassification.country,
                documentType = (m.template.documentKind ?: m.template.code ?: genericClassification.documentType).uppercase(),
                confidence   = m.score,
                signals      = m.signals + "template:${m.template.code}"
            )
        } ?: genericClassification

        // ── STEP 4: Barcode fields — ONLY from AAMVA (US/CA standard) ──────────
        // Non-AAMVA barcodes (e.g. the SV DUI, which carries only verification
        // data) never fill fields — those documents are read via OCR templates.
        // Their raw content is still captured for the failure report below.
        val aamva = barcode?.takeIf { it.parser == BarcodeReader.ParserType.AAMVA }
        val bcFn  = aamva?.firstName?.let      { field(it, BARCODE_CONFIDENCE, FieldSource.BARCODE) }
        val bcLn  = aamva?.lastName?.let       { field(it, BARCODE_CONFIDENCE, FieldSource.BARCODE) }
        val bcDoc = aamva?.documentNumber?.let { field(it, BARCODE_CONFIDENCE, FieldSource.BARCODE) }

        // ── STEP 5: Template field extraction (anchor/zone) ───────────────────
        val templateFields = templateMatch?.let {
            TemplateFieldExtractor.extract(it.template.fields, visionText, imageWidth, imageHeight)
        } ?: emptyMap()
        val tplFn  = templateFields["first_name"]?.let      { field(it.value, it.confidence, FieldSource.TEMPLATE) }
        val tplLn  = templateFields["last_name"]?.let       { field(it.value, it.confidence, FieldSource.TEMPLATE) }
        val tplDoc = templateFields["document_number"]?.let { field(it.value, it.confidence, FieldSource.TEMPLATE) }

        // ── STEP 6: MRZ fields ────────────────────────────────────────────────
        val mrzFn  = mrzData?.takeIf { mrzReliable }?.firstName?.ifBlank { null }?.let      { field(it, mrzData.confidence, FieldSource.MRZ) }
        val mrzLn  = mrzData?.takeIf { mrzReliable }?.lastName?.ifBlank { null }?.let       { field(it, mrzData.confidence, FieldSource.MRZ) }
        val mrzDoc = mrzData?.takeIf { mrzReliable }?.documentNumber?.ifBlank { null }?.let { field(it, mrzData.confidence, FieldSource.MRZ) }

        // ── STEP 7: Entity + generic scoring (FALLBACK) ───────────────────────
        val entities = try {
            EntityExtractionAdapter.extractEntities(fullText)
        } catch (e: Exception) {
            EntityExtractionAdapter.EntityExtractionResult.EMPTY
        }
        val genFnRaw  = FieldScoringEngine.scoreFirstName(lines, classification)
        val genLnRaw  = FieldScoringEngine.scoreLastName(lines, classification)
        val genDoc    = FieldScoringEngine.scoreDocumentNumber(lines, classification)
        val genFn = penaliseIfNonName(genFnRaw, entities.nonNameLines)
        val genLn = penaliseIfNonName(genLnRaw, entities.nonNameLines)

        // Legacy heuristic fallback (lowest priority).
        val legacyFn  = if (genFn.value.isNullOrBlank())  legacyFallbackName(fullText, isFirst = true)  else null
        val legacyLn  = if (genLn.value.isNullOrBlank())  legacyFallbackName(fullText, isFirst = false) else null
        val legacyDoc = if (genDoc.value.isNullOrBlank()) legacyFallbackDoc(fullText) else null

        // ── STEP 8: Priority merge  (BARCODE > MRZ > TEMPLATE > generic > legacy)
        val fnFinal  = firstUsable(bcFn, mrzFn, tplFn, genFn, legacyFn)
        val lnFinal  = firstUsable(bcLn, mrzLn, tplLn, genLn, legacyLn)
        val docFinal = firstUsable(bcDoc, mrzDoc, tplDoc, genDoc, legacyDoc)

        log("STEP 8 merged → fn='${fnFinal.value}'(${fnFinal.source}) ln='${lnFinal.value}'(${lnFinal.source}) doc='${docFinal.value}'(${docFinal.source})")

        // ── STEP 9: Failure reporting ─────────────────────────────────────────
        maybeReportFailure(
            templates      = templates,
            templateMatch  = templateMatch,
            barcode        = barcode,
            classification = classification,
            fnFinal        = fnFinal,
            lnFinal        = lnFinal,
            docFinal       = docFinal,
            sink           = unrecognisedSink
        )

        // ── STEP 10: Metrics ──────────────────────────────────────────────────
        val metricId = ocrMetricDao?.let {
            MetricsLogger.logScan(it, classification, fnFinal, lnFinal, docFinal, fullText, hasMrz = mrzReliable)
        } ?: -1L

        log("=== Pipeline END ===")

        return PipelineResult(
            firstName       = fnFinal,
            lastName        = lnFinal,
            documentNumber  = docFinal,
            classification  = classification,
            mrzData         = mrzData,
            fullOcrText     = fullText,
            metricId        = metricId,
            matchedTemplate = templateMatch?.template,
            barcode         = barcode
        )
    }

    // ─── Failure reporting decision ───────────────────────────────────────────

    private suspend fun maybeReportFailure(
        templates: List<DocumentTemplate>,
        templateMatch: TemplateMatcher.Match?,
        barcode: BarcodeReader.BarcodeReadResult?,
        classification: DocumentClassification,
        fnFinal: ExtractedField,
        lnFinal: ExtractedField,
        docFinal: ExtractedField,
        sink: UnrecognisedSink?
    ) {
        if (sink == null) return

        val unknownBarcode = barcode?.isUnknown == true
        val lowConfidence = !fnFinal.isAutoFillable && !docFinal.isAutoFillable && !lnFinal.isAutoFillable
        // Only report "no template" when a catalog actually exists (avoid spamming
        // when offline / never synced). Always capture unknown barcodes.
        val noTemplateWithCatalog = templates.isNotEmpty() && templateMatch == null

        val shouldReport = unknownBarcode ||
            noTemplateWithCatalog ||
            (templates.isNotEmpty() && lowConfidence)

        if (!shouldReport) return

        log("STEP 9 reporting unrecognised (unknownBarcode=$unknownBarcode noTemplate=$noTemplateWithCatalog lowConf=$lowConfidence)")
        sink.onUnrecognised(
            classification.documentType.takeIf { it != "UNKNOWN" },
            classification.confidence,
            if (unknownBarcode) barcode?.rawValue else null
        )
    }

    // ─── Merge / field helpers ─────────────────────────────────────────────────

    /** First candidate with a non-blank value, in priority order. Empty if none. */
    private fun firstUsable(vararg options: ExtractedField?): ExtractedField {
        for (o in options) if (o != null && !o.value.isNullOrBlank()) return o
        return ExtractedField.EMPTY
    }

    private fun field(value: String, confidence: Float, source: FieldSource): ExtractedField =
        ExtractedField(value = value, confidence = confidence, source = source)

    // ─── Fallback to legacy extractor logic ───────────────────────────────────

    private fun legacyFallbackName(text: String, isFirst: Boolean): ExtractedField {
        val result = DocumentDataExtractor.extractFromText(text, null)
        val value  = if (isFirst) result.firstName else result.lastName
        if (value.isNullOrBlank()) return ExtractedField.EMPTY
        return ExtractedField(value, 0.55f, FieldSource.HEURISTIC)
    }

    private fun legacyFallbackDoc(text: String): ExtractedField {
        val num = DocumentDataExtractor.extractDocumentNumber(text) ?: return ExtractedField.EMPTY
        return ExtractedField(num, 0.50f, FieldSource.HEURISTIC)
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /** If the field value appears in entity non-name lines, penalise confidence by 0.20. */
    private fun penaliseIfNonName(field: ExtractedField, nonNameLines: Set<String>): ExtractedField {
        if (field.value == null) return field
        val isNonName = nonNameLines.any { field.value.contains(it, ignoreCase = true) }
        if (!isNonName) return field
        val penalised = (field.confidence - 0.20f).coerceAtLeast(0f)
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
