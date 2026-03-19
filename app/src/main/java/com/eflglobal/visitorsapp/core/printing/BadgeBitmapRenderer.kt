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
        val photoSize  = 150f

        // Photo (left, dithered for thermal)
        drawPhoto(canvas, data.profileBitmap, M, contentTop, photoSize)

        // Visitor type pill (top-right, next to name area)
        drawVisitorTypePill(canvas, data.visitorTypeLabel, contentTop + 10f)

        // Data column (right of photo) — texts start from top
        val textX = M + photoSize + 16f
        drawDataColumn(canvas, data, textX, contentTop)


        // QR code below photo, same size as photo
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
        var y = startY + 24f

        // ── First name
        val namePaint = paint {
            color    = CLR_TEXT
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val firstName = data.firstName.uppercase(Locale.getDefault())
        y = drawWrappedText(canvas, firstName, x, y, maxW, namePaint, 32f)

        // ── Last name
        val lastName = data.lastName.uppercase(Locale.getDefault())
        y = drawWrappedText(canvas, lastName, x, y, maxW, namePaint, 32f)
        y += 8f

        // ── Company
        if (!data.company.isNullOrBlank()) {
            y = drawLabelValue(canvas, data.labelCompany, data.company.take(30), x, y)
            y += 4f
        }

        // ── Visiting
        y = drawLabelValue(canvas, data.labelVisiting, data.visitingPerson.take(28), x, y)
        y += 10f

        // ── Valid
        val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            .format(Date(data.entryDate))
        y = drawLabelValue(canvas, data.labelValid, dateStr, x, y)
        y += 4f

        // ── Printed
        val printedStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            .format(Date())
        canvas.drawText(
            "${data.labelPrinted} $printedStr", x, y,
            paint { color = CLR_LABEL; textSize = 19f; isAntiAlias = true }
        )
        y += 22f

        return y
    }

    /** Draws the visitor type pill right-aligned at the bottom of the badge. */
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
        canvas.drawRoundRect(pillRect, 50f, 50f, paint { color = CLR_CHIP_BG; isAntiAlias = true })
        canvas.drawRoundRect(pillRect, 50f, 50f, paint {
            color = CLR_DIVIDER; style = Paint.Style.STROKE; strokeWidth = 2f; isAntiAlias = true
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

        val padding = 6f
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
        val labelPaint = paint { color = CLR_LABEL; textSize = 21f; isAntiAlias = true }
        val valuePaint = paint {
            color    = CLR_TEXT; textSize = 21f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true
        }
        canvas.drawText(label, x, y, labelPaint)
        val lw = labelPaint.measureText(label)
        canvas.drawText(value, x + lw + 6f, y, valuePaint)
        return y + 27f
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
     * Creates a thermal-print-optimized photo using:
     * 1. Square center crop
     * 2. Grayscale conversion with enhanced brightness/contrast
     * 3. Floyd-Steinberg dithering for 1-bit rendering
     *
     * This produces MUCH better results than simple threshold on thermal printers
     * because it simulates gray tones through dot density patterns.
     */
    private fun toThermalOptimizedPhoto(src: Bitmap, targetSize: Int): Bitmap {
        // Step 1: Square center crop
        val size = minOf(src.width, src.height)
        val xOff = (src.width  - size) / 2
        val yOff = (src.height - size) / 2
        val cropped = if (xOff == 0 && yOff == 0 && src.width == src.height) src
        else Bitmap.createBitmap(src, xOff, yOff, size, size)

        // Step 2: Scale to target size
        val scaled = Bitmap.createScaledBitmap(cropped, targetSize, targetSize, true)
        if (cropped !== src) cropped.recycle()

        // Step 3: Convert to grayscale with brightness/contrast enhancement
        val w = scaled.width
        val h = scaled.height
        val pixels = FloatArray(w * h) // luminance 0..255

        for (y in 0 until h) {
            for (x in 0 until w) {
                val px = scaled.getPixel(x, y)
                val r = (px shr 16) and 0xFF
                val g = (px shr 8)  and 0xFF
                val b =  px         and 0xFF
                // Standard luminance
                var luma = (0.299f * r + 0.587f * g + 0.114f * b)

                // Enhance brightness (+30) and contrast (×1.4) for thermal printing
                luma = ((luma - 128f) * 1.4f + 128f + 30f).coerceIn(0f, 255f)

                pixels[y * w + x] = luma
            }
        }
        scaled.recycle()

        // Step 4: Floyd-Steinberg dithering
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                val oldVal = pixels[idx]
                val newVal = if (oldVal < 128f) 0f else 255f
                val error  = oldVal - newVal

                // Set pixel
                val c = if (newVal < 128f) Color.BLACK else Color.WHITE
                result.setPixel(x, y, c)

                // Distribute error to neighbours
                if (x + 1 < w)
                    pixels[idx + 1]     += error * 7f / 16f
                if (y + 1 < h) {
                    if (x - 1 >= 0)
                        pixels[(y + 1) * w + (x - 1)] += error * 3f / 16f
                    pixels[(y + 1) * w + x]            += error * 5f / 16f
                    if (x + 1 < w)
                        pixels[(y + 1) * w + (x + 1)]  += error * 1f / 16f
                }
            }
        }

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
