package com.eflglobal.visitorsapp.core.ocr

/**
 * ═══════════════════════════════════════════════════════════════════════════════
 * MrzParser — ISO/IEC 9303 Machine Readable Zone parser.
 *
 * Supports all three TD formats:
 *   • TD1  — 3 lines × 30 characters  (ID cards, DUI, driver licences)
 *   • TD2  — 2 lines × 36 characters  (older ID cards)
 *   • TD3  — 2 lines × 44 characters  (passports, travel documents)
 *
 * Robustness features:
 *   • Check-digit validation (ISO Luhn-variant) on every critical field.
 *   • OCR noise tolerance: common substitutions (O↔0, I↔1, S↔5) are corrected
 *     before parsing so that a slightly misread MRZ still yields valid data.
 *   • Fuzzy line detection: scans the raw OCR text for MRZ-like lines
 *     (≥30 chars, mostly uppercase alphanumeric + '<') even when line-breaks
 *     are inconsistent.
 *   • Name normalisation: converts "GARCIA<<JUAN<CARLOS" to
 *     firstName="Juan Carlos", lastName="Garcia".
 *
 * Usage:
 *   val result = MrzParser.parse(fullOcrText)
 *   if (result != null) { … result.firstName … result.lastName … }
 * ═══════════════════════════════════════════════════════════════════════════════
 */
object MrzParser {

    // ─── Public result type ───────────────────────────────────────────────────

