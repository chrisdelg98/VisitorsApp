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
 * Target size : 812 × 609 px  ≡  4 × 3 inch at 203 DPI (Zebra ZT230 native resolution).
 * Brother printers will scale this bitmap to fit their loaded media automatically.
 *
 * ┌─ HEADER (h=82) ──────────────────────────────────────────────┐
 * │  EFL Global [logo accent]              VISITOR BADGE         │
 * ├──────────────────────────────────────────────────────────────┤
 * │ [Photo 160×220]  VISITOR NAME (bold)          [QR 158×158]  │
 * │                  Company: …                                  │
 * │                  Visiting: …                                 │
 * │                  Date: dd/MM/yyyy                            │
 * ├──────────────────────────────────────────────────────────────┤
 * │  [VISITOR TYPE PILL]       Valid for 24 hours from entry     │
 * ├──────────────────────────────────────────────────────────────┤
 * │  Printed: dd/MM/yyyy HH:mm                                   │
 * ├──────────────────────────────────────────────────────────────┤
 * │  ▓▓▓▓▓▓▓▓▓▓▓▓▓  ORANGE FOOTER BAR (h=14)  ▓▓▓▓▓▓▓▓▓▓▓▓▓   │
 * └──────────────────────────────────────────────────────────────┘
 */
object BadgeBitmapRenderer {

    /** Width in pixels (4 in @ 203 DPI). */
    const val BADGE_W = 812

    /** Height in pixels (3 in @ 203 DPI). */
    const val BADGE_H = 609

    // ── Colors ─────────────────────────────────────────────────────────────
    private val CLR_HEADER   = Color.parseColor("#273647")   // Slate
    private val CLR_ORANGE   = Color.parseColor("#F68E2A")   // Brand orange
    private val CLR_TEXT     = Color.parseColor("#212121")   // Near-black
    private val CLR_GRAY     = Color.parseColor("#757575")   // Medium gray
    private val CLR_DIVIDER  = Color.parseColor("#BDBDBD")   // Light gray
    private val CLR_PILL_BG  = Color.parseColor("#F0F0F0")   // Pill background
    private val CLR_PHOTO_BG = Color.parseColor("#EEEEEE")   // Photo placeholder

    // ── Layout constants ───────────────────────────────────────────────────
    private const val HEADER_H   = 82f
    private const val PHOTO_X    = 18f
    private const val PHOTO_Y    = 92f
    private const val PHOTO_W    = 160f
    private const val PHOTO_H    = 220f
    private const val TEXT_X     = 192f
    private const val QR_SIZE    = 158f
    private const val QR_X       = BADGE_W - QR_SIZE - 16f   // 638f
    private const val QR_Y       = 92f
    private const val DIVIDER1_Y = 326f
    private const val PILL_Y     = 338f
    private const val DIVIDER2_Y = 398f
    private const val NOTE_Y     = 418f
    private const val PRINTED_Y  = 444f
    private const val FOOTER_Y   = BADGE_H - 14f

    // ── Data model ─────────────────────────────────────────────────────────

    data class RenderData(
        val visitorName: String,
        val company: String?,
        val visitingPerson: String,
        val visitorTypeLabel: String,
        val entryDate: Long,
        val profileBitmap: Bitmap?  = null,
        val qrBitmap: Bitmap?       = null,
        // Localized labels — populated from stringResource before calling render()
        val labelBadgeTitle: String = "VISITOR BADGE",
        val labelCompany: String    = "Company:",
        val labelVisiting: String   = "Visiting:",
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

        drawHeader(canvas, data.labelBadgeTitle)
        drawPhoto(canvas, data.profileBitmap)
        drawQr(canvas, data.qrBitmap)
        drawTextBlock(canvas, data)
        drawDivider(canvas, DIVIDER1_Y)
        drawVisitorTypePill(canvas, data.visitorTypeLabel, data.labelValidFor)
        drawDivider(canvas, DIVIDER2_Y)
        drawFooterText(canvas, data.labelPrinted, data.entryDate)
        drawOrangeBar(canvas)

        return bmp
    }

