package com.eflglobal.visitorsapp.core.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * ═══════════════════════════════════════════════════════════════════════════════
 * DocumentDataExtractor — Three-layer identity data extraction engine.
 *
 * Extraction pipeline (priority order):
 *
 *   LAYER 1 — MRZ (Machine Readable Zone)
 *     ISO 9303 TD1/TD2/TD3.  Works for passports and modern ID cards.
 *
 *   LAYER 2 — Labelled-field OCR
 *     Finds field labels (Nombres, Apellidos, Surname, etc.) and reads the
 *     value on the NEXT non-label line.  Bilingual label lines are handled
 *     correctly: "Apellidos / Surname" is treated as a pure label and the
 *     value is fetched from the following line.
 *
 *   LAYER 3 — Heuristic (no labels)
 *     For documents that print names without labels (many licences).
 *
 * If all layers fail → NONE (empty fields, manual entry).
 * ═══════════════════════════════════════════════════════════════════════════════
 */
object DocumentDataExtractor {

    // ─── Public result types ──────────────────────────────────────────────────

    enum class ExtractionSource { MRZ, OCR_KEYED, OCR_HEURISTIC, NONE }
    enum class Confidence { HIGH, MEDIUM, LOW, NONE }

    data class ExtractionResult(
        val firstName: String?,
        val lastName: String?,
        val documentNumber: String?,
        val dateOfBirth: String?,
        val nationality: String?,
        val mrzData: MrzParser.MrzData?,
        val source: ExtractionSource,
        val confidence: Confidence,
        val fullOcrText: String
    ) {
        val hasName: Boolean get() = !firstName.isNullOrBlank() || !lastName.isNullOrBlank()
        val isAutoFillReliable: Boolean get() =
            source == ExtractionSource.MRZ ||
            (source == ExtractionSource.OCR_KEYED && confidence == Confidence.HIGH)
    }

    // ─── All known label tokens (lower-case, normalised) ─────────────────────
    // These are the EXACT words/phrases that appear IN a label line.
    // A line that contains ANY of these is treated as a LABEL, never as a value.

    private val FIRST_NAME_LABELS = setOf(
        "nombres", "nombre", "primer nombre", "segundo nombre",
        "given names", "given name", "forenames", "forename",
        "first name", "first names", "firstname",
        "prenom", "prénom",
        "nome", "primeiro nome",
        "vorname"
    )

    private val LAST_NAME_LABELS = setOf(
        "apellidos", "apellido", "primer apellido", "segundo apellido",
        "surname", "surnames", "last name", "family name", "lastname",
        "nom de famille",
        "apelido", "sobrenome",
        "nachname", "familienname"
    )

    /** Every token that can appear in ANY label line (first or last name). */
    private val ALL_NAME_LABEL_TOKENS: Set<String> = FIRST_NAME_LABELS + LAST_NAME_LABELS

    /** Additional non-name label tokens that should never be treated as values. */
    private val OTHER_LABEL_TOKENS = setOf(
        "nationality", "nacionalidad", "sexo", "sex", "gender", "genero", "género",
        "fecha", "date", "nacimiento", "birth", "expiry", "vencimiento", "expiration",
        "expedicion", "issuance", "lugar", "place", "estado civil", "civil status",
        "estatura", "height", "municipio", "departamento",
        "republica", "república", "ministerio", "gobierno", "registro",
        "identificacion", "identificación",
        "dui", "dni", "nui", "cedula", "cédula", "pasaporte", "passport",
        "firma", "signature", "huella", "registrador", "conocido", "known"
    )

    private val ALL_LABEL_TOKENS: Set<String> = ALL_NAME_LABEL_TOKENS + OTHER_LABEL_TOKENS

    // ─── Public API ───────────────────────────────────────────────────────────

    suspend fun extract(bitmap: Bitmap): ExtractionResult {
        val (fullText, visionText) = runOcr(bitmap)
        return extractFromText(fullText, visionText)
    }

