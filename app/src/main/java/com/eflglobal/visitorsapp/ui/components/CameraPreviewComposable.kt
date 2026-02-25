package com.eflglobal.visitorsapp.ui.components

import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Handler
import android.os.Looper
import android.view.Surface
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

// ─── Simple camera preview ─────────────────────────────────────────────────────

@Composable
fun CameraPreviewComposable(
    onImageCaptured: (Bitmap) -> Unit,
    onError: (Exception) -> Unit = {},
    lensFacing: Int = CameraSelector.LENS_FACING_BACK,
    modifier: Modifier = Modifier,
    shouldCapture: Boolean = false,
    onCaptureComplete: () -> Unit = {}
) {
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val rotation       = Surface.ROTATION_90

    val previewView = remember {
        PreviewView(context).apply {
            scaleType         = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setTargetRotation(rotation)
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }
    val executor = remember { Executors.newSingleThreadExecutor() }

    LaunchedEffect(shouldCapture) {
        if (shouldCapture) {
            takePicture(imageCapture, executor, onImageCaptured, onError)
            onCaptureComplete()
        }
    }

    DisposableEffect(lensFacing) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val provider = future.get()
                val preview  = Preview.Builder().setTargetRotation(rotation).build()
                    .also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
            } catch (e: Exception) { onError(e) }
        }, ContextCompat.getMainExecutor(context))
        onDispose { executor.shutdown() }
    }

    Box(modifier = modifier) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
    }
}

// ─── Document scanning camera ──────────────────────────────────────────────────

/**
 * Camera composable for document scanning.
 *
 * - CAPTURE_MODE_MAXIMIZE_QUALITY for best image resolution.
 * - [ImageAnalysis] on a background thread computes per-frame sharpness from
 *   the YUV luma plane (no bitmap decode needed — very fast).
 * - Sharpness results are posted to the MAIN thread so Compose state writes
 *   are always thread-safe.
 * - The latest [onLiveSharpness] lambda is held in a stable ref (updated via
 *   SideEffect) so the background analyzer never captures a stale closure.
 * - Actual capture fires ONLY when [shouldCapture] transitions to true.
 */
@Composable
fun DocumentCameraPreviewComposable(
    onImageCaptured: (Bitmap) -> Unit,
    onError: (Exception) -> Unit = {},
    lensFacing: Int = CameraSelector.LENS_FACING_BACK,
    modifier: Modifier = Modifier,
    shouldCapture: Boolean = false,
    onCaptureComplete: () -> Unit = {},
    /** Invoked on the MAIN thread with each live-frame sharpness score. */
    onLiveSharpness: (Float) -> Unit = {}
) {
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val rotation       = Surface.ROTATION_90

    // Stable ref — always points to the latest lambda, safe to call from bg thread
    val sharpnessRef = remember { mutableStateOf(onLiveSharpness) }
    SideEffect { sharpnessRef.value = onLiveSharpness }

    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType         = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setTargetRotation(rotation)
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
    }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val captureExecutor  = remember { Executors.newSingleThreadExecutor() }

    val imageAnalysis = remember {
        ImageAnalysis.Builder()
            .setTargetRotation(rotation)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build().also { ia ->
                ia.setAnalyzer(analysisExecutor) { proxy ->
                    try {
                        val s = yuvSharpness(proxy)
                        mainHandler.post { sharpnessRef.value(s) }
                    } catch (_: Exception) {
                    } finally {
                        proxy.close()
                    }
                }
            }
    }

    LaunchedEffect(shouldCapture) {
        if (shouldCapture) {
            takePicture(imageCapture, captureExecutor, onImageCaptured, onError)
            onCaptureComplete()
        }
    }

    DisposableEffect(lensFacing) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val provider = future.get()
                val preview  = Preview.Builder().setTargetRotation(rotation).build()
                    .also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner, selector,
                    preview, imageCapture, imageAnalysis
                )
            } catch (e: Exception) { onError(e) }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            analysisExecutor.shutdown()
            captureExecutor.shutdown()
        }
    }

    Box(modifier = modifier) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
    }
}

// ─── Private helpers ───────────────────────────────────────────────────────────

private fun takePicture(
    imageCapture: ImageCapture,
    executor: java.util.concurrent.Executor,
    onSuccess: (Bitmap) -> Unit,
    onError: (Exception) -> Unit
) {
    imageCapture.takePicture(executor,
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(proxy: ImageProxy) {
                val bmp = proxyToBitmap(proxy); proxy.close(); onSuccess(bmp)
            }
            override fun onError(e: ImageCaptureException) = onError(e)
        }
    )
}

