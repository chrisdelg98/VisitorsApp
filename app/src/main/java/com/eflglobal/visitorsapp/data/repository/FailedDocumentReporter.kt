package com.eflglobal.visitorsapp.data.repository

import android.content.Context
import android.util.Log
import com.eflglobal.visitorsapp.BuildConfig
import com.eflglobal.visitorsapp.data.remote.ApiClient
import com.eflglobal.visitorsapp.data.remote.SecureStore
import com.eflglobal.visitorsapp.data.remote.dto.BoxDto
import com.eflglobal.visitorsapp.data.remote.dto.FailedDocumentBody
import com.eflglobal.visitorsapp.data.remote.dto.OcrBlockDto
import com.google.mlkit.vision.text.Text
import java.util.ArrayDeque

/**
 * ═══════════════════════════════════════════════════════════════════════════════
 * FailedDocumentReporter — pushes unreadable documents to
 * `POST /api/v1/ocr/failed-documents` for later template authoring / barcode
 * analysis.
 *
 * PRIVACY (certification): we send the structured [OcrBlockDto] list and, when
 * present, the raw barcode string — NEVER the image, and `ocr_text` only masked.
 * We default to omitting `ocr_text` entirely.
 *
 * ANTI-SPAM: the backend caps at 20/min/station. We enforce a stricter
 * client-side token window plus content de-duplication so retries and rapid
 * re-scans of the same document don't flood the queue.
 *
 * Fire-and-forget: [report] never throws and returns quickly; the HTTP call is
 * best-effort. Reuses the shared Retrofit stack (X-API-Key auth interceptor).
 * ═══════════════════════════════════════════════════════════════════════════════
 */
object FailedDocumentReporter {

    private const val TAG = "FailedDocReporter"

    // Client-side rate window: keep well under the server's 20/min.
    private const val MAX_PER_WINDOW = 15
    private const val WINDOW_MS = 60_000L

    // Suppress duplicate reports of the same content within this span.
    private const val DEDUPE_MS = 30_000L

    private val recentTimestamps = ArrayDeque<Long>()
    @Volatile private var lastSignature: Int = 0
    @Volatile private var lastSignatureAt: Long = 0L
    private val lock = Any()

    /**
     * @param detectedType        Best-guess classification (or null).
     * @param detectedConfidence  0..1 confidence of that guess.
     * @param visionText          ML Kit result → becomes `ocr_blocks` (normalised boxes).
     * @param imageWidth/Height   Pixel size for box normalisation (skip blocks if <=0).
     * @param barcodeRaw          Raw decoded barcode when it couldn't be parsed.
     */
    suspend fun report(
        ctx: Context,
        detectedType: String?,
        detectedConfidence: Float?,
        visionText: Text?,
        imageWidth: Int,
        imageHeight: Int,
        barcodeRaw: String? = null
    ) {
        val appCtx = ctx.applicationContext
        if (!SecureStore.hasStation(appCtx)) return

        val blocks = buildBlocks(visionText, imageWidth, imageHeight)
        // Nothing worth sending → don't spend a rate-limit token.
        if (blocks.isEmpty() && barcodeRaw.isNullOrBlank()) return

        val signature = (blocks.joinToString("|") { it.text } + "#" + barcodeRaw.orEmpty()).hashCode()
        if (!allow(signature)) {
            Log.d(TAG, "report suppressed (rate/dedupe)")
            return
        }

        val body = FailedDocumentBody(
            detectedType       = detectedType,
            detectedConfidence = detectedConfidence,
            ocrBlocks          = blocks,
            ocrText            = null,               // privacy: prefer blocks; omit text
            barcodeRaw         = barcodeRaw?.takeIf { it.isNotBlank() },
            appVersion         = BuildConfig.VERSION_NAME
        )

        try {
            val response = ApiClient.get(appCtx).reportFailedDocument(body)
            if (response.isSuccessful) {
                Log.i(TAG, "reported failed document (${blocks.size} blocks, barcode=${barcodeRaw != null})")
            } else {
                Log.w(TAG, "failed-document report HTTP ${response.code()}")
            }
        } catch (e: Exception) {
            // Best-effort — offline / server issues must not affect the scan flow.
            Log.w(TAG, "failed-document report error: ${e.message}")
        }
    }

    // ── Rate limiting + dedupe ──────────────────────────────────────────────

    private fun allow(signature: Int): Boolean = synchronized(lock) {
        val now = System.currentTimeMillis()

        // De-dupe identical content.
        if (signature == lastSignature && now - lastSignatureAt < DEDUPE_MS) return false

        // Slide the window.
        while (true) {
            val oldest = recentTimestamps.peekFirst() ?: break
            if (now - oldest <= WINDOW_MS) break
            recentTimestamps.pollFirst()
        }
        if (recentTimestamps.size >= MAX_PER_WINDOW) return false

        recentTimestamps.addLast(now)
        lastSignature = signature
        lastSignatureAt = now
        true
    }

    // ── Block building (normalised 0..1 boxes) ──────────────────────────────

    private fun buildBlocks(
        visionText: Text?,
        imageWidth: Int,
        imageHeight: Int
    ): List<OcrBlockDto> {
        if (visionText == null || imageWidth <= 0 || imageHeight <= 0) return emptyList()
        val out = ArrayList<OcrBlockDto>()
        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                val box = line.boundingBox ?: continue
                val text = line.text.trim()
                if (text.isEmpty()) continue
                out.add(
                    OcrBlockDto(
                        text = text,
                        box = BoxDto(
                            x = (box.left.toFloat() / imageWidth).coerceIn(0f, 1f),
                            y = (box.top.toFloat() / imageHeight).coerceIn(0f, 1f),
                            w = (box.width().toFloat() / imageWidth).coerceIn(0f, 1f),
                            h = (box.height().toFloat() / imageHeight).coerceIn(0f, 1f)
                        )
                    )
                )
            }
        }
        return out
    }
}
