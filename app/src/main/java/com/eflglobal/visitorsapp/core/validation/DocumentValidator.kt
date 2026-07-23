package com.eflglobal.visitorsapp.core.validation

import android.content.Context
import android.graphics.Bitmap
import com.eflglobal.visitorsapp.core.DependencyProvider
import com.eflglobal.visitorsapp.core.ocr.BarcodeReader
import com.eflglobal.visitorsapp.core.ocr.DocumentDataExtractor
import com.eflglobal.visitorsapp.core.ocr.DocumentProcessingPipeline
import com.eflglobal.visitorsapp.data.repository.FailedDocumentReporter
import com.eflglobal.visitorsapp.data.repository.TemplateRepository
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * ═══════════════════════════════════════════════════════════════════════════════
 * DocumentValidator — Central validation engine for identity documents.
 *
 * Validation pipeline (applied sequentially, fails fast):
 *
 *   STEP 1 — Sharpness Gate (Laplacian variance on full-res bitmap)
 *            Rejects blurry images before any expensive processing.
 *
 *   STEP 2 — Document Crop
 *            Crops the central region matching the orange guide frame (70% width,
 *            1.6:1 aspect ratio), discarding background noise.
 *
 *   STEP 3 — Post-crop Sharpness Re-check
 *            Second Laplacian pass on the cropped region (stricter threshold).
 *
 *   STEP 4 — OCR Extraction (ML Kit)
 *            Extracts all text, name, document number, date of birth.
 *
 *   STEP 5 — Document Type Validation
 *            Checks for identity-document-exclusive keywords and numeric patterns.
 *            Front and back side each have tailored keyword sets.
 *
 *   STEP 6 — Duplicate-side Detection (back side only)
 *            Double-check: OCR Jaccard similarity + perceptual hash (pHash).
 *            Both must agree (or fallback gracefully) before accepting.
 *
 * Every step produces a typed result so callers can show granular feedback.
 * ═══════════════════════════════════════════════════════════════════════════════
 */
object DocumentValidator {

    // ─── Thresholds ───────────────────────────────────────────────────────────

    /** Minimum Laplacian-variance sharpness on the RAW captured bitmap. */
    const val RAW_SHARPNESS_MIN = 10f

    /**
     * Minimum Laplacian-variance sharpness on the CROPPED document region.
     * Kept at/below [RAW_SHARPNESS_MIN] so the crop gate isn't stricter than the
     * raw gate — softer-but-readable captures are accepted (templates / MRZ /
     * barcode make the pipeline robust to moderate blur).
     */
    const val CROP_SHARPNESS_MIN = 13f

    /** Minimum OCR character count to consider a side "readable". */
    const val OCR_MIN_CHARS = 12

    /** Jaccard similarity threshold for OCR duplicate detection. */
    const val JACCARD_DUPLICATE_THRESHOLD = 0.50f

    /** pHash Hamming distance threshold (lower = stricter). */
    const val PHASH_DUPLICATE_THRESHOLD = 10

    // ─── Result types ─────────────────────────────────────────────────────────

    /** Overall result of the full validation pipeline. */
    sealed class ValidationResult {
        /** Document passed all checks. */
        data class Accepted(
            val croppedBitmap: Bitmap,
            val ocrData: OcrData
        ) : ValidationResult()

        /** A specific step failed — carries a user-facing message. */
        data class Rejected(
            val step: ValidationStep,
            val reason: String,       // internal key for logging
            val userMessage: String   // localised, shown in UI
        ) : ValidationResult()
    }

    enum class ValidationStep {
        RAW_SHARPNESS,
        CROP,
        CROP_SHARPNESS,
        OCR,
        DOCUMENT_TYPE,
        DUPLICATE_SIDE
    }