    /**
     * Structured data extracted from a valid MRZ.
     *
     * @param documentType  P=Passport, I=ID/DUI, A=Other travel doc, etc.
     * @param issuingCountry  3-letter ISO 3166-1 alpha-3 country code.
     * @param lastName        Primary identifier (family name), title-cased.
     * @param firstName       Secondary identifier (given names), title-cased.
     * @param documentNumber  Document number (cleaned, without check digit).
     * @param nationality     Nationality code (3 letters).
     * @param dateOfBirth     YYMMDD string, e.g. "851204".
     * @param sex             "M", "F", or "<" (unspecified).
     * @param expiryDate      YYMMDD string.
     * @param personalNumber  Optional personal number (TD3 line 2 field 9), may be empty.
     * @param mrzFormat       "TD1", "TD2", or "TD3".
     * @param checkDigitsOk   Number of check digits that passed (0-5 for TD3, 0-3 for TD1).
     * @param rawLines        The MRZ lines as parsed (useful for debugging).
     */
    data class MrzData(
        val documentType: String,
        val issuingCountry: String,
        val lastName: String,
        val firstName: String,
        val documentNumber: String,
        val nationality: String,
        val dateOfBirth: String,
        val sex: String,
        val expiryDate: String,
        val personalNumber: String,
        val mrzFormat: String,
        val checkDigitsOk: Int,
        val checkDigitsTotal: Int,
        val rawLines: List<String>
    ) {
        /** True if at least 60% of verifiable check digits are valid. */
        val isReliable: Boolean get() = checkDigitsTotal == 0 || (checkDigitsOk.toFloat() / checkDigitsTotal) >= 0.60f

        /** Confidence score 0.0–1.0 combining check-digit ratio + name presence. */
        val confidence: Float get() {
            val cdScore = if (checkDigitsTotal == 0) 0.5f
                          else checkDigitsOk.toFloat() / checkDigitsTotal
            val nameScore = if (lastName.isNotBlank() || firstName.isNotBlank()) 0.3f else 0f
            val docScore  = if (documentNumber.isNotBlank()) 0.2f else 0f
            return (cdScore * 0.5f + nameScore + docScore).coerceIn(0f, 1f)
        }
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Attempts to detect and parse an MRZ from [rawOcrText].
     *
     * Returns null if no valid MRZ is found.
     */
    fun parse(rawOcrText: String): MrzData? {
        val candidates = extractMrzLines(rawOcrText)
        return candidates.firstNotNullOfOrNull { lines ->
            tryParseMrz(lines)
        }
    }

    // ─── MRZ line extraction ──────────────────────────────────────────────────

    /**
     * Scans [text] for MRZ-like lines.
     *
     * A line is MRZ-like if it:
     *   • Has length ≥ 30
     *   • Contains ≥ 60% MRZ characters (A-Z, 0-9, <)
     *
     * Groups consecutive MRZ lines into candidate sets for parsing.
     */
    private fun extractMrzLines(text: String): List<List<String>> {
        val rawLines = text.lines()
        val mrzLines = mutableListOf<String>()

        for (raw in rawLines) {
            val cleaned = cleanMrzLine(raw)
            if (isMrzLike(cleaned)) mrzLines.add(cleaned)
        }

        // Also try to find MRZ embedded in long unbroken text chunks
        val embeddedCandidates = findEmbeddedMrz(text)

        val allCandidates = mutableListOf<List<String>>()

        // Build consecutive pairs/triples from mrzLines
        if (mrzLines.size >= 2) {
            for (i in 0 until mrzLines.size - 1) {
                // TD3 pair (44 chars each)
                if (mrzLines[i].length >= 44 && mrzLines[i + 1].length >= 44) {
                    allCandidates.add(listOf(
                        mrzLines[i].take(44).padEnd(44, '<'),
                        mrzLines[i + 1].take(44).padEnd(44, '<')
                    ))
                }
                // TD2 pair (36 chars each)
                if (mrzLines[i].length in 36..43 && mrzLines[i + 1].length in 36..43) {
                    allCandidates.add(listOf(
                        mrzLines[i].take(36).padEnd(36, '<'),
                        mrzLines[i + 1].take(36).padEnd(36, '<')
                    ))
                }
            }
            // TD1 triple (30 chars each)
            if (mrzLines.size >= 3) {
                for (i in 0 until mrzLines.size - 2) {
                    if (mrzLines[i].length >= 30) {
                        allCandidates.add(listOf(
                            mrzLines[i].take(30).padEnd(30, '<'),
                            mrzLines[i + 1].take(30).padEnd(30, '<'),
                            mrzLines[i + 2].take(30).padEnd(30, '<')
                        ))
                    }
                }
            }
        }

        allCandidates.addAll(embeddedCandidates)
        return allCandidates
    }

    /**
     * Normalizes a raw text line for MRZ parsing.
     * - Removes spaces and non-MRZ characters inside the line
     * - Applies common OCR character substitutions
     */
    private fun cleanMrzLine(line: String): String {
        // Remove leading/trailing whitespace
        var s = line.trim()
        // Remove internal spaces (OCR sometimes splits MRZ at spaces)
        s = s.replace(" ", "")
        // Uppercase
        s = s.uppercase()
        // Keep only valid MRZ chars
        s = s.filter { it.isLetterOrDigit() || it == '<' }
        // Apply OCR substitutions only in positions that should be numeric
        // We cannot know positions yet, so we defer substitution to field-level
        return s
    }

    private fun isMrzLike(line: String): Boolean {
        if (line.length < 30) return false
        val mrzChars = line.count { it.isUpperCase() || it.isDigit() || it == '<' }
        return mrzChars.toFloat() / line.length >= 0.85f
    }

    /**
     * Looks for MRZ sequences embedded in long text blocks (e.g. when ML Kit
     * merges lines without newlines).
     */
    private fun findEmbeddedMrz(text: String): List<List<String>> {
        val result = mutableListOf<List<String>>()
        val flat   = text.uppercase().replace(Regex("[^A-Z0-9<]"), "")

        // TD3: two consecutive 44-char MRZ sequences
        if (flat.length >= 88) {
            for (start in 0..flat.length - 88) {
                val l1 = flat.substring(start, start + 44)
                val l2 = flat.substring(start + 44, start + 88)
                if (isMrzLike(l1) && isMrzLike(l2)) {
                    result.add(listOf(l1, l2))
                }
            }
        }
        // TD1: three consecutive 30-char sequences
        if (flat.length >= 90) {
            for (start in 0..flat.length - 90) {
                val l1 = flat.substring(start, start + 30)
                val l2 = flat.substring(start + 30, start + 60)
                val l3 = flat.substring(start + 60, start + 90)
                if (isMrzLike(l1) && isMrzLike(l2) && isMrzLike(l3)) {
                    result.add(listOf(l1, l2, l3))
                }
            }
        }
        return result
    }

    // ─── MRZ parsing ──────────────────────────────────────────────────────────

    private fun tryParseMrz(lines: List<String>): MrzData? {
        return when {
            lines.size == 3 && lines.all { it.length == 30 } -> parseTD1(lines)
            lines.size == 2 && lines.all { it.length == 44 } -> parseTD3(lines)
            lines.size == 2 && lines.all { it.length == 36 } -> parseTD2(lines)
            else -> null
        }
    }

    // ─── TD3 (passport) ───────────────────────────────────────────────────────

    /**
     * TD3 Layout (2 × 44):
     * Line 1: [0]    type (P or V or A)
     *         [1]    subtype
     *         [2-4]  issuing country
     *         [5-43] names (primary<<secondary)
     * Line 2: [0-8]  document number
     *         [9]    check digit doc number
     *         [10-12] nationality
     *         [13-18] date of birth (YYMMDD)
     *         [19]   check digit DOB
     *         [20]   sex
     *         [21-26] expiry (YYMMDD)
     *         [27]   check digit expiry
     *         [28-41] personal number / optional data
     *         [42]   check digit personal number (or <)
     *         [43]   overall check digit
     */
    private fun parseTD3(lines: List<String>): MrzData? {
        val l1 = applySubstitutions(lines[0], TD3_ALPHA_POSITIONS_L1)
        val l2 = applySubstitutions(lines[1], TD3_NUMERIC_POSITIONS_L2)

        val docType    = l1.substring(0, 2).trimEnd('<')
        val country    = l1.substring(2, 5).trimEnd('<')
        val rawNames   = l1.substring(5, 44)

        val docNum     = l2.substring(0, 9).trimEnd('<')
        val cdDocNum   = l2[9]
        val nationality = l2.substring(10, 13).trimEnd('<')
        val dob        = l2.substring(13, 19)
        val cdDob      = l2[19]
        val sex        = l2[20].toString()
        val expiry     = l2.substring(21, 27)
        val cdExpiry   = l2[27]
        val personal   = l2.substring(28, 42).trimEnd('<')
        val cdPersonal = l2[42]
        val cdOverall  = l2[43]

        // Check-digit verification
        var okCount = 0
        val totalCount = 4
        if (verifyCheckDigit(docNum,          cdDocNum))   okCount++
        if (verifyCheckDigit(dob,             cdDob))      okCount++
        if (verifyCheckDigit(expiry,          cdExpiry))   okCount++
        if (verifyCheckDigit(
            docNum + cdDocNum + nationality + dob + cdDob + sex + expiry + cdExpiry + personal + cdPersonal,
            cdOverall
        )) okCount++

        val (lastName, firstName) = parseNames(rawNames)

        // Reject if zero check digits pass AND names are empty
        if (okCount == 0 && lastName.isBlank() && firstName.isBlank()) return null

        return MrzData(
            documentType   = docType,
            issuingCountry = country,
            lastName       = lastName,
            firstName      = firstName,
            documentNumber = docNum,
            nationality    = nationality,
            dateOfBirth    = dob,
            sex            = sex,
            expiryDate     = expiry,
            personalNumber = personal,
            mrzFormat      = "TD3",
            checkDigitsOk  = okCount,
            checkDigitsTotal = totalCount,
            rawLines       = lines
        )
    }

    // ─── TD1 (ID cards, 3 lines × 30) ────────────────────────────────────────

    /**
     * TD1 Layout (3 × 30):
     * Line 1: [0]    document type
     *         [1]    subtype
     *         [2-4]  issuing country
     *         [5-13] document number
     *         [14]   check digit doc number
     *         [15-29] optional data 1
     * Line 2: [0-5]  date of birth (YYMMDD)
     *         [6]    check digit DOB
     *         [7]    sex
     *         [8-13] expiry (YYMMDD)
     *         [14]   check digit expiry
     *         [15-17] nationality
     *         [18-28] optional data 2
     *         [29]   overall check digit
     * Line 3: [0-29] names (primary<<secondary)
     */
    private fun parseTD1(lines: List<String>): MrzData? {
        val l1 = applySubstitutions(lines[0], TD1_NUMERIC_POSITIONS_L1)
        val l2 = applySubstitutions(lines[1], TD1_NUMERIC_POSITIONS_L2)
        val l3 = applySubstitutions(lines[2], TD1_ALPHA_POSITIONS_L3)

        val docType    = l1.substring(0, 2).trimEnd('<')
        val country    = l1.substring(2, 5).trimEnd('<')
        val docNum     = l1.substring(5, 14).trimEnd('<')
        val cdDocNum   = l1[14]
        val optional1  = l1.substring(15, 30)

        val dob        = l2.substring(0, 6)
        val cdDob      = l2[6]
        val sex        = l2[7].toString()
        val expiry     = l2.substring(8, 14)
        val cdExpiry   = l2[14]
        val nationality = l2.substring(15, 18).trimEnd('<')
        val optional2  = l2.substring(18, 29)
        val cdOverall  = l2[29]

        val rawNames   = l3

        var okCount = 0
        val totalCount = 4
        if (verifyCheckDigit(docNum,    cdDocNum)) okCount++
        if (verifyCheckDigit(dob,       cdDob))    okCount++
        if (verifyCheckDigit(expiry,    cdExpiry)) okCount++
        if (verifyCheckDigit(
            l1.substring(5, 30) + l2.substring(0, 7) + l2.substring(8, 15) + l2.substring(18, 29),
            cdOverall
        )) okCount++

        val (lastName, firstName) = parseNames(rawNames)

        if (okCount == 0 && lastName.isBlank() && firstName.isBlank()) return null

        return MrzData(
            documentType    = docType,
            issuingCountry  = country,
            lastName        = lastName,
            firstName       = firstName,
            documentNumber  = docNum,
            nationality     = nationality,
            dateOfBirth     = dob,
            sex             = sex,
            expiryDate      = expiry,
            personalNumber  = (optional1 + optional2).trimEnd('<'),
            mrzFormat       = "TD1",
            checkDigitsOk   = okCount,
            checkDigitsTotal = totalCount,
            rawLines        = lines
        )
    }

    // ─── TD2 (older ID cards, 2 lines × 36) ─────────────────────────────────

    private fun parseTD2(lines: List<String>): MrzData? {
        val l1 = applySubstitutions(lines[0], emptySet())
        val l2 = applySubstitutions(lines[1], TD2_NUMERIC_POSITIONS_L2)

        val docType    = l1.substring(0, 2).trimEnd('<')
        val country    = l1.substring(2, 5).trimEnd('<')
        val rawNames   = l1.substring(5, 36)

        val docNum     = l2.substring(0, 9).trimEnd('<')
        val cdDocNum   = l2[9]
        val nationality = l2.substring(10, 13).trimEnd('<')
        val dob        = l2.substring(13, 19)
        val cdDob      = l2[19]
        val sex        = l2[20].toString()
        val expiry     = l2.substring(21, 27)
        val cdExpiry   = l2[27]
        val optional   = l2.substring(28, 35).trimEnd('<')
        val cdOverall  = l2[35]

        var okCount = 0
        val totalCount = 4
        if (verifyCheckDigit(docNum, cdDocNum))   okCount++
        if (verifyCheckDigit(dob, cdDob))         okCount++
        if (verifyCheckDigit(expiry, cdExpiry))   okCount++
        if (verifyCheckDigit(
            docNum + cdDocNum + nationality + dob + cdDob + sex + expiry + cdExpiry + optional,
            cdOverall
        )) okCount++

        val (lastName, firstName) = parseNames(rawNames)

        if (okCount == 0 && lastName.isBlank() && firstName.isBlank()) return null

        return MrzData(
            documentType    = docType,
            issuingCountry  = country,
            lastName        = lastName,
            firstName       = firstName,
            documentNumber  = docNum,
            nationality     = nationality,
            dateOfBirth     = dob,
            sex             = sex,
            expiryDate      = expiry,
            personalNumber  = optional,
            mrzFormat       = "TD2",
            checkDigitsOk   = okCount,
            checkDigitsTotal = totalCount,
            rawLines        = lines
        )
    }

    // ─── Name parsing ─────────────────────────────────────────────────────────

    /**
     * Parses the name field from a raw MRZ name zone.
     *
     * Format: PRIMARY_IDENTIFIER<<SECONDARY_IDENTIFIER<NAMES
     * Filler `<` chars act as spaces within each part.
     *
     * Returns Pair(lastName, firstName) — both title-cased.
     */
    private fun parseNames(raw: String): Pair<String, String> {
        val separator = "<<"
        val idx = raw.indexOf(separator)

        val (primaryRaw, secondaryRaw) = if (idx >= 0) {
            raw.substring(0, idx) to raw.substring(idx + 2)
        } else {
            raw to ""  // No separator — treat entire field as primary
        }

        // Replace single '<' with space within each part, trim filler
        val lastName  = primaryRaw.replace('<', ' ').trim().titleCase()
        val firstName = secondaryRaw.replace('<', ' ').trim().titleCase()

        return lastName to firstName
    }

    private fun String.titleCase(): String =
        split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.lowercase().replaceFirstChar { it.uppercaseChar() }
            }