    fun extractFromText(fullText: String, visionText: Text?): ExtractionResult {
        log("RAW OCR TEXT ↓\n$fullText\n↑ END OCR TEXT")

        if (fullText.isBlank()) {
            log("OCR returned empty text — returning NONE")
            return none(fullText)
        }

        // ── LAYER 1: MRZ ─────────────────────────────────────────────────────
        val mrzResult = MrzParser.parse(fullText)
        if (mrzResult != null && mrzResult.isReliable) {
            log("LAYER 1 MRZ ✅ format=${mrzResult.mrzFormat} " +
                "name='${mrzResult.lastName}, ${mrzResult.firstName}' doc='${mrzResult.documentNumber}'")
            return ExtractionResult(
                firstName      = mrzResult.firstName.ifBlank { null },
                lastName       = mrzResult.lastName.ifBlank { null },
                documentNumber = mrzResult.documentNumber.ifBlank { null },
                dateOfBirth    = mrzResult.dateOfBirth.ifBlank { null },
                nationality    = mrzResult.issuingCountry.ifBlank { null },
                mrzData        = mrzResult,
                source         = ExtractionSource.MRZ,
                confidence     = if (mrzResult.confidence >= 0.75f) Confidence.HIGH else Confidence.MEDIUM,
                fullOcrText    = fullText
            )
        }
        val unreliableMrz = mrzResult

        // ── LAYER 2: Labelled-field extraction ────────────────────────────────
        val keyed = extractKeyedFields(fullText, visionText)
        if (keyed != null) {
            val (kFirst, kLast, kDoc) = keyed
            val mergedDoc   = unreliableMrz?.documentNumber?.ifBlank { null } ?: kDoc
            val mergedFirst = kFirst ?: unreliableMrz?.firstName?.ifBlank { null }
            val mergedLast  = kLast  ?: unreliableMrz?.lastName?.ifBlank { null }
            log("LAYER 2 OCR-KEYED ✅ first='$mergedFirst' last='$mergedLast' doc='$mergedDoc'")
            if (mergedFirst != null || mergedLast != null || mergedDoc != null) {
                val conf = when {
                    mergedFirst != null && mergedLast != null -> Confidence.HIGH
                    mergedFirst != null || mergedLast != null -> Confidence.MEDIUM
                    else -> Confidence.LOW
                }
                return ExtractionResult(
                    firstName      = mergedFirst,
                    lastName       = mergedLast,
                    documentNumber = mergedDoc,
                    dateOfBirth    = null,
                    nationality    = unreliableMrz?.issuingCountry?.ifBlank { null },
                    mrzData        = unreliableMrz,
                    source         = ExtractionSource.OCR_KEYED,
                    confidence     = conf,
                    fullOcrText    = fullText
                )
            }
        }

        // ── LAYER 3: Heuristic ────────────────────────────────────────────────
        val heuristic = extractHeuristic(fullText, visionText)
        if (heuristic != null) {
            log("LAYER 3 HEURISTIC ✅ first='${heuristic.first}' last='${heuristic.second}' doc='${heuristic.third}'")
            return ExtractionResult(
                firstName      = heuristic.first,
                lastName       = heuristic.second,
                documentNumber = heuristic.third ?: extractDocumentNumber(fullText),
                dateOfBirth    = null,
                nationality    = null,
                mrzData        = null,
                source         = ExtractionSource.OCR_HEURISTIC,
                confidence     = Confidence.LOW,
                fullOcrText    = fullText
            )
        }

        val docOnly = extractDocumentNumber(fullText)
        if (docOnly != null) {
            log("LAYER 3 DOC-ONLY ✅ doc='$docOnly'")
            return ExtractionResult(
                firstName = null, lastName = null, documentNumber = docOnly,
                dateOfBirth = null, nationality = null, mrzData = null,
                source = ExtractionSource.OCR_HEURISTIC, confidence = Confidence.LOW,
                fullOcrText = fullText
            )
        }

        log("NONE — extraction failed for text of ${fullText.length} chars")
        return none(fullText)
    }

    // ─── OCR runner ───────────────────────────────────────────────────────────

    private suspend fun runOcr(bitmap: Bitmap): Pair<String, Text?> =
        suspendCoroutine { cont ->
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                .process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { cont.resume(it.text to it) }
                .addOnFailureListener { cont.resume("" to null) }
        }