    /** Structured OCR extraction result. */
    data class OcrData(
        val fullText: String,
        val detectedName: String?,
        val firstName: String?,
        val lastName: String?,
        val detectedDocNumber: String?,
        val detectedDob: String?,
        val lineCount: Int,
        val wordCount: Int,
        val charCount: Int,
        val extractionSource: DocumentDataExtractor.ExtractionSource =
            DocumentDataExtractor.ExtractionSource.NONE,
        val extractionConfidence: DocumentDataExtractor.Confidence =
            DocumentDataExtractor.Confidence.NONE,
        /** Full pipeline result — available for per-field confidence access and metrics. */
        val pipelineResult: DocumentProcessingPipeline.PipelineResult? = null
    )

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * All user-facing error messages used by the validation pipeline.
     * Build this in the UI layer with `stringResource` so the system locale is
     * respected automatically — no hardcoded strings inside the validator.
     */
    data class ValidationMessages(
        val rawBlurry:      String,
        val cropFailed:     String,
        val cropBlurry:     String,
        val noText:         String,
        val notDocument:    String,
        val notRecognised:  String,
        val duplicateSide:  String
    )

    /**
     * Runs the full validation pipeline on [rawBitmap].
     *
     * @param rawBitmap       Full-resolution bitmap from the camera.
     * @param isBackSide      True when scanning the back/reverse side.
     * @param referenceBitmap Previously accepted front-side bitmap (for dup check).
     * @param messages        Localised error strings — build from stringResource in the UI.
     * @param appContext      When provided, enables OCR-template extraction, barcode
     *                        reading and failure reporting. Null → generic pipeline only.
     */
    suspend fun validate(
        rawBitmap: Bitmap,
        isBackSide: Boolean = false,
        referenceBitmap: Bitmap? = null,
        messages: ValidationMessages,
        appContext: Context? = null
    ): ValidationResult {

        // ── STEP 1: Raw sharpness ─────────────────────────────────────────────
        val rawSharpness = calculateLaplacianVariance(rawBitmap)
        log("STEP 1 — raw sharpness: $rawSharpness (min=$RAW_SHARPNESS_MIN)")

        if (rawSharpness < RAW_SHARPNESS_MIN) {
            return ValidationResult.Rejected(
                step        = ValidationStep.RAW_SHARPNESS,
                reason      = "raw_blurry(${"%.1f".format(rawSharpness)})",
                userMessage = messages.rawBlurry
            )
        }

        // ── STEP 2: Crop document region ──────────────────────────────────────
        val cropped = try {
            cropDocumentRegion(rawBitmap)
        } catch (e: Exception) {
            log("STEP 2 — crop failed: ${e.message}")
            return ValidationResult.Rejected(
                step        = ValidationStep.CROP,
                reason      = "crop_failed",
                userMessage = messages.cropFailed
            )
        }
        log("STEP 2 — crop OK: ${cropped.width}×${cropped.height}")

        // ── STEP 3: Cropped sharpness re-check ───────────────────────────────
        val cropSharpness = calculateLaplacianVariance(cropped)
        log("STEP 3 — crop sharpness: $cropSharpness (min=$CROP_SHARPNESS_MIN)")

        if (cropSharpness < CROP_SHARPNESS_MIN) {
            return ValidationResult.Rejected(
                step        = ValidationStep.CROP_SHARPNESS,
                reason      = "crop_blurry(${"%.1f".format(cropSharpness)})",
                userMessage = messages.cropBlurry
            )
        }

        // ── STEP 4: OCR extraction ────────────────────────────────────────────
        val ocrData = try {
            runOcr(cropped, appContext, isBackSide)
        } catch (e: Exception) {
            log("STEP 4 — OCR failed: ${e.message}")
            // OCR failure is non-blocking — proceed with empty data
            OcrData(
                fullText             = "",
                detectedName         = null,
                firstName            = null,
                lastName             = null,
                detectedDocNumber    = null,
                detectedDob          = null,
                lineCount            = 0,
                wordCount            = 0,
                charCount            = 0,
                extractionSource     = DocumentDataExtractor.ExtractionSource.NONE,
                extractionConfidence = DocumentDataExtractor.Confidence.NONE
            )
        }
        log("STEP 4 — OCR: chars=${ocrData.charCount} lines=${ocrData.lineCount} name=${ocrData.detectedName} docNum=${ocrData.detectedDocNumber}")

        // ── STEP 5: Document-type validation ─────────────────────────────────
        val docCheck = checkDocumentType(ocrData, isBackSide)
        log("STEP 5 — docType: isDoc=${docCheck.isDocument} score=${docCheck.score} reason=${docCheck.reason}")

        if (!docCheck.isDocument) {
            val msg = when (docCheck.reason) {
                "no_text"      -> messages.noText
                "not_document" -> messages.notDocument
                else           -> messages.notRecognised
            }
            return ValidationResult.Rejected(
                step        = ValidationStep.DOCUMENT_TYPE,
                reason      = "doc_type:${docCheck.reason}",
                userMessage = msg
            )
        }

        // ── STEP 6: Duplicate-side check (back side only) ─────────────────────
        if (isBackSide && referenceBitmap != null) {
            val dupResult = checkDuplicateSide(
                frontBitmap = referenceBitmap,
                backBitmap  = cropped,
                ocrData     = ocrData
            )
            log("STEP 6 — dup: isDup=${dupResult.isDuplicate} jaccardSim=${dupResult.jaccardSimilarity} pHashDist=${dupResult.pHashDistance} reason=${dupResult.reason}")

            if (dupResult.isDuplicate) {
                return ValidationResult.Rejected(
                    step        = ValidationStep.DUPLICATE_SIDE,
                    reason      = "duplicate:${dupResult.reason}",
                    userMessage = messages.duplicateSide
                )
            }
        }

        // ── ALL STEPS PASSED ──────────────────────────────────────────────────
        log("✅ Document accepted — sharpness(raw=$rawSharpness crop=$cropSharpness) ocrChars=${ocrData.charCount}")
        return ValidationResult.Accepted(
            croppedBitmap = cropped,
            ocrData       = ocrData
        )
    }

