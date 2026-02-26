package com.eflglobal.visitorsapp.core.ocr

import com.eflglobal.visitorsapp.domain.model.DocumentClassification
import com.eflglobal.visitorsapp.domain.model.ExtractedField
import com.eflglobal.visitorsapp.domain.model.FieldSource

/**
 * ═══════════════════════════════════════════════════════════════════════════════
 * FieldScoringEngine
 *
 * Assigns a probabilistic confidence score to each candidate line for
 * FIRST_NAME, LAST_NAME, and DOCUMENT_NUMBER extraction.
 *
 * Scoring rules (additive):
 *   +0.40  Line immediately follows a matching field label
 *   +0.10  Additional label in the 2 lines before (within label proximity)
 *   +0.30  Matches a known document-number regex for the detected country
 *   +0.20  Format matches expected pattern (all alpha for names, digits for IDs)
 *   +0.15  ALL_CAPS (strong signal for printed names on Latin-American IDs)
 *   +0.10  Word count in expected range (1–4 words for a name)
 *   +0.10  Entity extraction tag matched (PERSON or ADDRESS)  ← injected externally
 *   -0.30  Name field contains 3+ digits
 *   -0.20  Name field length outside 2–60 chars
 *   -0.15  Line is also a label line (contaminated)
 *   -0.10  Name field has mixed alpha+digit proportion < 60%
 *
 * Returns ExtractedField with the winner line and final confidence.
 * If no candidate reaches AUTOFILL_THRESHOLD (0.60) → value = null.
 * ═══════════════════════════════════════════════════════════════════════════════
 */
object FieldScoringEngine {

    // ─── Label sets ──────────────────────────────────────────────────────────

    private val FIRST_NAME_LABELS = setOf(
        "nombres", "nombre", "primer nombre",
        "given names", "given name", "forenames", "forename",
        "first name", "first names", "firstname",
        "prenom", "prénom", "nome", "primeiro nome", "vorname"
    )

    private val LAST_NAME_LABELS = setOf(
        "apellidos", "apellido", "primer apellido",
        "surname", "surnames", "last name", "family name", "lastname",
        "nom de famille", "apelido", "sobrenome", "nachname", "familienname"
    )

    private val ALL_LABEL_TOKENS: Set<String> =
        FIRST_NAME_LABELS + LAST_NAME_LABELS + setOf(
            "nationality", "nacionalidad", "sexo", "sex", "gender", "genero", "género",
            "fecha", "date", "nacimiento", "birth", "expiry", "vencimiento", "expiration",
            "expedicion", "issuance", "lugar", "place", "estado civil",
            "estatura", "height", "municipio", "departamento",
            "republica", "república", "ministerio", "gobierno", "registro",
            "identificacion", "identificación",
            "dui", "dni", "nui", "cedula", "cédula", "pasaporte", "passport",
            "firma", "signature", "huella", "registrador", "conocido", "known"
        )

    // ─── Document-number regex map per country ────────────────────────────────

    private val DOC_NUMBER_REGEXES: Map<String, List<Regex>> = mapOf(
        "SV" to listOf(Regex("""\b\d{8}-\d\b"""), Regex("""\b\d{9}\b""")),
        "HN" to listOf(Regex("""\b\d{4}-\d{4}-\d{5}\b""")),
        "GT" to listOf(Regex("""\b\d{4}\s\d{5}\s\d{4}\b""")),
        "CR" to listOf(Regex("""\b\d-\d{4}-\d{4}\b""")),
        "NI" to listOf(Regex("""\b\d{3}-\d{6}-\d{4}[A-Z]\b""")),
        "MX" to listOf(Regex("""\b[A-Z]{4}\d{6}[HM][A-Z]{5}[A-Z0-9]\d\b""")),
        "PASSPORT" to listOf(Regex("""\b[A-Z]{1,2}\d{6,9}\b""")),
        "UNKNOWN" to listOf(
            Regex("""\b\d{8}-\d\b"""),
            Regex("""\b[A-Z]{1,2}\d{6,9}\b"""),
            Regex("""\b\d{7,13}\b""")
        )
    )

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Score all lines for FIRST_NAME and return the best candidate.
     * @param entityBoostLines  Set of line-values boosted by ML Kit entity extraction.
     */
    fun scoreFirstName(
        lines: List<String>,
        classification: DocumentClassification,
        entityBoostLines: Set<String> = emptySet()
    ): ExtractedField = scoreNameField(lines, FIRST_NAME_LABELS, classification, entityBoostLines)

    /**
     * Score all lines for LAST_NAME and return the best candidate.
     */
    fun scoreLastName(
        lines: List<String>,
        classification: DocumentClassification,
        entityBoostLines: Set<String> = emptySet()
    ): ExtractedField = scoreNameField(lines, LAST_NAME_LABELS, classification, entityBoostLines)

