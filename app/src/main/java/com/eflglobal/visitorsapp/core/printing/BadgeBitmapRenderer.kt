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
 * The layout replicates the Compose UI preview card exactly:
 *
 * Target size : 696 × 480 px — optimised for Brother 62 mm continuous roll at 300 DPI.
 *
 * ┌──────────────────────────────────────────────────────────────┐
 * │  EFL Global (logo)              VISITOR BADGE (title)       │
 * ├──────────────────────────────────────────────────────────────┤
 * │ ┌─────────┐  FIRST NAME (bold)                              │
 * │ │  PHOTO  │  LAST NAME  (bold)                              │
 * │ │ (square)│  Company: EFL                                   │
 * │ │ grayscl │  Visiting: John Doe                             │
 * │ └─────────┘  Valid: 18/03/2026                              │
 * │              Printed: 18/03/2026 10:30                       │
 * ├──────────────────────────────────────────────────────────────┤
 * │  [ VISITOR ]                                   ┌──────┐     │
 * │  Valid for 24 hours from entry                  │  QR  │     │
 * │                                                 └──────┘     │
 * └──────────────────────────────────────────────────────────────┘
 */
object BadgeBitmapRenderer {

    /** Width in pixels — fits 62 mm roll at 300 DPI with small margin. */
    const val BADGE_W = 696

    /** Height in pixels — compact layout, no excess dead space. */
    const val BADGE_H = 480

    // ── Colors — optimised for 1-bit thermal printing ──────────────────────
    // The Brother QL thermal printer converts to black/white at luma < 128.
    // ALL text & lines MUST have luma < 128 to be visible on the printout.
    private val CLR_TEXT     = Color.parseColor("#000000")   // Pure black (luma 0)
    private val CLR_GRAY     = Color.parseColor("#333333")   // Dark gray (luma ~51)
    private val CLR_LABEL    = Color.parseColor("#555555")   // Label gray (luma ~85) — visible on thermal
    private val CLR_DIVIDER  = Color.parseColor("#666666")   // Divider (luma ~102) — visible on thermal
    private val CLR_CHIP_BG  = Color.parseColor("#D0D0D0")   // Pill bg — light on screen, border on print
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
        // Localized labels — populated from stringResource before calling render()
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

        val headerBottom = drawHeader(canvas, data.labelBadgeTitle)
        drawDivider(canvas, headerBottom + 4f)

        val contentTop = headerBottom + 12f
        val photoSize  = 160f
        drawPhoto(canvas, data.profileBitmap, M, contentTop, photoSize)

        val textX = M + photoSize + 16f
        val dataBottom = drawDataColumn(canvas, data, textX, contentTop)

        // Place divider below whichever is taller: photo or data column
        val sectionBottom = maxOf(contentTop + photoSize, dataBottom) + 14f
        drawDivider(canvas, sectionBottom)

        val footerTop = sectionBottom + 10f
        drawFooter(canvas, data, footerTop)

        return bmp
    }

    // ── Section renderers ─────────────────────────────────────────────────

    /** Draws header: "EFL Global" left + badge title right. Returns bottom Y. */
    private fun drawHeader(canvas: Canvas, title: String): Float {
        val y = M + 28f

        // "EFL Global" on the left
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

        return y + 10f
    }

    /** Draws grayscale square photo with border. */
    private fun drawPhoto(canvas: Canvas, src: Bitmap?, x: Float, y: Float, size: Float) {
        val rect = RectF(x, y, x + size, y + size)

        // Background
        canvas.drawRoundRect(rect, 8f, 8f, paint { color = CLR_PHOTO_BG; isAntiAlias = true })

        if (src != null) {
            val gs     = toSquareGrayscale(src)
            val scaled = Bitmap.createScaledBitmap(gs, size.toInt(), size.toInt(), true)
            canvas.drawBitmap(scaled, x, y, null)
            if (gs !== src) gs.recycle()
            if (scaled !== src) scaled.recycle()
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

        // ── First name ───────────────────────────────────────────────────
        val namePaint = paint {
            color    = CLR_TEXT
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val firstName = data.firstName.uppercase(Locale.getDefault())
        y = drawWrappedText(canvas, firstName, x, y, maxW, namePaint, 32f)

        // ── Last name ────────────────────────────────────────────────────
        val lastName = data.lastName.uppercase(Locale.getDefault())
        y = drawWrappedText(canvas, lastName, x, y, maxW, namePaint, 32f)
        y += 8f

        // ── Company ──────────────────────────────────────────────────────
        if (!data.company.isNullOrBlank()) {
            y = drawLabelValue(canvas, data.labelCompany, data.company.take(30), x, y)
            y += 4f
        }

        // ── Visiting ─────────────────────────────────────────────────────
        y = drawLabelValue(canvas, data.labelVisiting, data.visitingPerson.take(28), x, y)
        y += 10f

        // ── Valid ────────────────────────────────────────────────────────
        val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            .format(Date(data.entryDate))
        y = drawLabelValue(canvas, data.labelValid, dateStr, x, y)
        y += 4f

        // ── Printed ──────────────────────────────────────────────────────
        val printedStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            .format(Date())
        canvas.drawText(
            "${data.labelPrinted} $printedStr", x, y,
            paint { color = CLR_LABEL; textSize = 19f; isAntiAlias = true }
        )
        y += 22f

        return y
    }

    /** Draws footer: visitor type pill (left) + QR code (right) + note. */
    private fun drawFooter(canvas: Canvas, data: RenderData, top: Float) {
        // ── Visitor type pill ────────────────────────────────────────────
        val pillText  = data.visitorTypeLabel.uppercase(Locale.getDefault())
        val pillPaint = paint {
            color = Color.BLACK; textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true
        }
        val pillTextW = pillPaint.measureText(pillText)
        val pillH     = 34f
        val pillW     = pillTextW + 28f
        val pillRect  = RectF(M, top, M + pillW, top + pillH)
        canvas.drawRoundRect(pillRect, 50f, 50f, paint { color = CLR_CHIP_BG; isAntiAlias = true })
        // Dark border so pill outline is visible on thermal print
        canvas.drawRoundRect(pillRect, 50f, 50f, paint {
            color = CLR_DIVIDER; style = Paint.Style.STROKE; strokeWidth = 2f; isAntiAlias = true
        })
        canvas.drawText(pillText, M + 14f, top + pillH - 9f, pillPaint)

        // ── QR code (bottom-right) ───────────────────────────────────────
        if (data.qrBitmap != null) {
            val qrSize = 100f
            val qrX    = BADGE_W - M - qrSize
            val qrY    = top
            val qrRect = RectF(qrX, qrY, qrX + qrSize, qrY + qrSize)

            // White background + border
            canvas.drawRoundRect(qrRect, 6f, 6f, paint { color = Color.WHITE; isAntiAlias = true })
            canvas.drawRoundRect(qrRect, 6f, 6f, paint {
                color = CLR_DIVIDER; style = Paint.Style.STROKE; strokeWidth = 1.5f; isAntiAlias = true
            })

            val padding = 4f
            val scaled = Bitmap.createScaledBitmap(
                data.qrBitmap,
                (qrSize - padding * 2).toInt(),
                (qrSize - padding * 2).toInt(),
                true
            )
            canvas.drawBitmap(scaled, qrX + padding, qrY + padding, null)
            scaled.recycle()
        }
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