    // ─── STEP 2: Document Crop ────────────────────────────────────────────────

    /**
     * Crops the central document region from [bitmap].
     *
     * CameraX with ROTATION_90 delivers landscape bitmaps (width > height).
     * If portrait is received, it is rotated to landscape first.
     *
     * The crop matches the on-screen guide frame: 70% of screen width at 1.6:1 AR.
     * Mapped to image coordinates: 55% of image width, centred.
     */
    fun cropDocumentRegion(bitmap: Bitmap): Bitmap {
        // Normalise to landscape
        val src = if (bitmap.height > bitmap.width) {
            val m = android.graphics.Matrix().apply { postRotate(90f) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
        } else {
            bitmap
        }

        val imgW = src.width.toFloat()
        val imgH = src.height.toFloat()

        // 65% of image width — generous crop so field labels near edges are not cut.
        // (was 55%, increased to capture more of the document area)
        val cropW      = (imgW * 0.65f).toInt()
        val cropH      = minOf((cropW / 1.6f).toInt(), (imgH * 0.88f).toInt())
        val finalCropW = (cropH * 1.6f).toInt().coerceAtMost(cropW)

        val left  = ((imgW - finalCropW) / 2f).toInt().coerceAtLeast(0)
        val top   = ((imgH - cropH)      / 2f).toInt().coerceAtLeast(0)
        val safeW = finalCropW.coerceAtMost(src.width  - left)
        val safeH = cropH.coerceAtMost(src.height - top)

        val cropped = Bitmap.createBitmap(src, left, top, safeW, safeH)
        if (src !== bitmap) src.recycle()

        log("✂️ Crop: ${bitmap.width}×${bitmap.height} → ${cropped.width}×${cropped.height}")
        return cropped
    }

    // ─── STEP 1 & 3: Sharpness ───────────────────────────────────────────────

    /**
     * Computes the Laplacian variance of [bitmap] as a sharpness score.
     * Higher = sharper.  Sampled every other pixel for performance.
     */
    fun calculateLaplacianVariance(bitmap: Bitmap): Float {
        val w      = bitmap.width
        val h      = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        var sum   = 0.0
        var sumSq = 0.0
        var count = 0

        val step = 2 // sample every 2nd pixel — fast, accurate enough
        for (y in step until h - step step step) {
            for (x in step until w - step step step) {
                val idx = y * w + x
                fun gray(px: Int) = ((px shr 16 and 0xFF) * 0.299
                        + (px shr 8  and 0xFF) * 0.587
                        + (px        and 0xFF) * 0.114)

                val c  = gray(pixels[idx])
                val t  = gray(pixels[(y - step) * w + x])
                val b  = gray(pixels[(y + step) * w + x])
                val l  = gray(pixels[y * w + (x - step)])
                val r  = gray(pixels[y * w + (x + step)])
                val lap = abs(4 * c - t - b - l - r)

                sum   += lap
                sumSq += lap * lap
                count++
            }
        }

        if (count == 0) return 0f
        val mean = sum / count
        return sqrt((sumSq / count) - (mean * mean)).toFloat()
    }

    // ─── STEP 4: OCR ─────────────────────────────────────────────────────────

    /**
     * Runs ML Kit OCR → DocumentProcessingPipeline (Classifier → MRZ → Template →
     * Barcode → Scoring → Metrics) and returns structured [OcrData].
     *
     * @param appContext  When provided, loads the OCR template catalog, reads the
     *                    barcode/QR on the (back) side and reports unreadable docs.
     * @param isBackSide  True when scanning the reverse — where AAMVA/PDF417 live.
     */
    suspend fun runOcr(
        bitmap: Bitmap,
        appContext: Context? = null,
        isBackSide: Boolean = false
    ): OcrData =
        suspendCoroutine { cont ->
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        // Scale up if image is small — ML Kit works best at 1080p+
        val processedBitmap = if (bitmap.width < 800 || bitmap.height < 500) {
            val scale = maxOf(800f / bitmap.width, 500f / bitmap.height)
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width  * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else bitmap

        recognizer.process(InputImage.fromBitmap(processedBitmap, 0))
            .addOnSuccessListener { visionText ->
                val fullText = visionText.text
                val lineCount = visionText.textBlocks.sumOf { it.lines.size }
                val wordCount = fullText.trim().split(Regex("\\s+")).count { it.length >= 2 }
                val charCount = fullText.trim().length

                // Capture OCR image dims (visionText boxes live in this space) BEFORE
                // the scaled bitmap is recycled — used for zone/aspect + block reporting.
                val procW = processedBitmap.width
                val procH = processedBitmap.height

                log("STEP 4 OCR — ${procW}×${procH} → chars=$charCount lines=$lineCount")
                log("STEP 4 OCR full text: ${fullText.take(400)}")

                if (processedBitmap !== bitmap) processedBitmap.recycle()

                // Pipeline is suspend — launch an IO coroutine and resume the continuation when done
                CoroutineScope(Dispatchers.IO).launch {
                    // Offline-first: everything below degrades to empty/null on failure.
                    val templates = appContext?.let {
                        runCatching { TemplateRepository.get(it).getTemplates() }.getOrDefault(emptyList())
                    } ?: emptyList()

                    // Read the barcode/QR off the ORIGINAL cropped bitmap (not the
                    // recycled scaled copy). Most relevant on the back side.
                    val barcode = runCatching { BarcodeReader.read(bitmap) }.getOrNull()

                    val dao = appContext?.let { DependencyProvider.provideOcrMetricDao(it) }

                    val sink = appContext?.let { ctx ->
                        DocumentProcessingPipeline.UnrecognisedSink { type, conf, raw ->
                            FailedDocumentReporter.report(
                                ctx, type, conf, visionText, procW, procH, raw
                            )
                        }
                    }

                    val result = DocumentProcessingPipeline.process(
                        fullText         = fullText,
                        visionText       = visionText,
                        ocrMetricDao     = dao,
                        templates        = templates,
                        barcode          = barcode,
                        imageWidth       = procW,
                        imageHeight      = procH,
                        isBackSide       = isBackSide,
                        unrecognisedSink = sink
                    )

                    val detectedName = listOfNotNull(
                        result.autoFirstName,
                        result.autoLastName
                    ).joinToString(" ").ifBlank { null }

                    cont.resume(OcrData(
                        fullText             = fullText,
                        detectedName         = detectedName,
                        firstName            = result.firstName.value,
                        lastName             = result.lastName.value,
                        detectedDocNumber    = result.documentNumber.value,
                        detectedDob          = null,
                        lineCount            = lineCount,
                        wordCount            = wordCount,
                        charCount            = charCount,
                        extractionSource     = mapFieldSource(result.firstName.source),
                        extractionConfidence = mapConfidence(result.firstName.confidence),
                        pipelineResult       = result
                    ))
                }
            }
            .addOnFailureListener { e ->
                log("STEP 4 OCR FAILED: ${e.message}")
                if (processedBitmap !== bitmap) processedBitmap.recycle()
                cont.resume(OcrData(
                    fullText             = "",
                    detectedName         = null,
                    firstName            = null,
                    lastName             = null,
                    detectedDocNumber    = null,
                    detectedDob          = null,
                    lineCount            = 0,
                    wordCount            = 0,
                    charCount            = 0,
                    extractionSource     = DocumentDataExtractor.ExtractionSource.NONE,
                    extractionConfidence = DocumentDataExtractor.Confidence.NONE,
                    pipelineResult       = null
                ))
            }
    }

    /** Map new FieldSource → legacy ExtractionSource for backward-compat display. */
    private fun mapFieldSource(
        source: com.eflglobal.visitorsapp.domain.model.FieldSource
    ): DocumentDataExtractor.ExtractionSource = when (source) {
        // Barcode is the most reliable machine-readable source — map alongside MRZ.
        com.eflglobal.visitorsapp.domain.model.FieldSource.BARCODE    -> DocumentDataExtractor.ExtractionSource.MRZ
        com.eflglobal.visitorsapp.domain.model.FieldSource.MRZ        -> DocumentDataExtractor.ExtractionSource.MRZ
        com.eflglobal.visitorsapp.domain.model.FieldSource.TEMPLATE,
        com.eflglobal.visitorsapp.domain.model.FieldSource.LABEL_OCR  -> DocumentDataExtractor.ExtractionSource.OCR_KEYED
        com.eflglobal.visitorsapp.domain.model.FieldSource.ENTITY,
        com.eflglobal.visitorsapp.domain.model.FieldSource.HEURISTIC  -> DocumentDataExtractor.ExtractionSource.OCR_HEURISTIC
        com.eflglobal.visitorsapp.domain.model.FieldSource.NONE       -> DocumentDataExtractor.ExtractionSource.NONE
    }

    /** Map numeric confidence → legacy Confidence enum. */
    private fun mapConfidence(conf: Float): DocumentDataExtractor.Confidence = when {
        conf >= 0.80f -> DocumentDataExtractor.Confidence.HIGH
        conf >= 0.60f -> DocumentDataExtractor.Confidence.MEDIUM
        conf >= 0.30f -> DocumentDataExtractor.Confidence.LOW
        else          -> DocumentDataExtractor.Confidence.NONE
    }

    // ─── STEP 5: Document-type validation ────────────────────────────────────

    data class DocTypeResult(
        val isDocument: Boolean,
        val score: Int,
        val reason: String
    )

    /**
     * Exclusive front-side keywords — words that appear on ID documents but
     * almost never on laptops, posters, or random objects.
     */
    private val FRONT_KEYWORDS = setOf(
        // Spanish
        "nombres", "apellidos", "apellido", "nacimiento", "vencimiento",
        "emisión", "emision", "identidad", "identificacion", "identificación",
        "dui", "nacionalidad", "ministerio", "república", "republica",
        "salvadoreño", "salvadorena", "cedula", "licencia de conducir",
        // English / international
        "surname", "forename", "given name", "given names", "first name",
        "nationality", "expiry", "expiration", "date of birth",
        "passport no", "passport number", "identity card", "id card",
        "driver licence", "driver license", "drivers license",
        "place of birth", "sex", "issuing authority",
        // MRZ
        "<<<"
    )

    /**
     * Exclusive back-side keywords.
     */
    private val BACK_KEYWORDS = setOf(
        // Address / residence
        "domicilio", "dirección", "direccion", "address", "residencia",
        // Administrative divisions
        "municipio", "departamento", "canton", "cantón", "barrio", "colonia",
        "ciudad", "distrito", "provincia",
        // Back-side specific fields
        "profesión", "profesion", "ocupacion", "ocupación", "profession",
        "estatura", "height", "talla",
        "firma", "signature", "huella",
        "estado civil", "civil status", "marital status",
        "lugar de nacimiento", "birthplace",
        "emisión", "emision", "issued", "expedido",
        "vencimiento", "expiry", "expiration",
        "código", "codigo", "code", "barcode",
        "ministerio", "registro", "civil", "republic", "republica",
        "<<<"
    )

    /** Numeric / alphanumeric patterns that strongly suggest an ID document. */
    private val DOC_PATTERNS = listOf(
        Regex("""\b\d{8}-\d\b"""),             // DUI: 12345678-9
        Regex("""\b\d{9}\b"""),                 // DUI no hyphen
        Regex("""\b[A-Z]{1,2}\d{6,8}\b"""),    // Passport: PA123456
        Regex("""\b\d{2}[/.-]\d{2}[/.-]\d{4}\b"""), // Dates
        Regex("""\b\d{4}[/.-]\d{2}[/.-]\d{2}\b"""), // ISO dates
        Regex("""[A-Z0-9<]{30,44}""")           // MRZ line
    )

    private fun checkDocumentType(ocrData: OcrData, isBackSide: Boolean): DocTypeResult {
        val text  = ocrData.fullText
        val lower = text.lowercase()

        // Minimum text requirement
        if (ocrData.charCount < 8 || ocrData.wordCount < 2) {
            return DocTypeResult(isDocument = false, score = 0, reason = "no_text")
        }

        val keywords = if (isBackSide) BACK_KEYWORDS else FRONT_KEYWORDS
        val keyHits  = keywords.count { lower.contains(it) }
        val patHits  = DOC_PATTERNS.count { it.containsMatchIn(text) }

        // Score: each keyword = 2pts, each pattern = 3pts
        val score = keyHits * 2 + patHits * 3

        log("  docType score=$score keyHits=$keyHits patHits=$patHits isBack=$isBackSide")

        return if (isBackSide) {
            // Back side: more permissive.
            // Accept if score≥2, OR has enough structured text (≥4 lines, ≥5 words)
            val accepted = score >= 2 || (ocrData.lineCount >= 4 && ocrData.wordCount >= 5)
            DocTypeResult(
                isDocument = accepted,
                score      = score,
                reason     = if (accepted) "ok" else "not_document"
            )
        } else {
            // Front side: strict.
            // Require score≥4 (e.g. 2 keywords, or 1 keyword + 1 pattern)
            val accepted = score >= 4
            DocTypeResult(
                isDocument = accepted,
                score      = score,
                reason     = if (accepted) "ok" else "not_document"
            )
        }
    }

    // ─── STEP 6: Duplicate-side detection ────────────────────────────────────

    data class DupCheckResult(
        val isDuplicate: Boolean,
        val jaccardSimilarity: Float,
        val pHashDistance: Int,
        val reason: String   // "jaccard" | "phash" | "both" | "not_duplicate" | "insufficient_text"
    )

    /**
     * Double-checks that [backBitmap] is NOT the same side as [frontBitmap].
     *
     * Method A — OCR Jaccard similarity (text-based, very reliable when both
     *   sides have enough text; short-circuits to NOT-duplicate when either side
     *   has < MIN_TOKENS tokens).
     *
     * Method B — Perceptual hash (pixel-based, works even when OCR yields little
     *   text).
     *
     * Decision logic:
     *   • If Method A has sufficient text:
     *       - If Jaccard ≥ threshold → DUPLICATE (confirmed by text content)
     *       - If Jaccard < threshold → NOT duplicate (text differs)
     *   • If Method A has insufficient text:
     *       - Fall back to Method B exclusively.
     */
    private suspend fun checkDuplicateSide(
        frontBitmap: Bitmap,
        backBitmap: Bitmap,
        ocrData: OcrData   // already computed OCR for backBitmap — reuse it
    ): DupCheckResult {

        // ── Method A: Jaccard OCR similarity ─────────────────────────────────
        val frontText   = extractRawOcrText(frontBitmap)
        val frontTokens = tokenize(frontText)
        val backTokens  = tokenize(ocrData.fullText)

        val MIN_TOKENS = 6
        val hasEnoughText = frontTokens.size >= MIN_TOKENS && backTokens.size >= MIN_TOKENS

        val jaccard = if (hasEnoughText) {
            val inter = frontTokens.intersect(backTokens).size.toFloat()
            val union = frontTokens.union(backTokens).size.toFloat()
            if (union == 0f) 0f else inter / union
        } else 0f

        log("  dup A — frontTokens=${frontTokens.size} backTokens=${backTokens.size} jaccard=${"%.3f".format(jaccard)}")

        if (hasEnoughText) {
            // Jaccard is definitive when text is sufficient
            val isDup = jaccard >= JACCARD_DUPLICATE_THRESHOLD
            return DupCheckResult(
                isDuplicate        = isDup,
                jaccardSimilarity  = jaccard,
                pHashDistance      = -1,
                reason             = if (isDup) "jaccard" else "not_duplicate"
            )
        }

        // ── Method B: pHash fallback ──────────────────────────────────────────
        val pDist = pHashDistance(frontBitmap, backBitmap)
        log("  dup B — pHash distance=$pDist (threshold=$PHASH_DUPLICATE_THRESHOLD)")
        val isDupByHash = pDist <= PHASH_DUPLICATE_THRESHOLD

        return DupCheckResult(
            isDuplicate        = isDupByHash,
            jaccardSimilarity  = jaccard,
            pHashDistance      = pDist,
            reason             = if (isDupByHash) "phash" else "not_duplicate"
        )
    }

    // ─── OCR helpers ─────────────────────────────────────────────────────────

    private suspend fun extractRawOcrText(bitmap: Bitmap): String =
        suspendCoroutine { cont ->
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                .process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { cont.resume(it.text) }
                .addOnFailureListener { cont.resume("") }
        }

    private fun tokenize(text: String): Set<String> =
        text.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 3 }
            .toSet()


    // ─── pHash ────────────────────────────────────────────────────────────────

    /**
     * Computes the Hamming distance between the 64-bit pHashes of [a] and [b].
     * Both are cropped to the document region before hashing to remove background.
     * Distance 0 = identical, 64 = completely different.
     */
    private fun pHashDistance(a: Bitmap, b: Bitmap): Int {
        return try {
            val ha = computePHash(cropDocumentRegion(a))
            val hb = computePHash(cropDocumentRegion(b))
            java.lang.Long.bitCount(ha[0] xor hb[0]) +
            java.lang.Long.bitCount(ha[1] xor hb[1])
        } catch (e: Exception) {
            log("pHash error: ${e.message}")
            64 // maximum distance = not similar
        }
    }

    private fun computePHash(bitmap: Bitmap): LongArray {
        val size  = 8
        val small = Bitmap.createScaledBitmap(bitmap, size, size, true)
        val px    = IntArray(size * size)
        small.getPixels(px, 0, size, 0, 0, size, size)
        small.recycle()

        val grays = px.map { p ->
            ((p shr 16 and 0xFF) * 0.299 +
             (p shr  8 and 0xFF) * 0.587 +
             (p        and 0xFF) * 0.114).toInt()
        }
        val avg = grays.average()

        var hi = 0L; var lo = 0L
        for (i in 0 until 64) {
            if (grays[i] >= avg) {
                if (i < 32) hi = hi or (1L shl i)
                else        lo = lo or (1L shl (i - 32))
            }
        }
        return longArrayOf(hi, lo)
    }

    // ─── Utility ──────────────────────────────────────────────────────────────

    private fun log(msg: String) = println("📋 DocValidator | $msg")
}

