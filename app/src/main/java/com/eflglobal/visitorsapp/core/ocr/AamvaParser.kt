package com.eflglobal.visitorsapp.core.ocr

/**
 * ═══════════════════════════════════════════════════════════════════════════════
 * AamvaParser — parses the PDF417 barcode found on the back of USA / Canada
 * driver licences and state IDs (AAMVA DL/ID Card Design Standard).
 *
 * The raw payload looks like:
 *
 *   @\n\rANSI 636000090002DL00410288ZV03290015DL
 *   DAQD12345678
 *   DCSSMITH
 *   DACJOHN
 *   DADQUINCY
 *   DBB01151985
 *   DBA01152027
 *   DBC1
 *   ...
 *
 * Each data element is a 3-letter code immediately followed by its value,
 * terminated by a separator (LF / CR / RS). We tolerate OCR/encoding noise by
 * splitting on any of those separators and reading the leading 3-char code.
 *
 * This is the MOST ACCURATE source for US/CA documents — when present it should
 * win over text OCR.
 * ═══════════════════════════════════════════════════════════════════════════════
 */
object AamvaParser {

    /** LF, CR, and the AAMVA control separators FS/GS/RS/US. */
    private val SEPARATORS = Regex("[\\r\\n\\u001C\\u001D\\u001E\\u001F]")

    data class AamvaData(
        val firstName: String?,
        val middleName: String?,
        val lastName: String?,
        val documentNumber: String?,
        val dateOfBirth: String?,   // normalised to YYYY-MM-DD when possible, else raw
        val expiryDate: String?,    // normalised to YYYY-MM-DD when possible, else raw
        val sex: String?,           // "M" | "F" | "X"
        val elements: Map<String, String>
    ) {
        /** True when we recovered at least a name or a document number. */
        val isUsable: Boolean get() =
            !lastName.isNullOrBlank() || !firstName.isNullOrBlank() || !documentNumber.isNullOrBlank()
    }

    /** Cheap detection: AAMVA payloads carry the "ANSI " header (usually after "@"). */
    fun isAamva(raw: String): Boolean {
        val head = raw.take(64)
        return head.contains("ANSI ") || head.startsWith("@")
    }

    fun parse(raw: String): AamvaData? {
        if (!isAamva(raw)) return null

        val elements = HashMap<String, String>()
        val tokens = raw.split(SEPARATORS)
        for (token in tokens) {
            val t = token.trim()
            if (t.length < 3) continue
            val code = t.substring(0, 3)
            // Data-element codes are 3 uppercase letters (D**, Z**, etc.).
            if (!code.all { it.isLetterOrDigit() } || !code[0].isLetter()) continue
            val value = t.substring(3).trim()
            if (value.isNotEmpty() && !elements.containsKey(code)) {
                elements[code] = value
            }
        }
        if (elements.isEmpty()) return null

        // Last name: DCS (standard) or DAB (older).
        val lastName = firstNonBlank(elements["DCS"], elements["DAB"])
        // First name(s): DAC or DCT (DCT may pack "JOHN QUINCY" together).
        val firstRaw = firstNonBlank(elements["DAC"], elements["DCT"])
        val middleName = elements["DAD"]?.cleanName()

        // If DCT packed given+middle, keep first token as first name.
        val firstName = firstRaw?.let {
            if (elements["DAC"] == null && it.contains(" ")) it.substringBefore(" ")
            else it
        }?.cleanName()

        val docNumber = elements["DAQ"]?.trim()?.takeIf { it.isNotBlank() }

        val dob = normaliseAamvaDate(elements["DBB"])
        val exp = normaliseAamvaDate(elements["DBA"])
        val sex = when (elements["DBC"]?.trim()) {
            "1" -> "M"
            "2" -> "F"
            else -> elements["DBC"]?.trim()
                ?.takeIf { it.equals("M", true) || it.equals("F", true) }
                ?.uppercase()
        }

        val data = AamvaData(
            firstName      = firstName,
            middleName     = middleName,
            lastName       = lastName?.cleanName(),
            documentNumber = docNumber,
            dateOfBirth    = dob,
            expiryDate     = exp,
            sex            = sex,
            elements       = elements
        )
        return data.takeIf { it.isUsable }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun firstNonBlank(vararg values: String?): String? =
        values.firstOrNull { !it.isNullOrBlank() }?.trim()

    /**
     * AAMVA dates are 8 digits. US jurisdictions use MMDDCCYY; Canadian ones use
     * CCYYMMDD. We disambiguate by checking which interpretation yields a valid
     * month/day, preferring the US layout. Returns YYYY-MM-DD or the raw string
     * when it can't be interpreted.
     */
    private fun normaliseAamvaDate(raw: String?): String? {
        val digits = raw?.filter { it.isDigit() } ?: return null
        if (digits.length != 8) return raw?.trim()?.takeIf { it.isNotBlank() }

        // US: MMDDCCYY
        val mmUS = digits.substring(0, 2).toInt()
        val ddUS = digits.substring(2, 4).toInt()
        val yyUS = digits.substring(4, 8)
        if (mmUS in 1..12 && ddUS in 1..31) {
            return "$yyUS-${"%02d".format(mmUS)}-${"%02d".format(ddUS)}"
        }

        // Canada: CCYYMMDD
        val yyCA = digits.substring(0, 4)
        val mmCA = digits.substring(4, 6).toInt()
        val ddCA = digits.substring(6, 8).toInt()
        if (mmCA in 1..12 && ddCA in 1..31) {
            return "$yyCA-${"%02d".format(mmCA)}-${"%02d".format(ddCA)}"
        }
        return raw?.trim()
    }

    /** AAMVA names arrive UPPERCASE; title-case them and strip trailing commas. */
    private fun String.cleanName(): String =
        trim().trimEnd(',')
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(" ") { w -> w.lowercase().replaceFirstChar { it.uppercaseChar() } }
}