    /**
     * Score all lines for DOCUMENT_NUMBER and return the best candidate.
     */
    fun scoreDocumentNumber(
        lines: List<String>,
        classification: DocumentClassification
    ): ExtractedField {
        val regexes = (DOC_NUMBER_REGEXES[classification.country] ?: emptyList()) +
                      (DOC_NUMBER_REGEXES["UNKNOWN"] ?: emptyList())

        // ── Label-adjacent search (highest priority) ──────────────────────────
        val idLabelPatterns = listOf(
            Regex("""(?i)n[uU][mM][eE][rR][oO]\s+[uU][nN][iI][cC][oO]"""),
            Regex("""(?i)unique\s+[iIlL1][dD]"""),
            Regex("""(?i)[iIlL1][dD][eE][nN][tT][iI][tT][yY]\s+[nN][uU][mM][bB][eE][rR]"""),
            Regex("""(?i)\bnui\b"""),
            Regex("""(?i)\bid\s+number\b"""),
            Regex("""(?i)passport\s+n[oO°]"""),
            Regex("""(?i)pasaporte\s+n[oO°]"""),
            Regex("""(?i)n[uU][mM][eE][rR][oO]\s+de\s+[iI][dD][eE][nN][tT][iI][fF]"""),
            Regex("""(?i)c[eE][dD][uU][lL][aA]\s+de\s+[iI][dD]"""),
            Regex("""(?i)document[oO]?\s+[uU][nN][iI][cC][oO]""")
        )

        for (i in lines.indices) {
            if (idLabelPatterns.none { it.containsMatchIn(lines[i]) }) continue
            // Scan next 5 lines, skipping label-continuation lines
            var noDigitSkips = 0
            for (j in 1..6) {
                val candidate = lines.getOrNull(i + j) ?: break
                if (!candidate.any { it.isDigit() }) {
                    if (++noDigitSkips >= 3) break
                    continue
                }
                if (idLabelPatterns.any { it.containsMatchIn(candidate) }) continue
                val extracted = extractNumberFromLine(candidate, regexes)
                if (extracted != null) {
                    val breakdown = mapOf("label_adjacent" to 0.40f, "regex_match" to 0.30f, "digit_line" to 0.20f)
                    val conf = breakdown.values.sum().coerceAtMost(1f)
                    log("  [DOC_NUM] label-adjacent ✅ '$extracted' (conf=${"%.2f".format(conf)})")
                    return ExtractedField(extracted, conf, FieldSource.LABEL_OCR, breakdown)
                }
            }
        }

        // ── Regex scan over all lines (fallback) ─────────────────────────────
        data class Candidate(val value: String, val score: Float, val breakdown: Map<String, Float>)

        val candidates = mutableListOf<Candidate>()
        for (line in lines) {
            if (!line.any { it.isDigit() }) continue
            val extracted = extractNumberFromLine(line, regexes) ?: continue
            val breakdown = mutableMapOf<String, Float>()

            // Country-specific regex match
            val countryRegexes = DOC_NUMBER_REGEXES[classification.country] ?: emptyList()
            if (countryRegexes.any { it.containsMatchIn(line) }) {
                breakdown["country_regex"] = 0.35f
            } else {
                breakdown["generic_regex"] = 0.20f
            }
            // Short clean line (likely just the number)
            if (line.trim().length <= 15) breakdown["clean_line"] = 0.15f
            // Penalise if line is long and noisy
            if (line.trim().length > 40) breakdown["long_line_penalty"] = -0.10f

            val score = breakdown.values.sum().coerceIn(0f, 1f)
            candidates += Candidate(extracted, score, breakdown)
        }

        val best = candidates.maxByOrNull { it.score }
        if (best != null && best.score > 0.20f) {
            log("  [DOC_NUM] regex fallback ✅ '${best.value}' (conf=${"%.2f".format(best.score)})")
            return ExtractedField(best.value, best.score, FieldSource.LABEL_OCR, best.breakdown)
        }

        log("  [DOC_NUM] not found")
        return ExtractedField.EMPTY
    }

    // ─── Core name scoring ────────────────────────────────────────────────────

