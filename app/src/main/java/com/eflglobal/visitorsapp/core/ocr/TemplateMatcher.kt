package com.eflglobal.visitorsapp.core.ocr

import android.util.Log
import com.eflglobal.visitorsapp.domain.model.DocumentTemplate

/**
 * ═══════════════════════════════════════════════════════════════════════════════
 * TemplateMatcher — data-driven document identification from the local catalog.
 *
 * Replaces the hardcoded COUNTRY_PROFILES logic in [DocumentClassifier]: instead
 * of baked-in tables, every PUBLISHED template scores itself against the scanned
 * side using its `signature`:
 *
 *   · id_regex match      — strong signal (+0.45)
 *   · keyword hits        — proportional, up to +0.35
 *   · has_mrz agreement   — +0.10 when the template expects an MRZ and one is present
 *   · has_pdf417 agreement— +0.10 when the template expects a barcode and one was read
 *   · aspect_ratio match  — +0.10 within tolerance
 *   · side agreement      — +0.05 same side / −0.15 opposite side
 *
 * The highest scorer above [IDENTIFY_THRESHOLD] wins. Below it → caller falls
 * back to the generic pipeline and reports the document as unrecognised.
 * ═══════════════════════════════════════════════════════════════════════════════
 */
object TemplateMatcher {

    /** Minimum score for a confident template identification. */
    const val IDENTIFY_THRESHOLD = 0.50f

    /** ±tolerance (fraction) for the aspect-ratio signal. */
    private const val ASPECT_TOLERANCE = 0.15f

    data class Match(
        val template: DocumentTemplate,
        val score: Float,
        val signals: List<String>
    )

    /**
     * @param templates    Local catalog (may be empty → returns null).
     * @param ocrText      Raw OCR text of the scanned side.
     * @param hasMrz       Whether an MRZ was detected in the text.
     * @param hasBarcode   Whether a barcode/QR was decoded on this side.
     * @param aspectRatio  width/height of the cropped document (null if unknown).
     * @param isBackSide   True when the scanned side is the reverse.
     */
    fun identify(
        templates: List<DocumentTemplate>,
        ocrText: String,
        hasMrz: Boolean,
        hasBarcode: Boolean,
        aspectRatio: Float?,
        isBackSide: Boolean
    ): Match? {
        if (templates.isEmpty() || ocrText.isBlank()) return null

        val lower = ocrText.lowercase()
        val scanSide = if (isBackSide) "back" else "front"

        var best: Match? = null

        for (t in templates) {
            val sig = t.signature
            val signals = mutableListOf<String>()
            var score = 0f

            // ── id_regex (strong) ──────────────────────────────────────────────
            sig.compiledIdRegex?.let { rx ->
                if (rx.containsMatchIn(ocrText)) {
                    score += 0.45f
                    signals += "id_regex(+0.45)"
                }
            }

            // ── keyword density ────────────────────────────────────────────────
            if (sig.keywords.isNotEmpty()) {
                val hits = sig.keywords.count { lower.contains(it.lowercase()) }
                if (hits > 0) {
                    val kwScore = (hits.toFloat() / sig.keywords.size * 0.35f).coerceAtMost(0.35f)
                    score += kwScore
                    signals += "keywords($hits/${sig.keywords.size},+${"%.2f".format(kwScore)})"
                }
            }

            // ── MRZ / barcode agreement ───────────────────────────────────────
            if (sig.hasMrz && hasMrz) { score += 0.10f; signals += "mrz(+0.10)" }
            if (sig.hasPdf417 && hasBarcode) { score += 0.10f; signals += "pdf417(+0.10)" }

            // ── aspect ratio ──────────────────────────────────────────────────
            if (sig.aspectRatio != null && aspectRatio != null && sig.aspectRatio > 0f) {
                val delta = kotlin.math.abs(aspectRatio - sig.aspectRatio) / sig.aspectRatio
                if (delta <= ASPECT_TOLERANCE) {
                    score += 0.10f
                    signals += "aspect(+0.10)"
                }
            }

            // ── side agreement ────────────────────────────────────────────────
            when {
                t.side.equals(scanSide, ignoreCase = true) -> { score += 0.05f; signals += "side_match(+0.05)" }
                else -> { score -= 0.15f; signals += "side_mismatch(-0.15)" }
            }

            score = score.coerceIn(0f, 1f)
            if (best == null || score > best!!.score) {
                best = Match(t, score, signals)
            }
        }

        val winner = best
        if (winner == null || winner.score < IDENTIFY_THRESHOLD) {
            Log.d("TemplateMatcher", "no template above threshold (best=${winner?.score})")
            return null
        }
        Log.d("TemplateMatcher", "matched '${winner.template.code}' score=${"%.2f".format(winner.score)} signals=${winner.signals}")
        return winner
    }
}
