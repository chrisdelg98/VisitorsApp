package com.eflglobal.visitorsapp.core.printing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Renders the visitor badge as an Android [Bitmap] using the native Canvas API.
 *
 * Target size : 696 × 480 px — optimised for Brother 62 mm continuous roll at 300 DPI.
 *
 * NEW LAYOUT (no bottom divider):
 * ┌──────────────────────────────────────────────────────────────┐
 * │  EFL Global                         VISITOR BADGE           │
 * ├──────────────────────────────────────────────────────────────┤
 * │ ┌─────────┐  FIRST NAME               [ VISITOR TYPE ]     │
 * │ │  PHOTO  │  LAST NAME                                      │
 * │ │(dithered)│  Company: EFL                                   │
 * │ │         │  Visiting: John Doe                              │
 * │ └─────────┘  Valid: 18/03/2026                               │
 * │ ┌─────────┐  Printed: 18/03/2026 10:30                      │
 * │ │   QR    │                                                  │
 * │ │  CODE   │                                                  │
 * │ └─────────┘                                                  │
 * └──────────────────────────────────────────────────────────────┘
 */
object BadgeBitmapRenderer {

    /** Width in pixels — fits 62 mm roll at 300 DPI with small margin. */
    const val BADGE_W = 696

    /** Height in pixels — compact layout, no excess dead space. */
    const val BADGE_H = 480

    // ── Colors — optimised for 1-bit thermal printing ──────────────────────
    private val CLR_TEXT     = Color.parseColor("#000000")   // Pure black
    private val CLR_GRAY     = Color.parseColor("#333333")   // Dark gray
    private val CLR_LABEL    = Color.parseColor("#555555")   // Label gray
    private val CLR_DIVIDER  = Color.parseColor("#666666")   // Divider
    private val CLR_CHIP_BG  = Color.parseColor("#D0D0D0")   // Pill bg
    private val CLR_PHOTO_BG = Color.parseColor("#E0E0E0")   // Photo placeholder

    // ── Margins ────────────────────────────────────────────────────────────
    private const val M = 20f   // outer margin

    // ── Data model ─────────────────────────────────────────────────────────

    data class RenderData(
        val firstName: String,
        val lastName: String,
        val company: String?,
        val visitingPerson: String,
        val visitorTypeLabel: String,
        val entryDate: Long,
        val profileBitmap: Bitmap?  = null,
        val qrBitmap: Bitmap?       = null,
        val logoBitmap: Bitmap?     = null,
        val labelBadgeTitle: String = "VISITOR BADGE",
        val labelCompany: String    = "Company:",
        val labelVisiting: String   = "Visiting:",
        val labelValid: String      = "Valid:",
        val labelValidFor: String   = "Valid for 24 hours from entry",
        val labelPrinted: String    = "Printed:"
    )

    // ── Public entry point ────────────────────────────────────────────────

    /**
     * Renders the badge into a fresh [Bitmap] of [BADGE_W] × [BADGE_H] pixels.
     * Call from a background thread; this method does NO coroutine switching.
     */
    fun render(data: RenderData): Bitmap {
        val bmp    = Bitmap.createBitmap(BADGE_W, BADGE_H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)

        // Header (logo + title)
        val headerBottom = drawHeader(canvas, data.labelBadgeTitle, data.logoBitmap)
        drawDivider(canvas, headerBottom + 4f)

        val contentTop = headerBottom + 12f
        val photoSize  = 175f          // bigger photo & QR (was 150)

        // Photo (left, dithered for thermal)
        drawPhoto(canvas, data.profileBitmap, M, contentTop, photoSize)

        // Visitor type pill (top-right of content area)
        drawVisitorTypePill(canvas, data.visitorTypeLabel, contentTop + 2f)

        // Data column (right of photo) — starts at pill top, spacing handled inside
        val textX = M + photoSize + 16f
        drawDataColumn(canvas, data, textX, contentTop + 2f)


        // QR code below photo, same width as photo
        val qrTop = contentTop + photoSize + 10f
        drawQrCode(canvas, data.qrBitmap, M, qrTop, photoSize)

        return bmp
    }

