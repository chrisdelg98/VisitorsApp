package com.eflglobal.visitorsapp.ui.components

import android.graphics.Bitmap
import android.graphics.Matrix
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

/**
 * Composable que muestra preview de cámara con CameraX.
 *
 * @param onImageCaptured Callback cuando se captura una imagen
 * @param lensFacing Cámara a usar (frontal o trasera)
 */
@Composable
fun CameraPreviewComposable(
    onImageCaptured: (Bitmap) -> Unit,
    onError: (Exception) -> Unit = {},
    lensFacing: Int = CameraSelector.LENS_FACING_BACK,
    modifier: Modifier = Modifier,
    shouldCapture: Boolean = false,
    onCaptureComplete: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Forzar rotación landscape (90 grados) para mantener consistencia
    // Esto previene que CameraX intente auto-rotar
    val targetRotation = Surface.ROTATION_90

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
            // Configurar el layout para que no se auto-ajuste
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setTargetRotation(targetRotation)
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

    val executor = remember { Executors.newSingleThreadExecutor() }

    // Capturar imagen cuando shouldCapture cambie a true
    LaunchedEffect(shouldCapture) {
        if (shouldCapture) {
            captureImage(
                imageCapture = imageCapture,
                executor = executor,
                onImageCaptured = onImageCaptured,
                onError = onError
            )
            onCaptureComplete()
        }
    }

    DisposableEffect(lensFacing) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder()
                    .setTargetRotation(targetRotation)
                    .build()
                    .also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(lensFacing)
                    .build()

                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
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
 * Captura una imagen usando ImageCapture.
 */
private fun captureImage(
    imageCapture: ImageCapture,
    executor: java.util.concurrent.Executor,
    onImageCaptured: (Bitmap) -> Unit,
    onError: (Exception) -> Unit
) {
    imageCapture.takePicture(
        executor,
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(imageProxy: ImageProxy) {
                val bitmap = imageProxyToBitmap(imageProxy)
                imageProxy.close()
                onImageCaptured(bitmap)
            }

            override fun onError(exception: ImageCaptureException) {
                onError(exception)
            }
        }
    )
}

/**
 * Convierte ImageProxy a Bitmap.
 */
private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
    val buffer = imageProxy.planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)

    val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

    // Rotar según la orientación
    val matrix = Matrix().apply {
        postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
    }

    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

