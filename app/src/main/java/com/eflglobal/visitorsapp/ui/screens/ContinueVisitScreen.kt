package com.eflglobal.visitorsapp.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.camera.core.CameraSelector
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eflglobal.visitorsapp.R
import com.eflglobal.visitorsapp.ui.components.CameraPermissionHandler
import com.eflglobal.visitorsapp.ui.components.QRScannerComposable
import com.eflglobal.visitorsapp.ui.theme.OrangePrimary
import com.eflglobal.visitorsapp.ui.theme.SlatePrimary
import com.eflglobal.visitorsapp.ui.viewmodel.ContinueVisitUiState
import com.eflglobal.visitorsapp.ui.viewmodel.ContinueVisitViewModel
import com.eflglobal.visitorsapp.ui.viewmodel.ViewModelFactory
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContinueVisitScreen(
    onFinish: () -> Unit,
    onBack: () -> Unit,
    viewModel: ContinueVisitViewModel? = null,
    selectedLanguage: String = "es"
) {
    val context = LocalContext.current
    val vm = viewModel ?: viewModel(factory = ViewModelFactory(context))
    val uiState by vm.uiState.collectAsState()

    var showQRScanner by remember { mutableStateOf(false) }

    // Reset state on enter / clean up on leave
    LaunchedEffect(Unit) { vm.resetState() }
    DisposableEffect(Unit) { onDispose { vm.resetState() } }

    // When a scan succeeds, hide the camera
    LaunchedEffect(uiState) {
        when (uiState) {
            is ContinueVisitUiState.ConfirmationNeeded,
            is ContinueVisitUiState.ReentryConfirmed,
            is ContinueVisitUiState.ContinuationConfirmed,
            is ContinueVisitUiState.Error -> showQRScanner = false
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.continue_visit_title), fontSize = 18.sp)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (val state = uiState) {

                // ── IDLE / SCANNING ───────────────────────────────────────────────
                is ContinueVisitUiState.Idle,
                is ContinueVisitUiState.Loading -> {
                    IdleContent(
                        isLoading    = state is ContinueVisitUiState.Loading,
                        showScanner  = showQRScanner,
                        onStartScan  = { showQRScanner = true },
                        onQRScanned  = { qr -> vm.lookupByQR(qr) },
                        onCancelScan = { showQRScanner = false }
                    )
                }

                // ── CONFIRMATION MODAL ────────────────────────────────────────────
                is ContinueVisitUiState.ConfirmationNeeded -> {
                    // Keep idle content in background
                    IdleContent(
                        isLoading    = false,
                        showScanner  = false,
                        onStartScan  = {},
                        onQRScanned  = {},
                        onCancelScan = {}
                    )

                    ConfirmationModal(
                        state      = state,
                        onConfirm  = { vm.confirm() },
                        onDismiss  = {
                            vm.resetState()
                            showQRScanner = false
                        }
                    )
                }

                // ── SUCCESS — SAME STATION ────────────────────────────────────────
                is ContinueVisitUiState.ReentryConfirmed -> {
                    SuccessContent(
                        isReentry    = true,
                        personName   = state.personName,
                        reentryCount = state.reentryCount,
                        onScanAgain  = {
                            vm.resetState()
                            showQRScanner = true
                        },
                        onGoHome     = onFinish
                    )
                }

                // ── SUCCESS — CROSS STATION ───────────────────────────────────────
                is ContinueVisitUiState.ContinuationConfirmed -> {
                    SuccessContent(
                        isReentry    = false,
                        personName   = state.personName,
                        reentryCount = null,
                        onScanAgain  = {
                            vm.resetState()
                            showQRScanner = true
                        },
                        onGoHome     = onFinish
                    )
                }

                // ── ERROR ─────────────────────────────────────────────────────────
                is ContinueVisitUiState.Error -> {
                    ErrorContent(
                        message      = errorMessage(state.message),
                        onRetry      = {
                            vm.resetState()
                            showQRScanner = true
                        },
                        onGoHome     = onFinish
                    )
                }
            }
        }
    }
}

// ── Helpers ─────────────────────────────────────────────────────────────────

@Composable
private fun errorMessage(code: String): String = when (code) {
    "visit_not_found"  -> stringResource(R.string.continue_visit_error_not_found)
    "person_not_found" -> stringResource(R.string.continue_visit_error_person)
    "visit_not_today"  -> stringResource(R.string.continue_visit_error_not_today)
    else               -> stringResource(R.string.continue_visit_error_generic)
}

// ── Idle / Scanner content ───────────────────────────────────────────────────

