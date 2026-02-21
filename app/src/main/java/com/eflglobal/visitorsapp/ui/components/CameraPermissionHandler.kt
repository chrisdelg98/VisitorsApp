package com.eflglobal.visitorsapp.ui.components

import android.Manifest
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale

/**
 * Composable para manejar permisos de cámara.
 *
 * @param onPermissionGranted Callback cuando se concede el permiso
 * @param onPermissionDenied Callback cuando se niega el permiso
 * @param content Contenido a mostrar cuando el permiso está concedido
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraPermissionHandler(
    onPermissionGranted: () -> Unit = {},
    onPermissionDenied: () -> Unit = {},
    content: @Composable () -> Unit
) {
    val cameraPermissionState = rememberPermissionState(
        permission = Manifest.permission.CAMERA
    )

    LaunchedEffect(cameraPermissionState.status) {
        if (cameraPermissionState.status.isGranted) {
            onPermissionGranted()
        }
    }

    when {
        cameraPermissionState.status.isGranted -> {
            // Permiso concedido, mostrar contenido
            content()
        }
        cameraPermissionState.status.shouldShowRationale -> {
            // Mostrar explicación y solicitar permiso
            LaunchedEffect(Unit) {
                cameraPermissionState.launchPermissionRequest()
            }
        }
        else -> {
            // Primera vez solicitando permiso
            LaunchedEffect(Unit) {
                cameraPermissionState.launchPermissionRequest()
            }
        }
    }
}