private fun proxyToBitmap(proxy: ImageProxy): Bitmap {
    // ImageCapture.OnImageCapturedCallback delivers the image in one of two formats:
    //   • JPEG  → planes[0] contains a full JPEG byte array → BitmapFactory works fine.
    //   • YUV_420_888 → three planes (Y, U, V) → must convert manually.
    //
    // We detect the format by checking the number of planes.
    // A single-plane proxy is always JPEG; three-plane is YUV.
    val rotationDegrees = proxy.imageInfo.rotationDegrees

    val bmp = if (proxy.planes.size == 1) {
        // ── JPEG path ─────────────────────────────────────────────────────────
        val buf   = proxy.planes[0].buffer
        val bytes = ByteArray(buf.remaining()).also { buf.get(it) }
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw IllegalStateException("BitmapFactory returned null for JPEG proxy")
    } else {
        // ── YUV_420_888 path ──────────────────────────────────────────────────
        yuvProxyToBitmap(proxy)
    }

    // Apply rotation so the bitmap is always upright
    return if (rotationDegrees != 0) {
        val m = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            .also { if (it !== bmp) bmp.recycle() }
    } else bmp
}

/**
 * Converts a YUV_420_888 [ImageProxy] to an ARGB [Bitmap].
 * Uses Android's YuvImage + JPEG compression as a reliable conversion path
 * that works across all Android versions and device manufacturers.
 */
private fun yuvProxyToBitmap(proxy: ImageProxy): Bitmap {
    val yPlane = proxy.planes[0]
    val uPlane = proxy.planes[1]
    val vPlane = proxy.planes[2]

    val yBuf = yPlane.buffer
    val uBuf = uPlane.buffer
    val vBuf = vPlane.buffer

    val ySize = yBuf.remaining()
    val uSize = uBuf.remaining()
    val vSize = vBuf.remaining()

    // Build NV21 byte array (Y plane then interleaved V,U)
    val nv21 = ByteArray(ySize + uSize + vSize)
    yBuf.get(nv21, 0, ySize)

    // U and V planes may be interleaved (NV12) or separate.
    // We reconstruct NV21 (V before U) regardless.
    val vBytes = ByteArray(vSize).also { vBuf.get(it) }
    val uBytes = ByteArray(uSize).also { uBuf.get(it) }

    var nv21Idx = ySize
    for (i in vBytes.indices) {
        nv21[nv21Idx++] = vBytes[i]
        if (i < uBytes.size) nv21[nv21Idx++] = uBytes[i]
    }

    val yuvImage = android.graphics.YuvImage(
        nv21,
        android.graphics.ImageFormat.NV21,
        proxy.width,
        proxy.height,
        null
    )
    val out = java.io.ByteArrayOutputStream()
    yuvImage.compressToJpeg(
        android.graphics.Rect(0, 0, proxy.width, proxy.height),
        95,
        out
    )
    val jpegBytes = out.toByteArray()
    return android.graphics.BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
        ?: throw IllegalStateException("BitmapFactory returned null for YUV→JPEG conversion")
}

/**
 * Computes Laplacian-variance sharpness directly from the YUV luma plane.
 * Sampled every 3rd pixel for speed.  Scale matches [DocumentValidator.calculateLaplacianVariance].
 */
private fun yuvSharpness(proxy: ImageProxy): Float {
    val plane       = proxy.planes[0]
    val buf         = plane.buffer
    val rowStride   = plane.rowStride
    val pixelStride = plane.pixelStride
    val w = proxy.width; val h = proxy.height
    val step = 3

    var sum = 0.0; var sumSq = 0.0; var count = 0

    for (row in step until h - step step step) {
        for (col in step until w - step step step) {
            val i   = row * rowStride + col * pixelStride
            val iUp = (row - step) * rowStride + col * pixelStride
            val iDn = (row + step) * rowStride + col * pixelStride
            val iL  = row * rowStride + (col - step) * pixelStride
            val iR  = row * rowStride + (col + step) * pixelStride
            if (iUp < 0 || iDn >= buf.capacity() || iL < 0 || iR >= buf.capacity()) continue

            val c   = buf[i].toInt()   and 0xFF
            val t   = buf[iUp].toInt() and 0xFF
            val b   = buf[iDn].toInt() and 0xFF
            val l   = buf[iL].toInt()  and 0xFF
            val r   = buf[iR].toInt()  and 0xFF
            val lap = kotlin.math.abs(4 * c - t - b - l - r).toDouble()
            sum += lap; sumSq += lap * lap; count++
        }
    }

    if (count == 0) return 0f
    val mean = sum / count
    return kotlin.math.sqrt((sumSq / count) - (mean * mean)).toFloat()
}