@Composable
private fun IdleContent(
    isLoading: Boolean,
    showScanner: Boolean,
    onStartScan: () -> Unit,
    onQRScanned: (String) -> Unit,
    onCancelScan: () -> Unit
) {
    Box(
        modifier         = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier            = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .padding(horizontal = 40.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        Icon(
            imageVector      = Icons.Default.QrCodeScanner,
            contentDescription = null,
            tint             = OrangePrimary,
            modifier         = Modifier.size(72.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text       = stringResource(R.string.continue_visit_instructions),
            style      = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
            textAlign  = TextAlign.Center,
            color      = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(40.dp))

        if (isLoading) {
            CircularProgressIndicator(color = OrangePrimary)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text  = stringResource(R.string.continue_visit_scanning),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        } else {
            Button(
                onClick  = onStartScan,
                shape    = RoundedCornerShape(14.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = OrangePrimary),
                modifier = Modifier.fillMaxWidth(0.7f).height(52.dp)
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text       = stringResource(R.string.continue_visit_title),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        } // close Column
    } // close centering Box

    // Full-screen QR scanner overlay
    if (showScanner) {
        CameraPermissionHandler {
            Box(modifier = Modifier.fillMaxSize()) {
                QRScannerComposable(
                    onQRScanned = onQRScanned,
                    lensFacing  = CameraSelector.LENS_FACING_FRONT,
                    modifier    = Modifier.fillMaxSize()
                )

                // Cancel button overlay
                Box(
                    modifier          = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment  = Alignment.BottomCenter
                ) {
                    OutlinedButton(
                        onClick  = onCancelScan,
                        shape    = RoundedCornerShape(14.dp),
                        colors   = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        ),
                        border   = androidx.compose.foundation.BorderStroke(1.dp, Color.White)
                    ) {
                        Text(
                            text       = stringResource(R.string.cancel),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

// ── Confirmation Modal ───────────────────────────────────────────────────────

@Composable
private fun ConfirmationModal(
    state: ContinueVisitUiState.ConfirmationNeeded,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val lookup = state.lookup

    // Auto-confirm countdown
    var secondsLeft by remember { mutableIntStateOf(lookup.autoConfirmSeconds) }
    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            delay(1_000)
            secondsLeft--
        }
        onConfirm()
    }

    // Photo bitmap (loaded off the main thread via remember)
    val profileBitmap: Bitmap? = remember(lookup.profilePhotoPath) {
        lookup.profilePhotoPath?.let {
            runCatching { BitmapFactory.decodeFile(it) }.getOrNull()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties       = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier  = Modifier
                .fillMaxWidth(0.60f)
                .wrapContentHeight(),
            shape     = RoundedCornerShape(24.dp),
            colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier            = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // ── Station badge ────────────────────────────────────────────
                val (badgeColor, badgeLabel) = if (lookup.isSameStation)
                    OrangePrimary.copy(alpha = 0.15f) to stringResource(R.string.continue_visit_same_station)
                else
                    Color(0xFF1565C0).copy(alpha = 0.12f) to stringResource(R.string.continue_visit_diff_station)

                val (textColor) = if (lookup.isSameStation)
                    listOf(OrangePrimary)
                else
                    listOf(Color(0xFF1565C0))

                Surface(
                    color  = badgeColor,
                    shape  = RoundedCornerShape(20.dp),
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Text(
                        text      = badgeLabel,
                        color     = textColor,
                        fontSize  = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier  = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }

                // ── Profile photo ────────────────────────────────────────────
                Box(
                    modifier         = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(OrangePrimary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (profileBitmap != null) {
                        Image(
                            bitmap             = profileBitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier           = Modifier.fillMaxSize(),
                            contentScale       = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector      = Icons.Default.Person,
                            contentDescription = null,
                            tint             = OrangePrimary,
                            modifier         = Modifier.size(48.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── Person name ──────────────────────────────────────────────
                Text(
                    text       = lookup.personFullName,
                    style      = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                    fontWeight = FontWeight.Bold,
                    color      = SlatePrimary,
                    textAlign  = TextAlign.Center
                )

                // ── "Visiting:" row ──────────────────────────────────────────
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text  = "${stringResource(R.string.continue_visit_visiting)}: ${lookup.visit.visitingPersonName}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )

                // ── Already-ended warning ────────────────────────────────────
                if (lookup.hasAlreadyEnded) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color  = Color(0xFFFFF3E0),
                        shape  = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier          = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector      = Icons.Default.Warning,
                                contentDescription = null,
                                tint             = Color(0xFFF57C00),
                                modifier         = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text     = stringResource(R.string.continue_visit_already_ended),
                                fontSize = 11.sp,
                                color    = Color(0xFFF57C00),
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                // ── Different-station info ───────────────────────────────────
                if (!lookup.isSameStation && lookup.currentStationName != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier          = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StationChip(
                            label = stringResource(R.string.continue_visit_current_station),
                            name  = lookup.currentStationName
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ── Auto-confirm progress ────────────────────────────────────
                val progress = secondsLeft.toFloat() / lookup.autoConfirmSeconds.toFloat()
                LinearProgressIndicator(
                    progress      = { progress },
                    modifier      = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color         = if (lookup.isSameStation) OrangePrimary else Color(0xFF1565C0),
                    trackColor    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text      = String.format(
                        stringResource(R.string.continue_visit_auto_confirm), secondsLeft
                    ),
                    fontSize  = 11.sp,
                    color     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                // ── Action buttons ───────────────────────────────────────────
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick   = onDismiss,
                        modifier  = Modifier.weight(1f).height(48.dp),
                        shape     = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text       = stringResource(R.string.cancel),
                            fontSize   = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Button(
                        onClick  = onConfirm,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.buttonColors(
                            containerColor = if (lookup.isSameStation) OrangePrimary
                            else Color(0xFF1565C0)
                        )
                    ) {
                        Text(
                            text = if (lookup.isSameStation)
                                stringResource(R.string.continue_visit_confirm_btn)
                            else
                                stringResource(R.string.continue_visit_confirm_btn_cross),
                            fontSize   = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign  = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StationChip(label: String, name: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text      = label,
            fontSize  = 10.sp,
            color     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
        Text(
            text       = name,
            fontSize   = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color      = SlatePrimary
        )
    }
}

// ── Success screen ───────────────────────────────────────────────────────────

@Composable
private fun SuccessContent(
    isReentry: Boolean,
    personName: String,
    reentryCount: Int?,
    onScanAgain: () -> Unit,
    onGoHome: () -> Unit
) {
    // Auto-navigate home after 4 seconds
    LaunchedEffect(Unit) {
        delay(4_000)
        onGoHome()
    }

    Box(
        modifier         = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
    Column(
        modifier            = Modifier
            .widthIn(max = 520.dp)
            .fillMaxWidth()
            .padding(horizontal = 40.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector      = Icons.Default.CheckCircle,
            contentDescription = null,
            tint             = if (isReentry) OrangePrimary else Color(0xFF1565C0),
            modifier         = Modifier.size(80.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text       = if (isReentry)
                stringResource(R.string.continue_visit_reentry_ok)
            else
                stringResource(R.string.continue_visit_continuation_ok),
            style      = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
            fontWeight = FontWeight.Bold,
            color      = SlatePrimary,
            textAlign  = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text      = personName,
            style     = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
            color     = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )

        if (isReentry && reentryCount != null && reentryCount > 1) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text     = String.format(
                    stringResource(R.string.continue_visit_reentry_count), reentryCount
                ),
                fontSize = 12.sp,
                color    = OrangePrimary
            )
        }

        Spacer(modifier = Modifier.height(40.dp))

        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick  = onScanAgain,
                modifier = Modifier.weight(1f).height(48.dp),
                shape    = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text       = stringResource(R.string.continue_visit_scan_again),
                    fontSize   = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Button(
                onClick  = onGoHome,
                modifier = Modifier.weight(1f).height(48.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = OrangePrimary)
            ) {
                Text(
                    text       = stringResource(R.string.continue_visit_go_home),
                    fontSize   = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    } // close Column
    } // close centering Box
}

// ── Error screen ─────────────────────────────────────────────────────────────

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    onGoHome: () -> Unit
) {
    Box(
        modifier         = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
    Column(
        modifier            = Modifier
            .widthIn(max = 520.dp)
            .fillMaxWidth()
            .padding(horizontal = 40.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector      = Icons.Default.ErrorOutline,
            contentDescription = null,
            tint             = MaterialTheme.colorScheme.error,
            modifier         = Modifier.size(72.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text      = message,
            style     = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
            textAlign = TextAlign.Center,
            color     = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(40.dp))

        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick  = onGoHome,
                modifier = Modifier.weight(1f).height(48.dp),
                shape    = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text       = stringResource(R.string.continue_visit_go_home),
                    fontSize   = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Button(
                onClick  = onRetry,
                modifier = Modifier.weight(1f).height(48.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = OrangePrimary)
            ) {
                Text(
                    text       = stringResource(R.string.continue_visit_scan_again),
                    fontSize   = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    } // close Column
    } // close centering Box
}