    // ── Section renderers ─────────────────────────────────────────────────

    /** Draws header: logo/text left + badge title right. Returns bottom Y. */
    private fun drawHeader(canvas: Canvas, title: String, logoBitmap: Bitmap? = null): Float {
        val y = M + 28f
        val logoH = 32f

        if (logoBitmap != null) {
            // Draw the actual logo image (grayscale)
            val aspect = logoBitmap.width.toFloat() / logoBitmap.height.toFloat()
            val logoW  = logoH * aspect
            val grayLogo = Bitmap.createBitmap(logoW.toInt(), logoH.toInt(), Bitmap.Config.ARGB_8888)
            val logoCanvas = Canvas(grayLogo)
            val src = Bitmap.createScaledBitmap(logoBitmap, logoW.toInt(), logoH.toInt(), true)
            logoCanvas.drawBitmap(src, 0f, 0f, Paint().apply {
                colorFilter = ColorMatrixColorFilter(ColorMatrix().also { it.setSaturation(0f) })
            })
            src.recycle()
            canvas.drawBitmap(grayLogo, M, y - logoH + 4f, null)
            grayLogo.recycle()

            // Badge title right of logo
            canvas.drawText(
                title, M + logoW + 10f, y,
                paint {
                    color     = CLR_TEXT
                    textSize  = 24f
                    typeface  = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    isAntiAlias = true
                }
            )
        } else {
            // Fallback: text-only "EFL Global"
            canvas.drawText(
                "EFL Global", M, y,
                paint {
                    color     = CLR_TEXT
                    textSize  = 26f
                    typeface  = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    isAntiAlias = true
                }
            )

            // Badge title on the right
            canvas.drawText(
                title, BADGE_W - M, y,
                paint {
                    color     = CLR_GRAY
                    textSize  = 22f
                    typeface  = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textAlign = Paint.Align.RIGHT
                    isAntiAlias = true
                }
            )
        }

        return y + 10f
    }

    /**
     * Draws the photo with Floyd-Steinberg dithering for optimal thermal print quality.
     * Enhances brightness and contrast before dithering so facial features are preserved.
     */
    private fun drawPhoto(canvas: Canvas, src: Bitmap?, x: Float, y: Float, size: Float) {
        val rect = RectF(x, y, x + size, y + size)

        // Background
        canvas.drawRoundRect(rect, 8f, 8f, paint { color = CLR_PHOTO_BG; isAntiAlias = true })

        if (src != null) {
            val dithered = toThermalOptimizedPhoto(src, size.toInt())
            canvas.drawBitmap(dithered, x, y, null)
            dithered.recycle()
        } else {
            // Placeholder "N/A"
            canvas.drawText(
                "N/A", x + size / 2f, y + size / 2f + 10f,
                paint {
                    color = CLR_GRAY; textSize = 30f
                    textAlign = Paint.Align.CENTER; isAntiAlias = true
                }
            )
        }

        // Border overlay
        canvas.drawRoundRect(rect, 8f, 8f, paint {
            color = CLR_DIVIDER; style = Paint.Style.STROKE
            strokeWidth = 2f; isAntiAlias = true
        })
    }

