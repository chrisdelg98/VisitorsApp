package com.eflglobal.visitorsapp.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eflglobal.visitorsapp.R
import com.eflglobal.visitorsapp.ui.localization.AppLanguage
import com.eflglobal.visitorsapp.ui.theme.OrangePrimary
import com.eflglobal.visitorsapp.ui.theme.SlatePrimary
import com.eflglobal.visitorsapp.ui.viewmodel.LanguageViewModel
import com.eflglobal.visitorsapp.ui.viewmodel.StationSetupUiState
import com.eflglobal.visitorsapp.ui.viewmodel.StationSetupViewModel
import com.eflglobal.visitorsapp.ui.viewmodel.ViewModelFactory

@Composable
fun StationSetupScreen(
    onActivate: () -> Unit,
    onBack: () -> Unit,
    selectedLanguage: String = "en",
    languageViewModel: LanguageViewModel? = null,
    viewModel: StationSetupViewModel = viewModel(
        factory = ViewModelFactory(LocalContext.current)
    )
) {
    var pin by remember { mutableStateOf("") }
    val uiState by viewModel.uiState.collectAsState()
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp.dp >= 600.dp

    LaunchedEffect(uiState) {
        when (uiState) {
            is StationSetupUiState.Success -> onActivate()
            is StationSetupUiState.StationExists -> onActivate()
            else -> {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (isTablet) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                StationWelcomePanel(
                    selectedLanguage = selectedLanguage,
                    languageViewModel = languageViewModel,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                StationActivationPanel(
                    pin = pin,
                    onPinChange = {
                        if (it.length <= 8 && it.all { c -> c.isDigit() }) {
                            pin = it
                            if (uiState is StationSetupUiState.Error) viewModel.clearError()
                        }
                    },
                    uiState = uiState,
                    onActivate = { viewModel.validatePin(pin) },
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                StationWelcomePanel(
                    selectedLanguage = selectedLanguage,
                    languageViewModel = languageViewModel,
                    modifier = Modifier.fillMaxWidth()
                )
                StationActivationPanel(
                    pin = pin,
                    onPinChange = {
                        if (it.length <= 8 && it.all { c -> c.isDigit() }) {
                            pin = it
                            if (uiState is StationSetupUiState.Error) viewModel.clearError()
                        }
                    },
                    uiState = uiState,
                    onActivate = { viewModel.validatePin(pin) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Left Panel — Branding + Language Selector
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun StationWelcomePanel(
    selectedLanguage: String,
    languageViewModel: LanguageViewModel?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(color = SlatePrimary.copy(alpha = 0.05f), shape = RoundedCornerShape(24.dp))
            .padding(40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.logo),
            contentDescription = "EFL Global Logo",
            modifier = Modifier
                .size(140.dp)
                .padding(bottom = 24.dp)
        )

        Text(
            text = stringResource(R.string.welcome),
            style = MaterialTheme.typography.headlineLarge.copy(fontSize = 32.sp),
            fontWeight = FontWeight.Bold,
            color = SlatePrimary
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.visitor_registration_system),
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        

        if (languageViewModel != null) {
            Spacer(modifier = Modifier.height(48.dp))
            StationLangSelector(
                selectedLanguage = selectedLanguage,
                onLanguageSelected = { languageViewModel.setLanguage(it) }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Right Panel — Activation Form
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun StationActivationPanel(
    pin: String,
    onPinChange: (String) -> Unit,
    uiState: StationSetupUiState,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Main activation card ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(36.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Icon badge
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(
                            color = OrangePrimary.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(18.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = OrangePrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = stringResource(R.string.configure_station),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = SlatePrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.setup_instructions),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(32.dp))

                // PIN field
                OutlinedTextField(
                    value = pin,
                    onValueChange = onPinChange,
                    label = { Text(stringResource(R.string.enter_pin)) },
                    placeholder = { Text("••••••••") },
                    isError = uiState is StationSetupUiState.Error,
                    supportingText = {
                        if (uiState is StationSetupUiState.Error) {
                            Text(
                                (uiState as StationSetupUiState.Error).message,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            Text(
                                "${pin.length}/8",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = OrangePrimary,
                        focusedLabelColor = OrangePrimary,
                        errorBorderColor = MaterialTheme.colorScheme.error
                    )
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Activate button
                Button(
                    onClick = onActivate,
                    enabled = pin.length == 8 && uiState !is StationSetupUiState.Loading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = OrangePrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(14.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                ) {
                    if (uiState is StationSetupUiState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.activate_station),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Improved info card ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = OrangePrimary.copy(alpha = 0.06f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Left accent bar
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(80.dp)
                        .background(OrangePrimary, RoundedCornerShape(2.dp))
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = OrangePrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.station_note_title),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = SlatePrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.station_note_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Language Selector (mirror of HomeScreen's LanguageSelector)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun StationLangSelector(
    selectedLanguage: String,
    onLanguageSelected: (String) -> Unit
) {
    val languages = listOf(
        AppLanguage.SPANISH    to AppLanguage.SPANISH.nativeName,
        AppLanguage.ENGLISH    to AppLanguage.ENGLISH.nativeName,
        AppLanguage.FRENCH     to AppLanguage.FRENCH.nativeName,
        AppLanguage.PORTUGUESE to AppLanguage.PORTUGUESE.nativeName,
    )

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = SlatePrimary.copy(alpha = 0.08f),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StationLangChip(
                text = languages[0].second,
                isSelected = selectedLanguage == languages[0].first.tag,
                onClick = { onLanguageSelected(languages[0].first.tag) }
            )
            StationLangChip(
                text = languages[1].second,
                isSelected = selectedLanguage == languages[1].first.tag,
                onClick = { onLanguageSelected(languages[1].first.tag) }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StationLangChip(
                text = languages[2].second,
                isSelected = selectedLanguage == languages[2].first.tag,
                onClick = { onLanguageSelected(languages[2].first.tag) }
            )
            StationLangChip(
                text = languages[3].second,
                isSelected = selectedLanguage == languages[3].first.tag,
                onClick = { onLanguageSelected(languages[3].first.tag) }
            )
        }
    }
}

@Composable
private fun StationLangChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = when {
            isPressed  -> 0.94f
            isSelected -> 1.02f
            else       -> 1.00f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness    = Spring.StiffnessLow
        ),
        label = "stationLangChipScale"
    )

    val contentColor by animateColorAsState(
        targetValue = if (isSelected) Color.White else SlatePrimary,
        animationSpec = tween(durationMillis = 380),
        label = "stationLangChipContent"
    )

    val chipBrush = if (isSelected)
        Brush.horizontalGradient(listOf(OrangePrimary, OrangePrimary.copy(red = 0.95f)))
    else
        Brush.horizontalGradient(listOf(SlatePrimary.copy(alpha = 0.06f), SlatePrimary.copy(alpha = 0.06f)))

    Box(
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .background(brush = chipBrush)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .widthIn(min = 100.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = contentColor,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}
