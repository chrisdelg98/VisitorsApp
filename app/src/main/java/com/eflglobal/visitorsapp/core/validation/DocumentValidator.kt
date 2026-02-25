package com.eflglobal.visitorsapp.core.validation

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
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
    const val RAW_SHARPNESS_MIN = 15f

    /** Minimum Laplacian-variance sharpness on the CROPPED document region. */
    const val CROP_SHARPNESS_MIN = 20f

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
        val detectedDocNumber: String?,
        val detectedDob: String?,
        val lineCount: Int,
        val wordCount: Int,
        val charCount: Int
    )

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Runs the full validation pipeline on [rawBitmap].
     *
     * @param rawBitmap      Full-resolution bitmap from the camera.
     * @param isBackSide     True when scanning the back/reverse side.
     * @param referenceBitmap Previously accepted front-side bitmap (for dup check).
     * @param lang           "es" or "en" for user-facing messages.
     */
    suspend fun validate(
        rawBitmap: Bitmap,
        isBackSide: Boolean = false,
        referenceBitmap: Bitmap? = null,
        lang: String = "es"
    ): ValidationResult {

        // ── STEP 1: Raw sharpness ─────────────────────────────────────────────
        val rawSharpness = calculateLaplacianVariance(rawBitmap)
        log("STEP 1 — raw sharpness: $rawSharpness (min=$RAW_SHARPNESS_MIN)")

        if (rawSharpness < RAW_SHARPNESS_MIN) {
            return ValidationResult.Rejected(
                step        = ValidationStep.RAW_SHARPNESS,
                reason      = "raw_blurry(${"%.1f".format(rawSharpness)})",
                userMessage = if (lang == "es")
                    "Imagen borrosa.\nMantenga el documento quieto e intente de nuevo."
                else
                    "Blurry image.\nKeep the document still and try again."
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
                userMessage = if (lang == "es")
                    "Error al recortar el documento.\nIntente de nuevo."
                else
                    "Could not crop the document.\nPlease try again."
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
                userMessage = if (lang == "es")
                    "El documento no está nítido.\nAcerque y estabilice el documento."
                else
                    "Document is not sharp enough.\nHold it steady and closer."
            )
        }

        // ── STEP 4: OCR extraction ────────────────────────────────────────────
        val ocrData = try {
            runOcr(cropped)
        } catch (e: Exception) {
            log("STEP 4 — OCR failed: ${e.message}")
            // OCR failure is non-blocking — proceed with empty data
            OcrData(
                fullText       = "",
                detectedName   = null,
                detectedDocNumber = null,
                detectedDob    = null,
                lineCount      = 0,
                wordCount      = 0,
                charCount      = 0
            )
        }
        log("STEP 4 — OCR: chars=${ocrData.charCount} lines=${ocrData.lineCount} name=${ocrData.detectedName} docNum=${ocrData.detectedDocNumber}")

        // ── STEP 5: Document-type validation ─────────────────────────────────
        val docCheck = checkDocumentType(ocrData, isBackSide)
        log("STEP 5 — docType: isDoc=${docCheck.isDocument} score=${docCheck.score} reason=${docCheck.reason}")

        if (!docCheck.isDocument) {
            val msg = when (docCheck.reason) {
                "no_text" ->
                    if (lang == "es")
                        "No se detectó texto.\nCentre el documento dentro del marco."
                    else
                        "No text detected.\nCenter the document in the frame."
                "not_document" ->
                    if (lang == "es")
                        "No parece un documento de identidad.\nUse un DUI, pasaporte o licencia."
                    else
                        "Does not appear to be an ID document.\nUse a DUI, passport or licence."
                else ->
                    if (lang == "es")
                        "Documento no reconocido.\nAsegúrese de que sea legible."
                    else
                        "Document not recognised.\nMake sure it is legible."
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
                    userMessage = if (lang == "es")
                        "Parece el mismo lado del documento.\nVoltéelo y escanee el reverso."
                    else
                        "Looks like the same side of the document.\nFlip it and scan the back."
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

        // 55% of image width corresponds to the 70% guide-frame width
        // (camera FOV is wider than the guide frame)
        val cropW      = (imgW * 0.55f).toInt()
        val cropH      = minOf((cropW / 1.6f).toInt(), (imgH * 0.82f).toInt())
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
     * Runs ML Kit OCR on [bitmap] and returns structured [OcrData].
     */
    suspend fun runOcr(bitmap: Bitmap): OcrData = suspendCoroutine { cont ->
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { visionText ->
                val fullText  = visionText.text
                val lines     = visionText.textBlocks.sumOf { it.lines.size }
                val words     = fullText.trim().split(Regex("\\s+")).count { it.length >= 2 }
                val chars     = fullText.trim().length
                val name      = extractName(fullText, visionText)
                val docNumber = extractDocNumber(fullText)
                val dob       = extractDateOfBirth(fullText)
                cont.resume(OcrData(fullText, name, docNumber, dob, lines, words, chars))
            }
            .addOnFailureListener {
                cont.resume(OcrData("", null, null, null, 0, 0, 0))
            }
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

    /**
     * Extracts full name from OCR text.
     * Strategy: look for name-field keywords, then read the following lines.
     * Falls back to heuristic (all-letter lines ≥6 chars).
     */
    private fun extractName(fullText: String, visionText: Text): String? {
        val lines = fullText.split("\n").map { it.trim() }.filter { it.isNotBlank() }

        val nameKeywords  = listOf(
            "nombres", "nombre", "forename", "given name", "given names",
            "first name", "primer nombre", "prénom", "prenom"
        )
        val surnameKeywords = listOf(
            "apellidos", "apellido", "surname", "last name",
            "family name", "nom", "nom de famille"
        )

        fun findAfterKeyword(keywords: List<String>): String? {
            for (i in lines.indices) {
                if (keywords.any { lines[i].lowercase().contains(it) }) {
                    // Try same line (after the colon / keyword)
                    val inline = lines[i].replace(Regex("(?i)(${keywords.joinToString("|")})\\s*[:.]?\\s*"), "").trim()
                    if (isNameCandidate(inline)) return cleanName(inline)
                    // Try next 1–3 lines
                    for (j in 1..3) {
                        if (i + j < lines.size && isNameCandidate(lines[i + j])) {
                            return cleanName(lines[i + j])
                        }
                    }
                }
            }
            return null
        }

        val first  = findAfterKeyword(nameKeywords)
        val last   = findAfterKeyword(surnameKeywords)

        if (first != null && last != null) return cleanName("$first $last")
        if (first != null) return first
        if (last  != null) return last

        // Heuristic: long all-letter uppercase line
        for (line in lines) {
            if (isNameCandidate(line) && line.length >= 6 &&
                line == line.uppercase() && line.count { it.isLetter() } >= 4) {
                return cleanName(line)
            }
        }

        // Relaxed heuristic
        for (block in visionText.textBlocks) {
            for (ln in block.lines) {
                val t = ln.text.trim()
                if (isNameCandidate(t) && t.length >= 6) return cleanName(t)
            }
        }

        return null
    }

    private fun isNameCandidate(text: String): Boolean {
        if (text.length < 2) return false
        val letters = text.count { it.isLetter() }
        if (letters < text.length * 0.55) return false
        if (text.count { it.isDigit() } > 2) return false
        return text.all { it.isLetter() || it.isWhitespace() || it == '\'' || it == '-' || it == '.' || it == ',' }
    }

    private fun cleanName(name: String): String =
        name.trim()
            .replace(Regex("\\s+"), " ")
            .split(" ")
            .joinToString(" ") { w ->
                if (w.isNotEmpty()) w.lowercase().replaceFirstChar { it.uppercase() } else w
            }

    /**
     * Extracts document number using priority-ordered patterns + keyword search.
     */
    private fun extractDocNumber(text: String): String? {
        // P1: DUI salvadoreño — most specific, check first
        Regex("""\b(\d{8})-(\d)\b""").find(text)?.let { return it.value }

        // P2: DUI without hyphen
        Regex("""\b(\d{9})\b""").find(text)?.let { v ->
            val n = v.value; return "${n.take(8)}-${n[8]}"
        }

        // P3: Passport — one/two letters + 6-8 digits
        Regex("""\b([A-Z]{1,2}\d{6,8})\b""").find(text)?.let { return it.value }

        // P4: Generic ID — 6–15 digits (must be isolated)
        Regex("""\b(\d{7,15})\b""").find(text)?.let { return it.value }

        // P5: Keyword-guided search
        val keywordsId = listOf(
            "dui", "número único de identidad", "numero unico de identidad",
            "id number", "document number", "número de documento",
            "numero de identificacion", "identificacion", "passport no",
            "passport number", "cedula", "dni", "no.", "nº", "num"
        )
        val lines = text.split("\n")
        for (i in lines.indices) {
            if (keywordsId.any { lines[i].lowercase().contains(it) }) {
                val numOnSameLine = Regex("""[\d-]{6,}""").find(lines[i])?.value
                if (numOnSameLine != null) return numOnSameLine.replace(Regex("[^\\d-]"), "")
                for (j in 1..3) {
                    if (i + j < lines.size) {
                        val num = Regex("""[\d-]{6,}""").find(lines[i + j])?.value
                        if (num != null) return num.replace(Regex("[^\\d-]"), "")
                    }
                }
            }
        }

        return null
    }

    /** Attempts to extract a date of birth from OCR text. */
    private fun extractDateOfBirth(text: String): String? {
        val dobKeywords = listOf(
            "nacimiento", "fecha de nacimiento", "date of birth",
            "dob", "born", "f. nac"
        )
        val datePattern = Regex("""\b(\d{2}[/.\-]\d{2}[/.\-]\d{4}|\d{4}[/.\-]\d{2}[/.\-]\d{2})\b""")

        val lines = text.split("\n")
        for (i in lines.indices) {
            if (dobKeywords.any { lines[i].lowercase().contains(it) }) {
                // Same line
                datePattern.find(lines[i])?.let { return it.value }
                // Next 2 lines
                for (j in 1..2) {
                    if (i + j < lines.size) {
                        datePattern.find(lines[i + j])?.let { return it.value }
                    }
                }
            }
        }
        // Fallback: any date in the text
        return datePattern.find(text)?.value
    }

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

