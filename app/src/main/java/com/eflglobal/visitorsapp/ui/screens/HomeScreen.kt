package com.eflglobal.visitorsapp.ui.screens

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import com.eflglobal.visitorsapp.R
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
                    selectedLanguage = selectedLanguage,
                    languageViewModel = languageViewModel,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )

                // Panel derecho - Options Section
                OptionsSection(
                    selectedLanguage = selectedLanguage,
                    onNewVisit = onNewVisit,
                    onRecurrentVisit = onRecurrentVisit,
                    onCheckout = onCheckout,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
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
                    selectedLanguage = selectedLanguage,
                    languageViewModel = languageViewModel,
                    modifier = Modifier.fillMaxWidth()
                )

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
                color = SlatePrimary.copy(alpha = 0.05f),
                shape = RoundedCornerShape(24.dp)
            )
            .padding(40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Logo de la empresa
        Image(
            painter = painterResource(id = R.drawable.logo),
            contentDescription = "EFL Global Logo",
            modifier = Modifier
                .size(140.dp)
                .padding(bottom = 32.dp)
        )

        Text(
            text = Strings.welcome(selectedLanguage),
            style = MaterialTheme.typography.headlineLarge.copy(fontSize = 32.sp),
            fontWeight = FontWeight.Bold,
            color = SlatePrimary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (selectedLanguage == "es")
                "Sistema de Registro de Visitantes"
            else
                "Visitor Registration System",
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(48.dp))

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
        modifier = modifier.padding(vertical = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HomeOptionCard(
            icon = Icons.Default.Add,
            title = Strings.newVisit(selectedLanguage),
            description = Strings.newVisitDesc(selectedLanguage),
            onClick = onNewVisit
        )

        Spacer(modifier = Modifier.height(20.dp))

        HomeOptionCard(
            icon = Icons.Default.Person,
            title = Strings.returningVisit(selectedLanguage),
            description = Strings.returningVisitDesc(selectedLanguage),
            onClick = onRecurrentVisit
        )

        Spacer(modifier = Modifier.height(20.dp))

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
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(24.dp)
            )
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
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
            containerColor = if (isSelected) OrangePrimary else Color.Transparent,
            contentColor = if (isSelected) Color.White else SlatePrimary.copy(alpha = 0.7f)
        ),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .width(100.dp)
            .height(44.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
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


