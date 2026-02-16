package com.eflglobal.visitorsapp.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Layout de dos columnas
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                // Columna izquierda: Bienvenida e idioma
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
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
                            .size(140.dp)
                            .padding(bottom = 48.dp)
                    )

                    Text(
                        text = Strings.welcome(selectedLanguage),
                        style = MaterialTheme.typography.displayMedium,
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
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(48.dp))

                    // Selector de idioma
                    LanguageSelector(
                        selectedLanguage = selectedLanguage,
                        onLanguageSelected = { languageViewModel.setLanguage(it) }
                    )
                }

                // Columna derecha: Opciones
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
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

            // Footer
            Text(
                text = "EFL Global © 2026",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 16.dp)
            )
        }
    }
}

@Composable
private fun LanguageSelector(
    selectedLanguage: String,
    onLanguageSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .height(56.dp)
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(28.dp)
            )
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        LanguageButton(
            text = Strings.spanish(selectedLanguage),
            isSelected = selectedLanguage == "es",
            onClick = { onLanguageSelected("es") }
        )

        LanguageButton(
            text = Strings.english(selectedLanguage),
            isSelected = selectedLanguage == "en",
            onClick = { onLanguageSelected("en") }
        )
    }
}

// ...existing code...

@Composable
private fun LanguageButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) SlatePrimary else MaterialTheme.colorScheme.surface,
            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        ),
        shape = RoundedCornerShape(24.dp),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = if (isSelected) 4.dp else 0.dp
        ),
        modifier = Modifier
            .height(48.dp)
            .widthIn(min = 120.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
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
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp,
            pressedElevation = 8.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(
                        color = OrangePrimary.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(12.dp)
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

            Spacer(modifier = Modifier.width(20.dp))

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = SlatePrimary
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}



