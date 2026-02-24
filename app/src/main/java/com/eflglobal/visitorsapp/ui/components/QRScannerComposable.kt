package com.eflglobal.visitorsapp.ui.components

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
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors

/**
 * Composable que escanea códigos QR usando CameraX + ZXing.
 *
 * @param onQRScanned Callback cuando se detecta un código QR válido
 * @param onError Callback cuando ocurre un error
 * @param lensFacing Cámara a usar (frontal o trasera)
 */
@Composable
fun QRScannerComposable(
    onQRScanned: (String) -> Unit,
    onError: (Exception) -> Unit = {},
    lensFacing: Int = CameraSelector.LENS_FACING_FRONT,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Forzar rotación landscape (90 grados) para mantener consistencia
    val targetRotation = Surface.ROTATION_90

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }
    val executor = remember { Executors.newSingleThreadExecutor() }

    var hasScanned by remember { mutableStateOf(false) }

    DisposableEffect(lensFacing) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                // Preview
                val preview = Preview.Builder()
                    .setTargetRotation(targetRotation)
                    .build()
                    .also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                // Image Analysis para escanear QR
                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetRotation(targetRotation)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(executor) { imageProxy ->
                            if (!hasScanned) {
                                val result = scanQRCode(imageProxy)
                                if (result != null) {
                                    hasScanned = true
                                    onQRScanned(result)
                                }
                            }
                            imageProxy.close()
                        }
                    }

                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(lensFacing)
                    .build()

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )

            } catch (e: Exception) {
                onError(e)
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            executor.shutdown()
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Escanea un código QR desde un ImageProxy usando ZXing.
 */
private fun scanQRCode(imageProxy: ImageProxy): String? {
    val buffer = imageProxy.planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)

    val source = PlanarYUVLuminanceSource(
        bytes,
        imageProxy.width,
        imageProxy.height,
        0,
        0,
        imageProxy.width,
        imageProxy.height,
        false
    )

    val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

    return try {
        val reader = MultiFormatReader()
        val hints = mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
            DecodeHintType.TRY_HARDER to true
        )
        reader.setHints(hints)

        val result = reader.decode(binaryBitmap)
        result.text
    } catch (e: Exception) {
        null
    }
}

/**
 * Clase para convertir ImageProxy a formato que ZXing puede leer.
 */
private class PlanarYUVLuminanceSource(
    private val yuvData: ByteArray,
    private val dataWidth: Int,
    private val dataHeight: Int,
    private val left: Int,
    private val top: Int,
    width: Int,
    height: Int,
    private val reverseHorizontal: Boolean
) : LuminanceSource(width, height) {

    override fun getRow(y: Int, row: ByteArray?): ByteArray {
        if (y < 0 || y >= height) {
            throw IllegalArgumentException("Requested row is outside the image: $y")
        }
        val width = width
        val result = row ?: ByteArray(width)
        val offset = (y + top) * dataWidth + left
        System.arraycopy(yuvData, offset, result, 0, width)

        if (reverseHorizontal) {
            result.reverse()
        }

        return result
    }

    override fun getMatrix(): ByteArray {
        val width = width
        val height = height

        if (width == dataWidth && height == dataHeight) {
            return yuvData
        }

        val area = width * height
        val matrix = ByteArray(area)
        var inputOffset = top * dataWidth + left

        if (width == dataWidth) {
            System.arraycopy(yuvData, inputOffset, matrix, 0, area)
            return matrix
        }

        for (y in 0 until height) {
            val outputOffset = y * width
            System.arraycopy(yuvData, inputOffset, matrix, outputOffset, width)
            inputOffset += dataWidth
        }

        return matrix
    }
}

