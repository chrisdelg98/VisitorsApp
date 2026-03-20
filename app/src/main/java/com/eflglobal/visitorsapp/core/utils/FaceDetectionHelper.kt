package com.eflglobal.visitorsapp.core.utils

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.tasks.await

/**
 * Utility for on-device face detection using ML Kit.
 *
 * Used by the selfie-capture modals to verify a face is present
 * before (and after) the auto-capture.
 */
object FaceDetectionHelper {

    private val options = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setMinFaceSize(0.15f)          // at least 15 % of frame
        .build()

    private val detector = FaceDetection.getClient(options)

    /** Simple boolean check – at least one face present. */
    suspend fun hasFace(bitmap: Bitmap): Boolean = try {
        val image = InputImage.fromBitmap(bitmap, 0)
        val faces = detector.process(image).await()
        faces.isNotEmpty()
    } catch (_: Exception) {
        false
    }

    /** Returns the number of faces detected. */
    suspend fun faceCount(bitmap: Bitmap): Int = try {
        val image = InputImage.fromBitmap(bitmap, 0)
        val faces = detector.process(image).await()
        faces.size
    } catch (_: Exception) {
        0
    }

    // ── Quality-aware validation ─────────────────────────────────────────────

    enum class FaceResult {
        OK,               // face detected, good framing
        NO_FACE,          // no face at all
        PARTIAL_FACE,     // face detected but likely only lower portion (chin/mouth)
        TOO_SMALL,        // face too small in frame
        OFF_CENTER         // face too far from center
    }

    /**
     * Validates that a captured selfie contains a reasonably-framed face.
     *
     * Rules (intentionally lenient for cultural inclusivity):
     * 1. At least one face must be detected.
     * 2. The face bounding box must cover ≥ 8 % of image area.
     * 3. The **vertical center** of the face box must be in the upper 70 % of
     *    the image (rejects photos showing only chin / beard).
     * 4. The face box height must be ≥ 60 % of its width (rejects very wide,
     *    short detections that are just a jaw line).
     * 5. The horizontal center must be within the central 80 % of the image.
     */
    suspend fun validateFace(bitmap: Bitmap): FaceResult = try {
        val image = InputImage.fromBitmap(bitmap, 0)
        val faces = detector.process(image).await()

        if (faces.isEmpty()) {
            FaceResult.NO_FACE
        } else {
            // Pick the largest face
            val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }!!
            evaluateFace(face, bitmap.width, bitmap.height)
        }
    } catch (_: Exception) {
        FaceResult.NO_FACE
    }

    private fun evaluateFace(face: Face, imgW: Int, imgH: Int): FaceResult {
        val box = face.boundingBox
        val faceArea = box.width().toFloat() * box.height().toFloat()
        val imgArea  = imgW.toFloat() * imgH.toFloat()
        val areaRatio = faceArea / imgArea

        // 1. Too small
        if (areaRatio < 0.08f) return FaceResult.TOO_SMALL

        // 2. Partial face check — vertical center of face must be in upper 70 %
        val faceCenterY = (box.top + box.bottom) / 2f
        if (faceCenterY > imgH * 0.75f) return FaceResult.PARTIAL_FACE

        // 3. Aspect ratio check — reject very wide, very short detections (jaw only)
        val aspectRatio = box.height().toFloat() / box.width().toFloat().coerceAtLeast(1f)
        if (aspectRatio < 0.55f) return FaceResult.PARTIAL_FACE

        // 4. Horizontal centering (lenient)
        val faceCenterX = (box.left + box.right) / 2f
        if (faceCenterX < imgW * 0.10f || faceCenterX > imgW * 0.90f) return FaceResult.OFF_CENTER

        return FaceResult.OK
    }
}