    // ─── Check-digit calculation ──────────────────────────────────────────────

    /**
     * Verifies the ISO 9303 check digit for [data] against [checkDigit].
     *
     * Character values:
     *   0-9 → 0-9
     *   A-Z → 10-35
     *   <   → 0
     *
     * Weighting: 7, 3, 1, 7, 3, 1, …
     */
    private fun verifyCheckDigit(data: String, checkDigit: Char): Boolean {
        if (checkDigit == '<') return true  // no check digit present — skip
        val expected = computeCheckDigit(data)
        return checkDigit.digitToIntOrNull() == expected
    }

    fun computeCheckDigit(data: String): Int {
        val weights = intArrayOf(7, 3, 1)
        var sum = 0
        data.forEachIndexed { i, c ->
            val value = when {
                c == '<'       -> 0
                c.isDigit()    -> c.digitToInt()
                c.isUpperCase() -> c.code - 'A'.code + 10
                else           -> 0  // ignore unexpected chars
            }
            sum += value * weights[i % 3]
        }
        return sum % 10
    }

    // ─── OCR correction tables ────────────────────────────────────────────────

    /**
     * Applies known OCR substitutions at specified positions.
     *
     * Positions where letters should appear: 0↔O confusion (O is zero in MRZ numerics).
     * Positions where digits should appear: O→0, I→1, S→5, B→8 etc.
     */
    private fun applySubstitutions(line: String, numericPositions: Set<Int>): String {
        val sb = StringBuilder(line)
        for (i in sb.indices) {
            if (i in numericPositions) {
                // This position should be numeric — fix alpha→digit confusions
                when (sb[i]) {
                    'O'  -> sb[i] = '0'
                    'I', 'L' -> sb[i] = '1'
                    'S'  -> sb[i] = '5'
                    'B'  -> sb[i] = '8'
                    'G'  -> sb[i] = '6'
                    'Z'  -> sb[i] = '2'
                    'T'  -> sb[i] = '7'
                }
            } else {
                // This position should be alphabetic — fix digit→alpha confusions
                when (sb[i]) {
                    '0'  -> sb[i] = 'O'
                    '1'  -> sb[i] = 'I'
                }
            }
        }
        return sb.toString()
    }

