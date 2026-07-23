package com.eflglobal.visitorsapp.core.ocr

import android.graphics.Rect
import android.util.Log
import com.eflglobal.visitorsapp.domain.model.TemplateField
import com.google.mlkit.vision.text.Text

/**
 * ═══════════════════════════════════════════════════════════════════════════════
 * TemplateFieldExtractor — pulls field VALUES out of ML Kit OCR using a matched
 * template's per-field descriptors.
 *
 * Two geometric methods (mrz / pdf417 methods are handled upstream in the
 * pipeline, not here):
 *
 *   · "anchor" — find the label text in the blocks, then take the value in the
 *     requested direction (right/below/left/above/same_line) by RELATIVE
 *     geometry. Robust to tilt/scale; needs no rectification.
 *
 *   · "zone"   — normalise every OCR box to 0..1 over the (cropped) image and
 *     take the text whose box-centre falls inside region {x,y,w,h}. When the
 *     image size is unknown we skip zone (the pipeline prioritises anchor).
 *
 * Every value is scored against the field's `validation` (regex/length/charset),
 * which feeds the confidence handed back to the pipeline.
 * ═══════════════════════════════════════════════════════════════════════════════
 */
object TemplateFieldExtractor {

    /** Base confidence before validation adjustment. */
    private const val ANCHOR_BASE = 0.78f
    private const val ZONE_BASE = 0.72f

    data class Extracted(
        val field: String,
        val value: String,
        val confidence: Float
    )

    /** A single OCR line with its pixel bounding box. */
    private data class Unit(val text: String, val box: Rect)

    /**
     * @param imageWidth/imageHeight  Pixel size of the OCR'd bitmap; pass <=0 to
     *        disable zone extraction (anchor still works).
     * @return map keyed by canonical field name (e.g. "first_name").
     */
    fun extract(
        fields: List<TemplateField>,
        visionText: Text?,
        imageWidth: Int,
        imageHeight: Int
    ): Map<String, Extracted> {
        if (visionText == null || fields.isEmpty()) return emptyMap()

        val units = collectUnits(visionText)
        if (units.isEmpty()) return emptyMap()

        val out = LinkedHashMap<String, Extracted>()
        for (f in fields) {
            val value = when (f.method.lowercase()) {
                "anchor" -> extractAnchor(f, units)
                "zone"   -> extractZone(f, units, imageWidth, imageHeight)
                else     -> null   // mrz / pdf417 handled by the pipeline
            } ?: continue

            val clean = value.trim()
            if (clean.isBlank()) continue

            val validationScore = f.validation?.score(clean) ?: 1f
            val base = if (f.method.equals("zone", true)) ZONE_BASE else ANCHOR_BASE
            val confidence = (base * (0.5f + 0.5f * validationScore)).coerceIn(0f, 1f)

            out[f.field] = Extracted(f.field, clean, confidence)
            Log.d("TemplateFieldExtractor", "field='${f.field}' method='${f.method}' value='$clean' val=$validationScore conf=${"%.2f".format(confidence)}")
        }
        return out
    }

    // ── Anchor ──────────────────────────────────────────────────────────────

    private fun extractAnchor(field: TemplateField, units: List<Unit>): String? {
        val anchor = field.anchor ?: return null
        val label = anchor.label.trim().lowercase()
        if (label.isBlank()) return null

        // The unit that contains the label text.
        val labelUnit = units.firstOrNull { it.text.lowercase().contains(label) } ?: return null
        val lb = labelUnit.box

        // same_line: if the label unit carries extra text after the label, use it.
        if (anchor.position.equals("same_line", ignoreCase = true)) {
            val inline = stripLabel(labelUnit.text, anchor.label)
            if (!inline.isNullOrBlank()) return inline
        }

        val candidates = units.filter { it !== labelUnit && it.text.isNotBlank() }
        val chosen = when (anchor.position.lowercase()) {
            "right", "same_line" -> candidates
                .filter { verticallyOverlaps(lb, it.box) && it.box.left >= lb.left }
                .minByOrNull { it.box.left }
            "left" -> candidates
                .filter { verticallyOverlaps(lb, it.box) && it.box.right <= lb.right }
                .maxByOrNull { it.box.right }
            "below" -> candidates
                .filter { horizontallyOverlaps(lb, it.box) && it.box.top >= lb.centerY() }
                .minByOrNull { it.box.top }
            "above" -> candidates
                .filter { horizontallyOverlaps(lb, it.box) && it.box.bottom <= lb.centerY() }
                .maxByOrNull { it.box.bottom }
            else -> null
        }
        return chosen?.text?.trim()
    }

    /** Removes the label prefix from a same-line unit, returning the trailing value. */
    private fun stripLabel(unitText: String, label: String): String? {
        val idx = unitText.lowercase().indexOf(label.trim().lowercase())
        if (idx < 0) return null
        val after = unitText.substring(idx + label.trim().length)
            .trimStart(':', ' ', '-', '.', '/')
            .trim()
        return after.takeIf { it.isNotBlank() }
    }

    // ── Zone ────────────────────────────────────────────────────────────────

    private fun extractZone(
        field: TemplateField,
        units: List<Unit>,
        imageWidth: Int,
        imageHeight: Int
    ): String? {
        val region = field.region ?: return null
        if (imageWidth <= 0 || imageHeight <= 0) return null

        val inside = units.filter { u ->
            val cx = u.box.centerX().toFloat() / imageWidth
            val cy = u.box.centerY().toFloat() / imageHeight
            region.contains(cx, cy)
        }
        if (inside.isEmpty()) return null

        // Reading order: top-to-bottom, then left-to-right.
        return inside
            .sortedWith(compareBy({ it.box.top }, { it.box.left }))
            .joinToString(" ") { it.text.trim() }
            .trim()
            .takeIf { it.isNotBlank() }
    }

    // ── Geometry helpers ──────────────────────────────────────────────────────

    private fun collectUnits(visionText: Text): List<Unit> {
        val units = ArrayList<Unit>()
        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                val box = line.boundingBox ?: continue
                val text = line.text
                if (text.isNotBlank()) units.add(Unit(text, box))
            }
        }
        return units
    }

    private fun verticallyOverlaps(a: Rect, b: Rect): Boolean {
        val overlap = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
        val minHeight = minOf(a.height(), b.height()).coerceAtLeast(1)
        return overlap > minHeight * 0.35f
    }

    private fun horizontallyOverlaps(a: Rect, b: Rect): Boolean {
        val overlap = minOf(a.right, b.right) - maxOf(a.left, b.left)
        val minWidth = minOf(a.width(), b.width()).coerceAtLeast(1)
        return overlap > minWidth * 0.25f
    }
}
