package com.eflglobal.visitorsapp.data.sync

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Helpers to turn an on-disk image file into the multipart parts expected by
 * `POST /v1/visits/{id}/images`.
 *
 * The backend caps uploads at 5 MB and we target <2 MB to keep the wire-time
 * down on weak LANs. The compression policy is best-effort: if the source is
 * already small enough we forward it untouched to avoid double encoding loss.
 */
internal object ImageUploadPayload {

    /** Backend hard limit. Anything bigger gets re-encoded aggressively. */
    private const val MAX_BYTES: Long = 5L * 1024 * 1024

    /** Soft target. Files above this get one re-encode pass at quality 70. */
    private const val TARGET_BYTES: Long = 2L * 1024 * 1024

    /** Multipart form-field name expected by the backend. */
    private const val FIELD_NAME = "image"

    /**
     * Builds the `(type, image)` multipart pair for a file on disk.
     *
     *  @param type      one of `personal_photo`, `doc_front`, `doc_back`
     *  @param file      the JPEG already persisted on the tablet
     */
    fun build(type: String, file: File): Pair<okhttp3.RequestBody, MultipartBody.Part> {
        val typePart = type.toRequestBody("text/plain".toMediaType())
        val imageMediaType = "image/jpeg".toMediaType()

        val imagePart: MultipartBody.Part = if (file.length() <= TARGET_BYTES) {
            // Forward untouched — keeps the original capture quality.
            MultipartBody.Part.createFormData(
                FIELD_NAME, file.name,
                file.asRequestBody(imageMediaType)
            )
        } else {
            val bytes = recompress(file, quality = 70)
                ?: file.readBytes()       // fallback if BitmapFactory fails
            val finalBytes = if (bytes.size <= MAX_BYTES) {
                bytes
            } else {
                recompress(file, quality = 55) ?: bytes
            }
            MultipartBody.Part.createFormData(
                FIELD_NAME, file.name,
                finalBytes.toRequestBody(imageMediaType)
            )
        }

        return typePart to imagePart
    }

    /** Re-encodes a JPEG at the given quality, returns null on decode failure. */
    private fun recompress(file: File, quality: Int): ByteArray? {
        val bitmap: Bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        return try {
            ByteArrayOutputStream().use { baos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
                baos.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }
}