    // ─── LAYER 2: Labelled-field extraction ───────────────────────────────────

    /**
     * Core rule: a "label line" is any line whose normalised form contains
     * at least one token from [ALL_LABEL_TOKENS].  The VALUE is ALWAYS
     * on a SUBSEQUENT line — never on the label line itself.
     *
     * This correctly handles:
     *   "Apellidos / Surname"   → pure label  → next line = "AREVALO DELGADO"
     *   "Nombres / Given Names" → pure label  → next line = "CHRISTIAN ALEXANDER"
     *
     * The old code tried to "strip" labels from the line and use the remainder
     * as the value, which caused "Given Names" to leak through as firstName.
     */
    private fun extractKeyedFields(
        fullText: String,
        visionText: Text?
    ): Triple<String?, String?, String?>? {

        val lines = fullText.split("\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        /** True when [line] contains ANY known label token. */
        fun isLabelLine(line: String): Boolean {
            val lower = normaliseLine(line)
            return ALL_LABEL_TOKENS.any { lower.contains(it) }
        }

        /** True when [line] contains a label token from [labelSet]. */
        fun matchesLabelSet(line: String, labelSet: Set<String>): Boolean {
            val lower = normaliseLine(line)
            return labelSet.any { lower.contains(it) }
        }

        /**
         * Given a label set, scans lines top-to-bottom.
         * When a label-line is found, skip subsequent label-lines (bilingual
         * continuations) and return the FIRST non-label line that looks like a name.
         *
         * Example (DUI El Salvador):
         *   Line N  : "Apellidos / Surname"       ← label  → found!
         *   Line N+1: "AREVALO DELGADO"            ← non-label name value  → return
         *
         *   Line M  : "Nombres / Given Names"      ← label  → found!
         *   Line M+1: "CHRISTIAN ALEXANDER"        ← non-label name value  → return
         */
        fun findNameAfterLabel(labelSet: Set<String>): String? {
            for (i in lines.indices) {
                val line = lines[i]
                if (!matchesLabelSet(line, labelSet)) continue
                log("  [KEYED] label='${labelSet.first()}' at line $i: '$line'")

                var consecutiveSkips = 0
                for (j in 1..5) {
                    val candidate = lines.getOrNull(i + j) ?: break
                    if (isLabelLine(candidate)) {
                        consecutiveSkips++
                        log("  [KEYED] skip label-continuation line ${i+j}: '$candidate'")
                        if (consecutiveSkips >= 3) break
                        continue
                    }
                    consecutiveSkips = 0
                    if (isNameValue(candidate)) {
                        log("  [KEYED] ✅ value for '${labelSet.first()}' found at line ${i+j}: '$candidate'")
                        return cleanName(candidate)
                    }
                    // Not a label, not a name-value → stop (e.g. date, number, noise)
                    log("  [KEYED] stop — line ${i+j} is not a name value: '$candidate'")
                    break
                }
            }
            return null
        }

        val firstName = findNameAfterLabel(FIRST_NAME_LABELS)
        val lastName  = findNameAfterLabel(LAST_NAME_LABELS)
        val docNumber = extractDocumentNumber(fullText)

        log("[KEYED] result → first='$firstName' last='$lastName' doc='$docNumber'")

        return if (firstName != null || lastName != null || docNumber != null)
            Triple(firstName, lastName, docNumber)
        else null
    }

    // ─── LAYER 3: Heuristic ───────────────────────────────────────────────────

    private fun extractHeuristic(fullText: String, visionText: Text?): Triple<String?, String?, String?>? {
        val lines = fullText.split("\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        // Filter lines that look like ALL-CAPS person names and are NOT label lines
        val capsNameLines = lines.filter { l ->
            isUpperCaseNameLine(l) && !isAnyLabelLine(l)
        }

        log("  Heuristic capsNameLines: $capsNameLines")

        if (capsNameLines.size >= 2) {
            val lastName  = cleanName(capsNameLines[0])
            val firstName = cleanName(capsNameLines[1])
            if (lastName.isNotBlank() && firstName.isNotBlank())
                return Triple(firstName, lastName, extractDocumentNumber(fullText))
        }
        if (capsNameLines.size == 1) {
            val full  = cleanName(capsNameLines[0])
            val parts = full.split(" ").filter { it.isNotBlank() }
            return when {
                parts.size >= 3 -> Triple(
                    parts.dropLast(2).joinToString(" ").ifBlank { null },
                    parts.takeLast(2).joinToString(" "),
                    extractDocumentNumber(fullText)
                )
                parts.size == 2 -> Triple(parts[0], parts[1], extractDocumentNumber(fullText))
                else -> Triple(full, null, extractDocumentNumber(fullText))
            }
        }

        // ML Kit block-level
        if (visionText != null) {
            val blockNames = visionText.textBlocks
                .flatMap { it.lines }
                .map { it.text.trim() }
                .filter { isUpperCaseNameLine(it) && !isAnyLabelLine(it) }
                .take(3)
            if (blockNames.size >= 2)
                return Triple(cleanName(blockNames[1]), cleanName(blockNames[0]), extractDocumentNumber(fullText))
            if (blockNames.size == 1)
                return Triple(cleanName(blockNames[0]), null, extractDocumentNumber(fullText))
        }

        // Relaxed fallback
        val relaxed = lines
            .filter { isRelaxedNameCandidate(it) && !isAnyLabelLine(it) }
            .sortedByDescending { it.count { c -> c.isLetter() } }
            .take(2)
        if (relaxed.size >= 2)
            return Triple(cleanName(relaxed[1]), cleanName(relaxed[0]), extractDocumentNumber(fullText))
        if (relaxed.size == 1)
            return Triple(cleanName(relaxed[0]), null, extractDocumentNumber(fullText))

        return null
    }

    // ─── Document number extraction ───────────────────────────────────────────

    /**
     * Extracts the document / ID number from the full OCR text.
     *
     * Priority:
     *  P0  Label-adjacent (walks next lines after ID-number label)
     *  P1  DUI SV format: DDDDDDDD-D
     *  P2  DUI without hyphen: 9 consecutive digits
     *  P3  Honduras DNI: 4-4-5 digit groups
     *  P4  Guatemala DPI: 4-5-4 digit groups
     *  P5  Passport / alphanumeric doc: 1-2 letters + 6-8 digits
     *  P6  Generic: longest 7-12 digit sequence that is not a date
     */
    fun extractDocumentNumber(text: String): String? {
        val lines = text.split("\n").map { it.trim() }.filter { it.isNotBlank() }

        // ── P0: Label-adjacent ────────────────────────────────────────────────
        // Build ASCII-safe patterns (avoid non-ASCII inside char classes with (?i))
        val idLabelPatterns = listOf(
            // "Número / Nümero Único / Unico de Identidad" — handle OCR noise
            Regex("""(?i)n[uU][mM][eE][rR][oO]\s+[uU][nN][iI][cC][oO]"""),
            Regex("""(?i)unique\s+[iIlL1][dD]"""),
            Regex("""(?i)[iIlL1][dD][eE][nN][tT][iI][tT][yY]\s+[nN][uU][mM][bB][eE][rR]"""),
            Regex("""(?i)document[oO]?\s+[uU][nN][iI][cC][oO]"""),
            Regex("""(?i)\bnui\b"""),
            Regex("""(?i)\bid\s+number\b"""),
            Regex("""(?i)c[eE][dD][uU][lL][aA]\s+de\s+[iI][dD][eE][nN][tT][iI][dD][aA][dD]"""),
            Regex("""(?i)passport\s+n[oO°]"""),
            Regex("""(?i)pasaporte\s+n[oO°]"""),
            Regex("""(?i)n[uU][mM][eE][rR][oO]\s+de\s+[iI][dD][eE][nN][tT][iI][fF][iI][cC][aA][cC][iI][oO][nN]""")
        )

        for (i in lines.indices) {
            if (idLabelPatterns.none { it.containsMatchIn(lines[i]) }) continue

            log("  P0: label found at line $i '${lines[i]}'")

            // Check lines i, i+1 … i+6 for the number.
            // j=0: check the label line itself (inline value after ":")
            // j>0: skip any continuation label lines (bilingual or multi-line labels)
            //       skip any lines that have no digits at all
            //       stop only when we find a non-label, digit-bearing line that
            //       doesn't parse — that means the number isn't here
            var nonDigitSkips = 0
            for (j in 0..6) {
                val candidate = lines.getOrNull(i + j) ?: break

                // Skip ID-label continuations (bilingual second line)
                if (j > 0 && idLabelPatterns.any { it.containsMatchIn(candidate) }) {
                    log("    P0 skip id-label-continuation at ${i+j}: '$candidate'")
                    continue
                }

                // Skip any other label-only line (e.g. "Fecha de expedicion / Date of issuance")
                // but only if it has no digits — we don't want to skip a line like "05650411-7"
                val hasDigits = candidate.any { it.isDigit() }
                if (j > 0 && !hasDigits) {
                    nonDigitSkips++
                    log("    P0 skip no-digit line at ${i+j}: '$candidate'")
                    if (nonDigitSkips >= 3) break
                    continue
                }
                nonDigitSkips = 0

                val num = extractRawDocNumber(candidate, skipLongLines = j == 0)
                if (num != null) {
                    log("  P0 ✅ '$num' at line ${i+j}: '$candidate'")
                    return num
                }

                // Has digits but didn't parse → if it's a pure label line skip it,
                // otherwise stop (the number won't appear further away)
                if (j > 0) {
                    val normLower = normaliseLine(candidate)
                    val isAnotherLabel = ALL_LABEL_TOKENS.any { normLower.contains(it) }
                    if (isAnotherLabel) {
                        log("    P0 skip label at ${i+j}: '$candidate'")
                        continue
                    }
                    log("    P0 stop — digit line did not parse at ${i+j}: '$candidate'")
                    break
                }
            }
        }

        // ── P1: DUI SV — DDDDDDDD-D ──────────────────────────────────────────
        Regex("""\b(\d{8}-\d)\b""").find(text)?.let {
            log("  P1 DUI ✅ '${it.value}'"); return it.value
        }

        // ── P2: DUI no hyphen — 9 consecutive digits ──────────────────────────
        Regex("""(?<!\d)(\d{9})(?!\d)""").find(text)?.let { v ->
            val n = v.value
            val f = "${n.take(8)}-${n.last()}"
            log("  P2 DUI-nohyphen ✅ '$f'"); return f
        }

        // ── P3: Honduras DNI — 4-4-5 ─────────────────────────────────────────
        Regex("""\b(\d{4})[\s\-](\d{4})[\s\-](\d{5})\b""").find(text)?.let {
            val v = it.groupValues.drop(1).joinToString("")
            log("  P3 HN-DNI ✅ '$v'"); return v
        }

        // ── P4: Guatemala DPI — 4-5-4 ────────────────────────────────────────
        Regex("""\b(\d{4})\s(\d{5})\s(\d{4})\b""").find(text)?.let {
            val v = it.groupValues.drop(1).joinToString("")
            log("  P4 GT-DPI ✅ '$v'"); return v
        }

        // ── P5: Passport — 1-2 letters + 6-8 digits ──────────────────────────
        Regex("""\b([A-Z]{1,2}\d{6,8})\b""").find(text)?.let {
            log("  P5 Passport ✅ '${it.value}'"); return it.value
        }

        // ── P6: Generic fallback ──────────────────────────────────────────────
        val generic = Regex("""(?<!\d)(\d{7,12})(?!\d)""").findAll(text)
            .map { it.value }
            .filter { !isDateLike(it) }
            .maxByOrNull { it.length }
        if (generic != null) { log("  P6 generic ✅ '$generic'"); return generic }

        log("  extractDocNum: NONE found"); return null
    }

    /**
     * Extracts a document-number token from a single line.
     * [skipLongLines]: when true (j==0, the label line itself), allow longer lines
     * so inline values like "DUI: 05650411-7 ..." are found.
     */
    private fun extractRawDocNumber(line: String, skipLongLines: Boolean = false): String? {
        val t = line.trim()
        if (!skipLongLines && t.length > 40) return null
        if (t.none { it.isDigit() }) return null

        Regex("""\b(\d{8}-\d)\b""").find(t)?.let { return it.value }

        Regex("""\b(\d{4}[\s\-]\d{4}[\s\-]\d{5})\b""").find(t)?.let {
            return it.value.replace(Regex("[\\s\\-]"), "")
        }

        Regex("""\b([A-Z]{1,2}\d{6,8})\b""").find(t)?.let {
            if (t.length <= 20) return it.value
        }

        // Pure digit / digit-separator line
        if (t.matches(Regex("""[\d\s\-/]{4,18}"""))) {
            val digits = t.replace(Regex("[\\s\\-/]"), "")
            if (digits.length in 6..15 && !isDateLike(digits)) {
                if (digits.length == 9) return "${digits.take(8)}-${digits.last()}"
                return digits
            }
        }

        return null
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /** Lower-case + collapse spaces + remove leading/trailing noise. */
    private fun normaliseLine(line: String): String =
        line.lowercase()
            .replace(Regex("[/|\\\\]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    /** True if this line contains ANY label token (first-name, last-name, or other). */
    private fun isAnyLabelLine(line: String): Boolean {
        val lower = normaliseLine(line)
        return ALL_LABEL_TOKENS.any { lower.contains(it) }
    }

    /**
     * True if [text] is a plausible name VALUE line.
     * - 3..80 chars
     * - ≤ 2 digits
     * - ≥ 50% letters
     * - NOT itself a label line
     */
    private fun isNameValue(text: String): Boolean {
        val t = text.trim()
        if (t.length < 3 || t.length > 80) return false
        if (t.count { it.isDigit() } > 2) return false
        val letters = t.count { it.isLetter() }
        if (letters.toFloat() / t.length.coerceAtLeast(1) < 0.50f) return false
        if (isAnyLabelLine(t)) return false
        return true
    }

    /** ALL-CAPS name line (heuristic). */
    private fun isUpperCaseNameLine(text: String): Boolean {
        val t = text.trim()
        if (t.length < 4 || t.length > 80) return false
        if (t.count { it.isDigit() } > 1) return false
        val letters = t.count { it.isLetter() }
        if (letters < 4) return false
        if (letters.toFloat() / t.length < 0.70f) return false
        if (t.any { it.isLetter() && it.isLowerCase() }) return false
        return true
    }

    /** Relaxed name candidate — allows mixed case. */
    private fun isRelaxedNameCandidate(text: String): Boolean {
        val t = text.trim()
        if (t.length < 4 || t.length > 80) return false
        if (t.count { it.isDigit() } > 2) return false
        val letters = t.count { it.isLetter() }
        if (letters < 3) return false
        if (letters.toFloat() / t.length < 0.55f) return false
        return true
    }

    /** Title-case a cleaned name string. */
    private fun cleanName(raw: String): String =
        raw.trim()
            .replace(Regex("[^\\p{L}\\s'\\-]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .split(" ")
            .joinToString(" ") { w ->
                if (w.isNotEmpty()) w.lowercase().replaceFirstChar { it.uppercaseChar() } else w
            }

    private fun isDateLike(s: String): Boolean {
        if (s.length !in 6..8) return false
        return try {
            when (s.length) {
                8 -> {
                    val y = s.substring(0, 4).toInt()
                    val m = s.substring(4, 6).toInt()
                    val d = s.substring(6, 8).toInt()
                    y in 1900..2099 && m in 1..12 && d in 1..31
                }
                6 -> {
                    val m = s.substring(2, 4).toInt()
                    val d = s.substring(4, 6).toInt()
                    m in 1..12 && d in 1..31
                }
                else -> false
            }
        } catch (_: Exception) { false }
    }

    private fun none(fullText: String) = ExtractionResult(
        firstName = null, lastName = null, documentNumber = null,
        dateOfBirth = null, nationality = null, mrzData = null,
        source = ExtractionSource.NONE, confidence = Confidence.NONE,
        fullOcrText = fullText
    )

    private fun log(msg: String) = android.util.Log.d("DocExtractor", msg)
}
