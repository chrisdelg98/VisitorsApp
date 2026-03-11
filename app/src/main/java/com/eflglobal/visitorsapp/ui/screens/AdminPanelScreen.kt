package com.eflglobal.visitorsapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eflglobal.visitorsapp.R
import com.eflglobal.visitorsapp.domain.model.Visit
import com.eflglobal.visitorsapp.ui.theme.OrangePrimary
import com.eflglobal.visitorsapp.ui.theme.SlatePrimary
import com.eflglobal.visitorsapp.ui.viewmodel.AdminPanelUiState
import com.eflglobal.visitorsapp.ui.viewmodel.AdminPanelViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPanelScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    viewModel: AdminPanelViewModel,
    selectedLanguage: String = "es"
) {
    val uiState by viewModel.uiState.collectAsState()
    var showLogoutDialog by remember { mutableStateOf(false) }

    // Diálogo de confirmación de logout
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.logout),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(text = stringResource(R.string.logout_confirmation))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        viewModel.logout {
                            onLogout()
                        }
                    }
                ) {
                    Text(
                        text = stringResource(R.string.yes),
                        color = OrangePrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(text = stringResource(R.string.no))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.admin_panel),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh"
                        )
                    }
                    IconButton(onClick = { showLogoutDialog = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = stringResource(R.string.logout)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SlatePrimary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (val state = uiState) {
                is AdminPanelUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = OrangePrimary)
                    }
                }

                is AdminPanelUiState.Success -> {
                    AdminPanelContent(
                        state = state,
                        onRefresh = { viewModel.refresh() },
                        selectedLanguage = selectedLanguage
                    )
                }

                is AdminPanelUiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(64.dp)
                            )
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                            Button(
                                onClick = { viewModel.refresh() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = OrangePrimary
                                )
                            ) {
                                Text("Reintentar")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminPanelContent(
    state: AdminPanelUiState.Success,
    onRefresh: () -> Unit,
    selectedLanguage: String
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Header: Información de la estación
        item {
            StationInfoCard(
                stationName = state.station.stationName,
                countryCode = state.station.countryCode,
                stationId = state.station.stationId
            )
        }

        // Estadísticas en cards
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatCard(
                    title = stringResource(R.string.total_visits),
                    value = state.totalVisits.toString(),
                    icon = Icons.Default.People,
                    color = SlatePrimary,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = stringResource(R.string.active_visits),
                    value = state.activeVisits.toString(),
                    icon = Icons.Default.Person,
                    color = OrangePrimary,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatCard(
                    title = stringResource(R.string.today_visits),
                    value = state.todayVisits.toString(),
                    icon = Icons.Default.Today,
                    color = Color(0xFF4CAF50),
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = stringResource(R.string.month_visits),
                    value = state.thisMonthVisits.toString(),
                    icon = Icons.Default.CalendarMonth,
                    color = Color(0xFF2196F3),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Título de visitas recientes
        item {
            Text(
                text = stringResource(R.string.recent_visits),
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                fontWeight = FontWeight.Bold,
                color = SlatePrimary,
                modifier = Modifier.padding(top = 16.dp)
            )
        }

        // Lista de visitas recientes
        if (state.recentVisits.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.no_visits_yet),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }
            }
        } else {
            items(state.recentVisits) { visit ->
                VisitItemCard(visit = visit)
            }
        }
    }
}

@Composable
private fun StationInfoCard(
    stationName: String,
    countryCode: String,
    stationId: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = SlatePrimary.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.current_station),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
                Text(
                    text = stationName,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                    fontWeight = FontWeight.Bold,
                    color = SlatePrimary
                )
                Text(
                    text = "$countryCode • ${stationId.take(8)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }
            Icon(
                imageVector = Icons.Default.Store,
                contentDescription = null,
                tint = SlatePrimary.copy(alpha = 0.3f),
                modifier = Modifier.size(48.dp)
            )
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(140.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(32.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineLarge.copy(fontSize = 32.sp),
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun VisitItemCard(visit: Visit) {
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    val entryDateStr = dateFormat.format(Date(visit.entryDate))
    val exitDateStr = visit.exitDate?.let { dateFormat.format(Date(it)) } ?: "Activa"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = visit.visitingPersonName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = SlatePrimary
                )
                Text(
                    text = "Entrada: $entryDateStr",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(
                    text = "Salida: $exitDateStr",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (visit.exitDate == null) OrangePrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            // Badge de estado
            Surface(
                color = if (visit.exitDate == null) OrangePrimary.copy(alpha = 0.1f) else Color.Gray.copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = if (visit.exitDate == null) "ACTIVA" else "FINALIZADA",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (visit.exitDate == null) OrangePrimary else Color.Gray,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