    // ── Section renderers ─────────────────────────────────────────────────

    private fun drawHeader(canvas: Canvas, title: String) {
        // Background bar
        canvas.drawRect(0f, 0f, BADGE_W.toFloat(), HEADER_H, paint { color = CLR_HEADER })

        // "EFL Global" on the left (orange accent)
        canvas.drawText(
            "EFL Global",
            20f, HEADER_H - 24f,
            paint {
                color     = CLR_ORANGE
                textSize  = 26f
                typeface  = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }
        )

        // Badge title on the right (white)
        canvas.drawText(
            title.uppercase(Locale.getDefault()),
            BADGE_W - 20f, HEADER_H - 24f,
            paint {
                color     = Color.WHITE
                textSize  = 24f
                typeface  = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.RIGHT
                isAntiAlias = true
            }
        )

        // Thin orange underline accent
        canvas.drawRect(
            0f, HEADER_H - 4f, BADGE_W.toFloat(), HEADER_H,
            paint { color = CLR_ORANGE }
        )
    }

    private fun drawPhoto(canvas: Canvas, src: Bitmap?) {
        val rect = RectF(PHOTO_X, PHOTO_Y, PHOTO_X + PHOTO_W, PHOTO_Y + PHOTO_H)
        if (src != null) {
            val gs     = toSquareGrayscale(src)
            val scaled = Bitmap.createScaledBitmap(gs, PHOTO_W.toInt(), PHOTO_H.toInt(), true)
            canvas.drawBitmap(scaled, PHOTO_X, PHOTO_Y, null)
            if (gs !== src) gs.recycle()
            if (scaled !== src) scaled.recycle()
        } else {
            // Placeholder
            canvas.drawRect(rect, paint { color = CLR_PHOTO_BG; isAntiAlias = true })
            canvas.drawRect(rect, paint {
                color = CLR_DIVIDER; style = Paint.Style.STROKE; strokeWidth = 1.5f; isAntiAlias = true
            })
            canvas.drawText(
                "N/A", PHOTO_X + PHOTO_W / 2f, PHOTO_Y + PHOTO_H / 2f + 10f,
                paint { color = CLR_GRAY; textSize = 28f; textAlign = Paint.Align.CENTER; isAntiAlias = true }
            )
        }
        // Border overlay
        canvas.drawRect(rect, paint {
            color = CLR_DIVIDER; style = Paint.Style.STROKE; strokeWidth = 1f; isAntiAlias = true
        })
    }

    private fun drawQr(canvas: Canvas, qrBitmap: Bitmap?) {
        if (qrBitmap == null) return
        val scaled = Bitmap.createScaledBitmap(qrBitmap, QR_SIZE.toInt(), QR_SIZE.toInt(), true)
        canvas.drawBitmap(scaled, QR_X, QR_Y, null)
        if (scaled !== qrBitmap) scaled.recycle()
        // Border
        canvas.drawRect(
            QR_X, QR_Y, QR_X + QR_SIZE, QR_Y + QR_SIZE,
            paint { color = CLR_DIVIDER; style = Paint.Style.STROKE; strokeWidth = 1f; isAntiAlias = true }
        )
    }

