package com.eflglobal.visitorsapp.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import com.eflglobal.visitorsapp.ui.localization.Strings
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Layout adaptativo: Column para móvil, Row para tablet
            if (isTablet) {
                // Layout de dos columnas para tablet
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(32.dp)
                ) {
                    WelcomeSection(
                        selectedLanguage = selectedLanguage,
                        languageViewModel = languageViewModel,
                        modifier = Modifier.weight(1f)
                    )

                    OptionsSection(
                        selectedLanguage = selectedLanguage,
                        onNewVisit = onNewVisit,
                        onRecurrentVisit = onRecurrentVisit,
                        onCheckout = onCheckout,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                // Layout de una columna para móvil
                WelcomeSection(
                    selectedLanguage = selectedLanguage,
                    languageViewModel = languageViewModel,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                OptionsSection(
                    selectedLanguage = selectedLanguage,
                    onNewVisit = onNewVisit,
                    onRecurrentVisit = onRecurrentVisit,
                    onCheckout = onCheckout,
                    modifier = Modifier.fillMaxWidth()
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
            .background(
                color = SlatePrimary.copy(alpha = 0.03f),
                shape = RoundedCornerShape(24.dp)
            )
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Logo de la empresa
        Image(
            painter = painterResource(id = R.drawable.logo),
            contentDescription = "EFL Global Logo",
            modifier = Modifier
                .size(120.dp)
                .padding(bottom = 24.dp)
        )

        Text(
            text = Strings.welcome(selectedLanguage),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = SlatePrimary
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = if (selectedLanguage == "es")
                "Sistema de Registro de Visitantes"
            else
                "Visitor Registration System",
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Selector de idioma
        LanguageSelector(
            selectedLanguage = selectedLanguage,
            onLanguageSelected = { languageViewModel.setLanguage(it) }
        )
    }
}

@Composable
private fun OptionsSection(
    selectedLanguage: String,
    onNewVisit: () -> Unit,
    onRecurrentVisit: () -> Unit,
    onCheckout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HomeOptionCard(
            icon = Icons.Default.Add,
            title = Strings.newVisit(selectedLanguage),
            description = Strings.newVisitDesc(selectedLanguage),
            onClick = onNewVisit
        )

        HomeOptionCard(
            icon = Icons.Default.Person,
            title = Strings.recurringVisit(selectedLanguage),
            description = Strings.recurringVisitDesc(selectedLanguage),
            onClick = onRecurrentVisit
        )

        HomeOptionCard(
            icon = Icons.AutoMirrored.Filled.ExitToApp,
            title = Strings.endVisit(selectedLanguage),
            description = Strings.endVisitDesc(selectedLanguage),
            onClick = onCheckout
        )
    }
}

@Composable
private fun LanguageSelector(
    selectedLanguage: String,
    onLanguageSelected: (String) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LanguageButton(
            text = "Español",
            isSelected = selectedLanguage == "es",
            onClick = { onLanguageSelected("es") }
        )
        LanguageButton(
            text = "English",
            isSelected = selectedLanguage == "en",
            onClick = { onLanguageSelected("en") }
        )
    }
}

@Composable
private fun LanguageButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) OrangePrimary else MaterialTheme.colorScheme.surface,
            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.height(48.dp),
        border = if (!isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
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
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp,
            pressedElevation = 8.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .background(
                        color = OrangePrimary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(16.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = OrangePrimary,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 16.sp),
                    fontWeight = FontWeight.Bold,
                    color = SlatePrimary
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 2
                )
            }
        }
    }
}


