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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import com.eflglobal.visitorsapp.R
import com.eflglobal.visitorsapp.ui.localization.AppLanguage
import com.eflglobal.visitorsapp.ui.theme.OrangePrimary
import com.eflglobal.visitorsapp.ui.theme.SlatePrimary
import com.eflglobal.visitorsapp.ui.viewmodel.LanguageViewModel

@Composable
fun HomeScreen(
    onNewVisit: () -> Unit,
    onRecurrentVisit: () -> Unit,
    onCheckout: () -> Unit,
    onStationSetup: () -> Unit,
    languageViewModel: LanguageViewModel,
    selectedLanguage: String
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val isTablet = screenWidth >= 600.dp // Detectar si es tablet

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Layout adaptativo: Column para móvil, Row para tablet
        if (isTablet) {
            // Layout de dos columnas para tablet
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Panel izquierdo - Welcome Section
                WelcomeSection(
                    selectedLanguage  = selectedLanguage,
                    languageViewModel = languageViewModel,
                    modifier          = Modifier.weight(1f).fillMaxHeight()
                )

                // Panel derecho - Options Section
                OptionsSection(
                    onNewVisit        = onNewVisit,
                    onRecurrentVisit  = onRecurrentVisit,
                    onCheckout        = onCheckout,
                    modifier          = Modifier.weight(1f).fillMaxHeight()
                )
            }
        } else {
            // Layout de una columna para móvil
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                WelcomeSection(
                    selectedLanguage  = selectedLanguage,
                    languageViewModel = languageViewModel,
                    modifier          = Modifier.fillMaxWidth()
                )

                OptionsSection(
                    onNewVisit       = onNewVisit,
                    onRecurrentVisit = onRecurrentVisit,
                    onCheckout       = onCheckout,
                    modifier         = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun WelcomeSection(
    selectedLanguage: String,
    languageViewModel: LanguageViewModel,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(color = SlatePrimary.copy(alpha = 0.05f), shape = RoundedCornerShape(24.dp))
            .padding(40.dp),
        verticalArrangement   = Arrangement.Center,
        horizontalAlignment   = Alignment.CenterHorizontally
    ) {
        // Logo de la empresa
        Image(
            painter           = painterResource(id = R.drawable.logo),
            contentDescription = "EFL Global Logo",
            modifier          = Modifier.size(140.dp).padding(bottom = 32.dp)
        )

        Text(
            text       = stringResource(R.string.welcome),
            style      = MaterialTheme.typography.headlineLarge.copy(fontSize = 32.sp),
            fontWeight = FontWeight.Bold,
            color      = SlatePrimary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text      = stringResource(R.string.visitor_registration_system),
            style     = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
            color     = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Selector de idioma
        LanguageSelector(
            selectedLanguage   = selectedLanguage,
            onLanguageSelected = { languageViewModel.setLanguage(it) }
        )
    }
}

@Composable
private fun OptionsSection(
    onNewVisit: () -> Unit,
    onRecurrentVisit: () -> Unit,
    onCheckout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier            = modifier.padding(vertical = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HomeOptionCard(
            icon        = Icons.Default.Add,
            title       = stringResource(R.string.new_visit),
            description = stringResource(R.string.new_visit_desc),
            onClick     = onNewVisit
        )

        Spacer(modifier = Modifier.height(20.dp))

        HomeOptionCard(
            icon        = Icons.Default.Person,
            title       = stringResource(R.string.returning_visit),
            description = stringResource(R.string.returning_visit_desc),
            onClick     = onRecurrentVisit
        )

        Spacer(modifier = Modifier.height(20.dp))

        HomeOptionCard(
            icon        = Icons.AutoMirrored.Filled.ExitToApp,
            title       = stringResource(R.string.end_visit),
            description = stringResource(R.string.end_visit_desc),
            onClick     = onCheckout
        )
    }
}

@Composable
private fun LanguageSelector(
    selectedLanguage: String,
    onLanguageSelected: (String) -> Unit
) {
    val languages = listOf(
        AppLanguage.SPANISH    to stringResource(R.string.lang_spanish),
        AppLanguage.ENGLISH    to stringResource(R.string.lang_english),
        AppLanguage.FRENCH     to stringResource(R.string.lang_french),
        AppLanguage.PORTUGUESE to stringResource(R.string.lang_portuguese),
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
        // Row 1: ES  |  EN
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LangChip(
                text       = languages[0].second,
                isSelected = selectedLanguage == languages[0].first.tag,
                onClick    = { onLanguageSelected(languages[0].first.tag) }
            )
            LangChip(
                text       = languages[1].second,
                isSelected = selectedLanguage == languages[1].first.tag,
                onClick    = { onLanguageSelected(languages[1].first.tag) }
            )
        }
        // Row 2: PT  |  FR
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LangChip(
                text       = languages[2].second,
                isSelected = selectedLanguage == languages[2].first.tag,
                onClick    = { onLanguageSelected(languages[2].first.tag) }
            )
            LangChip(
                text       = languages[3].second,
                isSelected = selectedLanguage == languages[3].first.tag,
                onClick    = { onLanguageSelected(languages[3].first.tag) }
            )
        }
    }
}

@Composable
private fun LangChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Liquid spring scale — squish on press, bounce on release
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
        label = "langChipScale"
    )

    // Background color — smooth tween
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) OrangePrimary else SlatePrimary.copy(alpha = 0.06f),
        animationSpec = tween(durationMillis = 380),
        label = "langChipBg"
    )

    // Text + icon color — full SlatePrimary when unselected for clear contrast
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) Color.White else SlatePrimary,
        animationSpec = tween(durationMillis = 380),
        label = "langChipContent"
    )

    // Subtle gradient overlay when selected
    val chipBrush = if (isSelected) Brush.horizontalGradient(
        listOf(OrangePrimary, OrangePrimary.copy(red = 0.95f))
    ) else Brush.horizontalGradient(
        listOf(SlatePrimary.copy(alpha = 0.06f), SlatePrimary.copy(alpha = 0.06f))
    )

    Box(
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .background(brush = chipBrush)
            .clickable(
                interactionSource = interactionSource,
                indication        = null,
                onClick           = onClick
            )
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .widthIn(min = 100.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text       = text,
            fontSize   = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color      = contentColor,
            textAlign  = TextAlign.Center,
            maxLines   = 1
        )
    }
}

@Composable
private fun HomeOptionCard(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        onClick   = onClick,
        modifier  = Modifier.fillMaxWidth().height(120.dp),
        shape     = RoundedCornerShape(20.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp, pressedElevation = 8.dp)
    ) {
        Row(
            modifier          = Modifier.fillMaxSize().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .background(color = OrangePrimary.copy(alpha = 0.15f), shape = RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = OrangePrimary, modifier = Modifier.size(32.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = title,
                    style      = MaterialTheme.typography.titleLarge.copy(fontSize = 16.sp),
                    fontWeight = FontWeight.Bold,
                    color      = SlatePrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text     = description,
                    style    = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                    color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 2
                )
            }
        }
    }
}

