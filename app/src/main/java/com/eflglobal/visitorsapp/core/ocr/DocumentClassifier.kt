package com.eflglobal.visitorsapp.core.ocr

import com.eflglobal.visitorsapp.domain.model.DocumentClassification

/**
 * ═══════════════════════════════════════════════════════════════════════════════
 * DocumentClassifier
 *
 * Classifies an identity document from raw OCR text BEFORE field extraction.
 * Uses a scoring system across multiple signal categories, normalised to 0–1.
 *
 * If confidence < 0.50 → caller should skip automatic extraction.
 * ═══════════════════════════════════════════════════════════════════════════════
 */
object DocumentClassifier {

    // ─── Country signal tables ────────────────────────────────────────────────

    private data class CountryProfile(
        val code: String,
        val keywords: Set<String>,
        val idRegex: Regex?,
        val docTypes: Set<String>
    )

    private val COUNTRY_PROFILES = listOf(
        CountryProfile(
            code = "SV",
            keywords = setOf(
                "el salvador", "república de el salvador", "documento unico de identidad",
                "dui", "registro nacional de las personas", "rnpn",
                "numero unico de identidad", "unique id number"
            ),
            idRegex = Regex("""\b\d{8}-\d\b"""),
            docTypes = setOf("dui", "id_card")
        ),
        CountryProfile(
            code = "HN",
            keywords = setOf(
                "honduras", "república de honduras", "registro nacional de las personas",
                "tarjeta de identidad", "identidad nacional"
            ),
            idRegex = Regex("""\b\d{4}-\d{4}-\d{5}\b"""),
            docTypes = setOf("id_card")
        ),
        CountryProfile(
            code = "GT",
            keywords = setOf(
                "guatemala", "república de guatemala", "documento personal de identificación",
                "dpi", "renap"
            ),
            idRegex = Regex("""\b\d{4}\s\d{5}\s\d{4}\b"""),
            docTypes = setOf("dpi", "id_card")
        ),
        CountryProfile(
            code = "US",
            keywords = setOf(
                "united states", "driver license", "driver's license",
                "state of", "department of motor vehicles", "dmv",
                "license no", "lic no", "id no"
            ),
            idRegex = Regex("""\b[A-Z]\d{7,8}\b"""),
            docTypes = setOf("driver_license", "id_card")
        ),
        CountryProfile(
            code = "MX",
            keywords = setOf(
                "mexico", "méxico", "estados unidos mexicanos",
                "credencial para votar", "ine", "ife", "curp", "rfc",
                "clave de elector"
            ),
            idRegex = Regex("""\b[A-Z]{4}\d{6}[HM][A-Z]{5}[A-Z0-9]\d\b"""), // CURP
            docTypes = setOf("id_card", "voter_card")
        ),
        CountryProfile(
            code = "CR",
            keywords = setOf(
                "costa rica", "república de costa rica",
                "tribunal supremo de elecciones", "cédula de identidad"
            ),
            idRegex = Regex("""\b\d-\d{4}-\d{4}\b"""),
            docTypes = setOf("cedula", "id_card")
        ),
        CountryProfile(
            code = "NI",
            keywords = setOf(
                "nicaragua", "república de nicaragua",
                "consejo supremo electoral", "cedula de identidad ciudadana"
            ),
            idRegex = Regex("""\b\d{3}-\d{6}-\d{4}[A-Z]\b"""),
            docTypes = setOf("cedula", "id_card")
        )
    )

    private val PASSPORT_KEYWORDS = setOf(
        "passport", "pasaporte", "travel document", "mrp", "p<"
    )
    private val MRZ_PATTERN = Regex("""[A-Z0-9<]{30,44}""")
    private val DRIVER_LICENSE_KEYWORDS = setOf(
        "driver license", "driver's license", "licencia de conducir",
        "licencia de conducción", "permiso de conducir", "driving licence"
    )

    // ─── Public API ───────────────────────────────────────────────────────────

    fun classify(ocrText: String): DocumentClassification {
        if (ocrText.isBlank()) return DocumentClassification.UNKNOWN

        val lower   = ocrText.lowercase()
        val signals = mutableListOf<String>()
        var totalScore = 0f

        // ── Signal 1: MRZ presence (+0.30) ───────────────────────────────────
        val hasMrz = MRZ_PATTERN.findAll(ocrText).count { it.value.length >= 30 } >= 2
        if (hasMrz) {
            totalScore += 0.30f
            signals += "mrz_detected(+0.30)"
        }

        // ── Signal 2: Document type keyword density (+0.15–+0.25) ────────────
        val isPassport = PASSPORT_KEYWORDS.any { lower.contains(it) }
        val isLicense  = DRIVER_LICENSE_KEYWORDS.any { lower.contains(it) }
        if (isPassport) { totalScore += 0.20f; signals += "passport_keyword(+0.20)" }
        if (isLicense)  { totalScore += 0.15f; signals += "license_keyword(+0.15)" }

        // ── Signal 3: Country matching ────────────────────────────────────────
        var bestCountry    = "UNKNOWN"
        var bestCountryScore = 0f
        var bestDocType    = if (isPassport) "PASSPORT" else if (isLicense) "DRIVER_LICENSE" else "ID_CARD"

        for (profile in COUNTRY_PROFILES) {
            var countryScore = 0f
            var hits = 0

            for (kw in profile.keywords) {
                if (lower.contains(kw)) { countryScore += 0.10f; hits++ }
            }

            profile.idRegex?.let { rx ->
                if (rx.containsMatchIn(ocrText)) {
                    countryScore += 0.25f
                    signals += "id_regex_${profile.code}(+0.25)"
                }
            }

            if (countryScore > bestCountryScore) {
                bestCountryScore = countryScore
                bestCountry = profile.code
                if (!isPassport && !isLicense) {
                    bestDocType = profile.docTypes.firstOrNull()?.uppercase() ?: "ID_CARD"
                }
            }
        }

        if (bestCountryScore > 0f) {
            totalScore += bestCountryScore.coerceAtMost(0.50f)
            signals += "country_${bestCountry}(+${String.format("%.2f", bestCountryScore.coerceAtMost(0.50f))})"
        }

        // ── Signal 4: Structural labels present (+0.05 each, max +0.20) ──────
        val structuralLabels = listOf(
            "apellidos", "nombres", "surname", "given name",
            "fecha de nacimiento", "date of birth", "expiry", "vencimiento"
        )
        val labelHits = structuralLabels.count { lower.contains(it) }
        val labelScore = (labelHits * 0.05f).coerceAtMost(0.20f)
        if (labelScore > 0f) {
            totalScore += labelScore
            signals += "structural_labels(hits=$labelHits, +${String.format("%.2f", labelScore)})"
        }

        // ── Signal 5: OCR character density (sanity check) ───────────────────
        // A real document should have >= 80 chars of readable text
        if (ocrText.length >= 80) { totalScore += 0.05f; signals += "text_density_ok(+0.05)" }

        // ── Normalise to 0–1 ──────────────────────────────────────────────────
        val confidence = totalScore.coerceIn(0f, 1f)

        log("classify → country=$bestCountry type=$bestDocType conf=${"%.2f".format(confidence)} signals=$signals")

        return DocumentClassification(
            country      = bestCountry,
            documentType = bestDocType.uppercase(),
            confidence   = confidence,
            signals      = signals
        )
    }

    private fun log(msg: String) = android.util.Log.d("DocClassifier", msg)
}

