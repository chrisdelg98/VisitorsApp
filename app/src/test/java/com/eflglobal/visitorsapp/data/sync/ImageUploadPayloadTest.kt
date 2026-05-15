package com.eflglobal.visitorsapp.data.sync

import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Unit tests for [ImageUploadPayload].
 *
 * We can only safely exercise the "forward untouched" branch on the JVM — the
 * recompression path uses `BitmapFactory.decodeFile`, which is Android-only.
 * The threshold logic, the multipart field name and the MIME type are however
 * pure-JVM and worth pinning down here because they are the contract with the
 * backend.
 *
 * The backend caps uploads at 5 MB; the soft target is 2 MB. Anything below
 * that target must be forwarded as-is, without re-encoding loss.
 */
class ImageUploadPayloadTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun `small file is forwarded untouched`() {
        val file = tmp.newFile("doc_front.jpg")
        // 128 KB — well below the 2 MB target.
        file.writeBytes(ByteArray(128 * 1024) { 0x42 })

        val (typePart, imagePart) = ImageUploadPayload.build("doc_front", file)

        // ── type part ──
        val typeBuffer = Buffer()
        typePart.writeTo(typeBuffer)
        assertEquals("doc_front", typeBuffer.readUtf8())
        assertEquals("text/plain", typePart.contentType().toString().substringBefore(";").trim())

        // ── image part ──
        val headers = imagePart.headers
        assertNotNull(headers)
        val disposition = headers!!["Content-Disposition"] ?: ""
        assertTrue(
            "Field name must be 'image' (backend contract): $disposition",
            disposition.contains("name=\"image\"")
        )
        assertTrue(
            "Filename must be preserved: $disposition",
            disposition.contains("doc_front.jpg")
        )

        // Body length must equal the original file length: nothing re-encoded.
        val bodyLen = imagePart.body.contentLength()
        assertEquals(file.length(), bodyLen)

        // MIME type must be image/jpeg.
        assertEquals(
            "image/jpeg",
            imagePart.body.contentType()?.toString()?.substringBefore(";")?.trim()
        )
    }

    @Test
    fun `target boundary file is still forwarded untouched`() {
        // Exactly 2 MB — must still take the "forward as-is" branch (`<=`).
        val file = tmp.newFile("profile.jpg")
        file.writeBytes(ByteArray(2 * 1024 * 1024) { 0x37 })

        val (_, imagePart) = ImageUploadPayload.build("personal_photo", file)

        // No re-encoding ⇒ body bytes equal file bytes.
        assertEquals(file.length(), imagePart.body.contentLength())
    }

    @Test
    fun `type label is propagated verbatim`() {
        val file = tmp.newFile("back.jpg")
        file.writeBytes(ByteArray(1024))

        for (type in listOf("personal_photo", "doc_front", "doc_back")) {
            val (typePart, _) = ImageUploadPayload.build(type, file)
            val buf = Buffer()
            typePart.writeTo(buf)
            assertEquals(type, buf.readUtf8())
        }
    }

    @Test
    fun `empty file still produces a valid multipart envelope`() {
        // Edge case: should not crash. The worker filters empty files before
        // calling this, but the helper itself should be tolerant.
        val file = tmp.newFile("empty.jpg")
        val (_, imagePart) = ImageUploadPayload.build("doc_front", file)
        assertEquals(0L, imagePart.body.contentLength())
    }
}