    private fun drawTextBlock(canvas: Canvas, data: RenderData) {
        val maxTextWidth = QR_X - TEXT_X - 12f   // don't overlap QR code

        var y = 130f

        // ── Visitor name ─────────────────────────────────────────────────
        val namePaint = paint {
            color    = CLR_TEXT
            textSize = 36f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val nameStr = data.visitorName.uppercase(Locale.getDefault())
        y = drawWrappedText(canvas, nameStr, TEXT_X, y, maxTextWidth, namePaint, lineSpacing = 40f)
        y += 14f

        // ── Company ──────────────────────────────────────────────────────
        if (!data.company.isNullOrBlank()) {
            y = drawLabelValue(
                canvas, data.labelCompany, data.company.take(30), TEXT_X, y, maxTextWidth
            )
            y += 4f
        }

        // ── Visiting ─────────────────────────────────────────────────────
        y = drawLabelValue(
            canvas, data.labelVisiting, data.visitingPerson.take(28), TEXT_X, y, maxTextWidth
        )
        y += 8f

        // ── Entry date ───────────────────────────────────────────────────
        val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            .format(Date(data.entryDate))
        canvas.drawText(
            dateStr, TEXT_X, y,
            paint { color = CLR_GRAY; textSize = 20f; isAntiAlias = true }
        )
    }

    private fun drawVisitorTypePill(canvas: Canvas, typeLabel: String, noteText: String) {
        val pillPaint = paint { color = CLR_PILL_BG; isAntiAlias = true }
        val textPaint = paint {
            color    = CLR_TEXT
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val text      = typeLabel.uppercase(Locale.getDefault())
        val textWidth = textPaint.measureText(text)
        val pillH     = 44f
        val pillLeft  = 20f
        val pillRight = pillLeft + textWidth + 30f
        val pillRect  = RectF(pillLeft, PILL_Y, pillRight, PILL_Y + pillH)
        canvas.drawRoundRect(pillRect, 22f, 22f, pillPaint)
        canvas.drawText(text, pillLeft + 15f, PILL_Y + pillH - 11f, textPaint)

        // Note text (smaller, gray) to the right of pill
        val noteX = pillRight + 18f
        if (noteX < BADGE_W - 20f) {
            canvas.drawText(
                noteText.take(55), noteX, PILL_Y + pillH - 11f,
                paint { color = CLR_GRAY; textSize = 18f; isAntiAlias = true }
            )
        }
    }

    private fun drawDivider(canvas: Canvas, y: Float) {
        canvas.drawLine(20f, y, BADGE_W - 20f, y,
            paint { color = CLR_DIVIDER; strokeWidth = 1.5f })
    }

    private fun drawFooterText(canvas: Canvas, printedLabel: String, entryDate: Long) {
        val entryFmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        canvas.drawText(
            "$printedLabel ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())}",
            20f, PRINTED_Y,
            paint { color = CLR_GRAY; textSize = 18f; isAntiAlias = true }
        )
    }

    private fun drawOrangeBar(canvas: Canvas) {
        canvas.drawRect(0f, FOOTER_Y, BADGE_W.toFloat(), BADGE_H.toFloat(),
            paint { color = CLR_ORANGE })
    }

    // ── Text helpers ──────────────────────────────────────────────────────

    /**
     * Draws [text] starting at ([x], [y]), wrapping onto a new line if [text] exceeds [maxWidth].
     * Returns the Y position after the last drawn line.
     */
    private fun drawWrappedText(
        canvas: Canvas, text: String, x: Float, y: Float,
        maxWidth: Float, paint: Paint, lineSpacing: Float = 36f
    ): Float {
        if (paint.measureText(text) <= maxWidth) {
            canvas.drawText(text, x, y, paint)
            return y + lineSpacing
        }
        // Split at words
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

    /** Draws a gray [label] followed by a bold [value]. Returns the Y after the line. */
    private fun drawLabelValue(
        canvas: Canvas, label: String, value: String,
        x: Float, y: Float, maxWidth: Float
    ): Float {
        val labelPaint = paint { color = CLR_GRAY; textSize = 22f; isAntiAlias = true }
        val valuePaint = paint {
            color    = CLR_TEXT
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText(label, x, y, labelPaint)
        val lw = labelPaint.measureText(label)
        canvas.drawText(value, x + lw + 6f, y, valuePaint)
        return y + 30f
    }

    // ── Bitmap utilities ──────────────────────────────────────────────────

    /**
     * Converts a Bitmap to a square, grayscale crop centered on the image.
     * The returned bitmap may be the same instance as [src] if already matching.
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

