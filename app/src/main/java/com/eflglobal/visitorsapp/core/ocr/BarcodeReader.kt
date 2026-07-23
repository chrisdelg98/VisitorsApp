package com.eflglobal.visitorsapp.core.ocr

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.tasks.await

/**
 * ═══════════════════════════════════════════════════════════════════════════════
 * BarcodeReader — reusable ML Kit barcode/QR reader focused on document BACKS.
 *
 * Detects and decodes ANY format present (PDF417, QR, CODE_128, …) and returns
 * the raw value plus a best-effort parse:
 *
 *   1. AAMVA  (US/CA driver licences & IDs — PDF417) → [AamvaParser]. Most
 *      accurate source for those documents → should win over text OCR.
 *   2. Known non-AAMVA (e.g. El Salvador DUI) → dedicated best-effort parser.
 *   3. Unknown → keep [rawValue], try to pull a document number by regex, and
 *      surface it so the caller can attach `barcode_raw` to a failure report.
 *
 * Never throws: on any error (no barcode, decode failure, model unavailable) it
 * returns null so the pipeline degrades gracefully to text OCR.
 * ═══════════════════════════════════════════════════════════════════════════════
 */
object BarcodeReader {

    private const val TAG = "BarcodeReader"

    enum class ParserType {
        /** US/CA AAMVA PDF417 — standard & reliable, authoritative over text OCR. */
        AAMVA,
        /**
         * Anything else (e.g. the El Salvador DUI back barcode, which carries only
         * verification data, NOT functional fields). We keep [rawValue] for
         * reporting but NEVER fill identity fields from it — those documents are
         * extracted via OCR templates instead.
         */
        UNKNOWN
    }

    /** A single decoded symbol. */
    data class RawBarcode(val rawValue: String, val format: Int)

    /**
     * Parsed outcome of the best barcode found on a side.
     *
     * @param rawValue  The raw decoded content (always present) — use for
     *                  `barcode_raw` when [parser] == UNKNOWN.
     * @param format    ML Kit [Barcode] format constant.
     */
    data class BarcodeReadResult(
        val rawValue: String,
        val format: Int,
        val parser: ParserType,
        val firstName: String? = null,
        val middleName: String? = null,
        val lastName: String? = null,
        val documentNumber: String? = null,
        val dateOfBirth: String? = null,
        val expiryDate: String? = null,
        val sex: String? = null
    ) {
        /** True when the parser produced usable identity fields (not just raw). */
        val hasParsedFields: Boolean get() =
            !firstName.isNullOrBlank() || !lastName.isNullOrBlank() || !documentNumber.isNullOrBlank()

        /** True when we could not interpret the content — report it for analysis. */
        val isUnknown: Boolean get() = parser == ParserType.UNKNOWN
    }

    // Generic doc-number GUESS for unknown formats — informational only; the
    // pipeline does NOT autofill from it (kept for the failure report / debugging).
    private val GENERIC_DOC_REGEX = Regex("""\b[A-Z0-9]{6,15}\b""")

    /**
     * Decode every barcode in [bitmap]. Empty list when none / on failure.
     */
    suspend fun scan(bitmap: Bitmap): List<RawBarcode> {
        return try {
            val scanner = BarcodeScanning.getClient()
            val image = InputImage.fromBitmap(bitmap, 0)
            val barcodes = scanner.process(image).await()
            barcodes.mapNotNull { bc ->
                bc.rawValue?.takeIf { it.isNotBlank() }?.let { RawBarcode(it, bc.format) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "barcode scan failed: ${e.message}")
            emptyList()
        }
    }

    /**
     * Scan [bitmap] and return the best interpreted barcode, or null when none
     * was found. AAMVA is preferred; otherwise the longest raw payload wins
     * (PDF417 carries far more than a stray CODE_128).
     */
    suspend fun read(bitmap: Bitmap): BarcodeReadResult? {
        val raw = scan(bitmap)
        if (raw.isEmpty()) return null

        // Prefer an AAMVA payload; else the richest (longest) barcode.
        val chosen = raw.firstOrNull { AamvaParser.isAamva(it.rawValue) }
            ?: raw.maxByOrNull { it.rawValue.length }!!

        return parse(chosen.rawValue, chosen.format)
    }

    /**
     * Route a raw decoded value to the right parser. Exposed for unit tests and
     * for callers that already have a decoded string (e.g. from ZXing).
     */
    fun parse(rawValue: String, format: Int = Barcode.FORMAT_UNKNOWN): BarcodeReadResult {
        // 1) AAMVA (US/CA).
        AamvaParser.parse(rawValue)?.let { a ->
            Log.d(TAG, "parsed AAMVA barcode (doc=${a.documentNumber})")
            return BarcodeReadResult(
                rawValue       = rawValue,
                format         = format,
                parser         = ParserType.AAMVA,
                firstName      = a.firstName,
                middleName     = a.middleName,
                lastName       = a.lastName,
                documentNumber = a.documentNumber,
                dateOfBirth    = a.dateOfBirth,
                expiryDate     = a.expiryDate,
                sex            = a.sex
            )
        }

        // 2) Non-AAMVA (e.g. El Salvador DUI) — no functional data to trust. Keep
        //    the raw content (for reporting) and a doc-number GUESS for reference,
        //    but the pipeline extracts these documents via OCR templates instead.
        val guess = GENERIC_DOC_REGEX.find(rawValue)?.value
            ?.takeIf { it.any(Char::isDigit) }
        Log.d(TAG, "non-AAMVA barcode format=$format len=${rawValue.length} docGuess=$guess")
        return BarcodeReadResult(
            rawValue       = rawValue,
            format         = format,
            parser         = ParserType.UNKNOWN,
            documentNumber = guess
        )
    }
}