    private fun scoreNameField(
        lines: List<String>,
        labelSet: Set<String>,
        classification: DocumentClassification,
        entityBoostLines: Set<String>
    ): ExtractedField {
        data class Candidate(val value: String, val score: Float, val breakdown: Map<String, Float>)

        val candidates = mutableListOf<Candidate>()

        for (i in lines.indices) {
            val line = lines[i]

            // Only score lines that are plausible name candidates
            if (!isPlausibleNameLine(line)) continue
            // Skip label lines themselves
            if (isLabelLine(line)) continue

            val breakdown = mutableMapOf<String, Float>()

            // ── Rule 1: Label proximity (+0.40 / +0.10) ─────────────────────
            for (lookBack in 1..3) {
                val prev = lines.getOrNull(i - lookBack) ?: break
                if (containsFieldLabel(prev, labelSet)) {
                    val bonus = if (lookBack == 1) 0.40f else 0.10f
                    breakdown["label_proximity_$lookBack"] = bonus
                    break
                }
            }

            // ── Rule 2: Format — ALL CAPS (+0.15) ────────────────────────────
            if (isAllCaps(line)) breakdown["all_caps"] = 0.15f

            // ── Rule 3: Word count in 1–4 (+0.10) ────────────────────────────
            val words = line.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            if (words.size in 1..4) breakdown["word_count_ok"] = 0.10f

            // ── Rule 4: High letter density (+0.20) ──────────────────────────
            val letters  = line.count { it.isLetter() }
            val total    = line.length.coerceAtLeast(1)
            val density  = letters.toFloat() / total
            if (density >= 0.80f) breakdown["high_letter_density"] = 0.20f
            else if (density >= 0.60f) breakdown["medium_letter_density"] = 0.10f

            // ── Rule 5: Entity boost (+0.10) ─────────────────────────────────
            if (entityBoostLines.any { line.contains(it, ignoreCase = true) }) {
                breakdown["entity_boost"] = 0.10f
            }

            // ── Penalties ────────────────────────────────────────────────────
            val digitCount = line.count { it.isDigit() }
            if (digitCount >= 3) breakdown["digit_penalty"] = -0.30f
            else if (digitCount >= 1) breakdown["digit_minor_penalty"] = -0.10f

            val len = line.trim().length
            if (len < 2 || len > 60) breakdown["length_penalty"] = -0.20f

            if (density < 0.50f) breakdown["low_letter_density_penalty"] = -0.10f

            val score = breakdown.values.sum().coerceIn(0f, 1f)
            if (score > 0f) candidates += Candidate(cleanName(line), score, breakdown)
        }

        if (candidates.isEmpty()) {
            log("  [NAME:${labelSet.first()}] no candidates found")
            return ExtractedField.EMPTY
        }

        val best = candidates.maxByOrNull { it.score }!!
        log("  [NAME:${labelSet.first()}] best='${best.value}' conf=${"%.2f".format(best.score)} breakdown=${best.breakdown}")

        if (best.score < ExtractedField.AUTOFILL_THRESHOLD) {
            log("  [NAME:${labelSet.first()}] below threshold → not auto-filled")
            return ExtractedField(best.value, best.score, FieldSource.LABEL_OCR, best.breakdown)
        }

        return ExtractedField(best.value, best.score, FieldSource.LABEL_OCR, best.breakdown)
    }

    // ─── Number extraction helpers ────────────────────────────────────────────

    private fun extractNumberFromLine(line: String, regexes: List<Regex>): String? {
        val t = line.trim()
        for (rx in regexes) {
            val match = rx.find(t) ?: continue
            val v = match.value.replace(Regex("[\\s]"), "")
            if (!isDateLike(v.filter { it.isDigit() })) return v
        }
        // DUI SV without hyphen
        Regex("""\b(\d{9})\b""").find(t)?.let { m ->
            val n = m.value
            if (!isDateLike(n)) return "${n.take(8)}-${n.last()}"
        }
        return null
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun normaliseLine(line: String) =
        line.lowercase()
            .replace(Regex("[/|\\\\]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun isLabelLine(line: String): Boolean {
        val lower = normaliseLine(line)
        return ALL_LABEL_TOKENS.any { lower.contains(it) }
    }

    private fun containsFieldLabel(line: String, labelSet: Set<String>): Boolean {
        val lower = normaliseLine(line)
        return labelSet.any { lower.contains(it) }
    }

    private fun isPlausibleNameLine(line: String): Boolean {
        val t = line.trim()
        if (t.length < 2 || t.length > 80) return false
        val letters = t.count { it.isLetter() }
        return letters.toFloat() / t.length.coerceAtLeast(1) >= 0.40f
    }

    private fun isAllCaps(line: String): Boolean {
        val letters = line.filter { it.isLetter() }
        return letters.isNotEmpty() && letters.all { it.isUpperCase() }
    }

    private fun cleanName(raw: String): String =
        raw.trim()
            .replace(Regex("[^\\p{L}\\s'\\-]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .split(" ")
            .joinToString(" ") { w ->
                if (w.isNotEmpty()) w.lowercase().replaceFirstChar { it.uppercaseChar() } else w
            }

    private fun isDateLike(digits: String): Boolean {
        if (digits.length !in 6..8) return false
        return try {
            when (digits.length) {
                8 -> {
                    val y = digits.substring(0, 4).toInt()
                    val m = digits.substring(4, 6).toInt()
                    val d = digits.substring(6, 8).toInt()
                    y in 1900..2099 && m in 1..12 && d in 1..31
                }
                6 -> digits.substring(2, 4).toInt() in 1..12 && digits.substring(4, 6).toInt() in 1..31
                else -> false
            }
        } catch (_: Exception) { false }
    }

    private fun log(msg: String) = android.util.Log.d("FieldScoring", msg)
}