    /** Draws the data column: first name, last name, company, visiting, valid, printed. Returns bottom Y. */
    private fun drawDataColumn(canvas: Canvas, data: RenderData, x: Float, startY: Float): Float {
        val maxW = BADGE_W - x - M
        val detailGap = 10f            // consistent gap between detail lines
        val sectionGap = 18f           // gap pill→name AND name→details (equal)

        // Start below the pill with consistent gap
        var y = startY + 50f + sectionGap   // 50 = pill height, then gap

        // ── First name
        val namePaint = paint {
            color    = CLR_TEXT
            textSize = 30f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val firstName = data.firstName.uppercase(Locale.getDefault())
        y = drawWrappedText(canvas, firstName, x, y, maxW, namePaint, 35f)

        // ── Last name
        val lastName = data.lastName.uppercase(Locale.getDefault())
        y = drawWrappedText(canvas, lastName, x, y, maxW, namePaint, 35f)
        y += sectionGap                // same gap as pill→name

        // ── Company
        if (!data.company.isNullOrBlank()) {
            y = drawLabelValue(canvas, data.labelCompany, data.company.take(30), x, y)
            y += detailGap
        }

        // ── Visiting
        y = drawLabelValue(canvas, data.labelVisiting, data.visitingPerson.take(28), x, y)
        y += detailGap

        // ── Valid
        val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            .format(Date(data.entryDate))
        y = drawLabelValue(canvas, data.labelValid, dateStr, x, y)
        y += detailGap

        // ── Printed
        val printedStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            .format(Date())
        canvas.drawText(
            "${data.labelPrinted} $printedStr", x, y,
            paint { color = CLR_LABEL; textSize = 20f; isAntiAlias = true }
        )
        y += 24f

        return y
    }

    /** Draws the visitor type pill right-aligned — outline only, no fill. */
    private fun drawVisitorTypePill(canvas: Canvas, typeLabel: String, bottomY: Float) {
        val pillText  = typeLabel.uppercase(Locale.getDefault())
        val pillPaint = paint {
            color = Color.BLACK; textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true
        }
        val pillTextW = pillPaint.measureText(pillText)
        val pillH     = 30f
        val pillW     = pillTextW + 24f
        val pillX     = BADGE_W - M - pillW
        val pillY     = bottomY
        val pillRect  = RectF(pillX, pillY, pillX + pillW, pillY + pillH)
        // Outline only — no filled background
        canvas.drawRoundRect(pillRect, 50f, 50f, paint {
            color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 2.5f; isAntiAlias = true
        })
        canvas.drawText(pillText, pillX + 12f, pillY + pillH - 8f, pillPaint)
    }

    /** Draws QR code at the given position with the specified size. */
    private fun drawQrCode(canvas: Canvas, qrBitmap: Bitmap?, x: Float, y: Float, size: Float) {
        if (qrBitmap == null) return

        val rect = RectF(x, y, x + size, y + size)

        // White background + border
        canvas.drawRoundRect(rect, 6f, 6f, paint { color = Color.WHITE; isAntiAlias = true })
        canvas.drawRoundRect(rect, 6f, 6f, paint {
            color = CLR_DIVIDER; style = Paint.Style.STROKE; strokeWidth = 1.5f; isAntiAlias = true
        })

        val padding = 3f
        val scaled = Bitmap.createScaledBitmap(
            qrBitmap,
            (size - padding * 2).toInt(),
            (size - padding * 2).toInt(),
            false  // nearest-neighbor for crisp QR
        )
        canvas.drawBitmap(scaled, x + padding, y + padding, null)
        scaled.recycle()
    }

    // ── Drawing helpers ──────────────────────────────────────────────────

    private fun drawDivider(canvas: Canvas, y: Float) {
        canvas.drawLine(M, y, BADGE_W - M, y,
            paint { color = CLR_DIVIDER; strokeWidth = 2f; isAntiAlias = true })
    }

    /** Draws a gray [label] + bold [value] inline. Returns the Y after the line. */
    private fun drawLabelValue(canvas: Canvas, label: String, value: String, x: Float, y: Float): Float {
        val labelPaint = paint { color = CLR_LABEL; textSize = 22f; isAntiAlias = true }
        val valuePaint = paint {
            color    = CLR_TEXT; textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true
        }
        canvas.drawText(label, x, y, labelPaint)
        val lw = labelPaint.measureText(label)
        canvas.drawText(value, x + lw + 6f, y, valuePaint)
        return y + 29f
    }

    private fun drawWrappedText(
        canvas: Canvas, text: String, x: Float, y: Float,
        maxWidth: Float, paint: Paint, lineSpacing: Float = 32f
    ): Float {
        if (paint.measureText(text) <= maxWidth) {
            canvas.drawText(text, x, y, paint)
            return y + lineSpacing
        }
        val words = text.split(" ")
        var line  = ""
        var curY  = y
        for (word in words) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(candidate) <= maxWidth) {
                line = candidate
            } else {
                if (line.isNotEmpty()) {
                    canvas.drawText(line, x, curY, paint)
                    curY += lineSpacing
                }
                line = word
            }
        }
        if (line.isNotEmpty()) {
            canvas.drawText(line, x, curY, paint)
            curY += lineSpacing
        }
        return curY
    }

    // ── Bitmap utilities ──────────────────────────────────────────────────

    /**
     * Creates a thermal-print-optimized photo using advanced image processing:
     * 1. Square center crop
     * 2. Up-scale to 2× for better detail retention
     * 3. Grayscale conversion
     * 4. Adaptive histogram analysis (auto-detects dark/light photos)
     * 5. Histogram equalization — spreads tonal range
     * 6. Gamma correction — lifts shadows without blowing highlights
     * 7. Adaptive contrast + brightness
     * 8. Unsharp masking — sharpens edges for better facial definition
     * 9. Floyd-Steinberg dithering at 2× resolution
     * 10. Down-scale to final size (supersampled dithering = smoother dots)
     */
    private fun toThermalOptimizedPhoto(src: Bitmap, targetSize: Int): Bitmap {
        // Step 1: Square center crop
        val size = minOf(src.width, src.height)
        val xOff = (src.width  - size) / 2
        val yOff = (src.height - size) / 2
        val cropped = if (xOff == 0 && yOff == 0 && src.width == src.height) src
        else Bitmap.createBitmap(src, xOff, yOff, size, size)

        // Step 2: Scale to 2× target for supersampled processing
        val processSize = targetSize * 2
        val scaled = Bitmap.createScaledBitmap(cropped, processSize, processSize, true)
        if (cropped !== src) cropped.recycle()

        // Step 3: Convert to grayscale luminance array
        val w = scaled.width
        val h = scaled.height
        val pixels = FloatArray(w * h)

        for (py in 0 until h) {
            for (px in 0 until w) {
                val c = scaled.getPixel(px, py)
                val r = (c shr 16) and 0xFF
                val g = (c shr 8)  and 0xFF
                val b =  c         and 0xFF
                pixels[py * w + px] = 0.299f * r + 0.587f * g + 0.114f * b
            }
        }
        scaled.recycle()

        // Step 4: Analyse histogram — adaptive parameters
        val histogram = IntArray(256)
        var sumLuma = 0f
        var minLuma = 255f
        var maxLuma = 0f
        for (v in pixels) {
            val bin = v.toInt().coerceIn(0, 255)
            histogram[bin]++
            sumLuma += v
            if (v < minLuma) minLuma = v
            if (v > maxLuma) maxLuma = v
        }
        val meanLuma = sumLuma / pixels.size

        // Step 5: Histogram equalization — spread tonal range
        val cdf = IntArray(256)
        cdf[0] = histogram[0]
        for (i in 1..255) cdf[i] = cdf[i - 1] + histogram[i]
        val cdfMin = cdf.first { it > 0 }
        val totalPx = pixels.size
        val eqLut = FloatArray(256) { i ->
            ((cdf[i] - cdfMin).toFloat() / (totalPx - cdfMin).toFloat() * 255f).coerceIn(0f, 255f)
        }
        // Blend: 25% equalized + 75% original — minimal EQ to avoid darkening
        for (i in pixels.indices) {
            val orig = pixels[i]
            val eq   = eqLut[orig.toInt().coerceIn(0, 255)]
            pixels[i] = orig * 0.75f + eq * 0.25f
        }

        // Step 6: Adaptive gamma correction — aggressively lighten for thermal paper
        // Lower gamma = brighter. Thermal paper absorbs ink → needs extra lightness.
        val gamma = when {
            meanLuma < 70f  -> 0.35f   // very dark → very strong lift
            meanLuma < 100f -> 0.42f   // dark → strong lift
            meanLuma < 130f -> 0.50f   // slightly dark → significant lift
            meanLuma < 160f -> 0.58f   // normal → moderate lift
            meanLuma < 190f -> 0.68f   // bright → mild lift
            else            -> 0.78f   // very bright — still lift for thermal
        }
        val gammaLut = FloatArray(256) { i ->
            (255f * Math.pow((i / 255.0), gamma.toDouble()).toFloat()).coerceIn(0f, 255f)
        }
        for (i in pixels.indices) {
            pixels[i] = gammaLut[pixels[i].toInt().coerceIn(0, 255)]
        }

        // Step 7: Adaptive contrast + brightness boost
        // Re-compute mean after equalization + gamma
        var newMean = 0f
        for (v in pixels) newMean += v
        newMean /= pixels.size
        // Target mean ~190 for thermal — extra light so faces stay clear
        val brightShift = (190f - newMean).coerceIn(0f, 70f)
        val contrast = 1.08f   // very gentle contrast — preserves all detail
        for (i in pixels.indices) {
            pixels[i] = ((pixels[i] - 128f) * contrast + 128f + brightShift).coerceIn(0f, 255f)
        }

        // Step 8: Unsharp mask — light sharpening for facial definition
        // Simple 3×3 kernel: sharpened = original + strength × (original - blurred)
        val strength = 0.35f   // light touch to avoid amplifying dark spots
        val sharpened = FloatArray(pixels.size)
        for (py in 0 until h) {
            for (px in 0 until w) {
                val idx = py * w + px
                if (px == 0 || px == w - 1 || py == 0 || py == h - 1) {
                    sharpened[idx] = pixels[idx]
                    continue
                }
                // 3×3 box blur of neighbours
                val blur = (
                    pixels[(py-1)*w + (px-1)] + pixels[(py-1)*w + px] + pixels[(py-1)*w + (px+1)] +
                    pixels[ py   *w + (px-1)] + pixels[ py   *w + px] + pixels[ py   *w + (px+1)] +
                    pixels[(py+1)*w + (px-1)] + pixels[(py+1)*w + px] + pixels[(py+1)*w + (px+1)]
                ) / 9f
                sharpened[idx] = (pixels[idx] + strength * (pixels[idx] - blur)).coerceIn(0f, 255f)
            }
        }

        // Step 9: Floyd-Steinberg dithering at 2× resolution
        // Threshold 150 (above center 128) biases heavily toward white → lighter thermal print
        val ditherThreshold = 150f
        val dithered = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        for (py in 0 until h) {
            for (px in 0 until w) {
                val idx = py * w + px
                val oldVal = sharpened[idx]
                val newVal = if (oldVal < ditherThreshold) 0f else 255f
                val error  = oldVal - newVal

                dithered.setPixel(px, py, if (newVal < ditherThreshold) Color.BLACK else Color.WHITE)

                // Distribute error to neighbours
                if (px + 1 < w)
                    sharpened[idx + 1]     += error * 7f / 16f
                if (py + 1 < h) {
                    if (px - 1 >= 0)
                        sharpened[(py+1) * w + (px-1)] += error * 3f / 16f
                    sharpened[(py+1) * w + px]         += error * 5f / 16f
                    if (px + 1 < w)
                        sharpened[(py+1) * w + (px+1)] += error * 1f / 16f
                }
            }
        }

        // Step 10: Down-scale from 2× to target size (supersampled = smoother)
        val result = Bitmap.createScaledBitmap(dithered, targetSize, targetSize, true)
        if (result !== dithered) dithered.recycle()

        return result
    }

    /**
     * Converts a Bitmap to a square, grayscale crop centered on the image.
     * Used by the UI preview (not printing). Kept for backward compatibility.
     */
    fun toSquareGrayscale(src: Bitmap): Bitmap {
        val size  = minOf(src.width, src.height)
        val xOff  = (src.width  - size) / 2
        val yOff  = (src.height - size) / 2
        val cropped = if (xOff == 0 && yOff == 0 && src.width == src.height) src
        else Bitmap.createBitmap(src, xOff, yOff, size, size)

        val result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c      = Canvas(result)
        c.drawBitmap(cropped, 0f, 0f, Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().also { it.setSaturation(0f) })
        })
        if (cropped !== src) cropped.recycle()
        return result
    }

    // ── DSL helper ─────────────────────────────────────────────────────────

    private inline fun paint(block: Paint.() -> Unit) = Paint().apply(block)
}