    // Numeric positions for TD3 line 2 (positions that must be digits or check digits)
    private val TD3_NUMERIC_POSITIONS_L2 = setOf(
        0,1,2,3,4,5,6,7,8, // doc number (alphanumeric — skip substitution here)
        9,                  // check digit
        13,14,15,16,17,18,  // DOB
        19,                 // check digit
        21,22,23,24,25,26,  // expiry
        27,                 // check digit
        43                  // overall check digit
    )

    // Alpha positions for TD3 line 1 (names + country code)
    private val TD3_ALPHA_POSITIONS_L1 = emptySet<Int>()

    // TD1 line 1: doc number positions 5-13 are alphanumeric, check digit at 14
    private val TD1_NUMERIC_POSITIONS_L1 = setOf(14) // only check digit is strictly numeric

    // TD1 line 2: numeric positions
    private val TD1_NUMERIC_POSITIONS_L2 = setOf(
        0,1,2,3,4,5,  // DOB
        6,            // check digit
        8,9,10,11,12,13, // expiry
        14,           // check digit
        29            // overall check digit
    )

    // TD1 line 3: all alpha (names)
    private val TD1_ALPHA_POSITIONS_L3 = emptySet<Int>()

    // TD2 line 2 numeric positions
    private val TD2_NUMERIC_POSITIONS_L2 = setOf(
        9,            // check digit doc
        13,14,15,16,17,18, // DOB
        19,           // check digit DOB
        21,22,23,24,25,26, // expiry
        27,           // check digit expiry
        35            // overall
    )
}

