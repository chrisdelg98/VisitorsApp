package com.eflglobal.visitorsapp.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import com.eflglobal.visitorsapp.ui.localization.LanguageManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eflglobal.visitorsapp.R
import com.eflglobal.visitorsapp.core.printing.PrinterConfig
import com.eflglobal.visitorsapp.core.printing.PrinterConfigRepository
import com.eflglobal.visitorsapp.core.printing.PrinterManager
import com.eflglobal.visitorsapp.domain.model.VisitWithPersonInfo
import com.eflglobal.visitorsapp.ui.components.VisitorBadgeButton
import com.eflglobal.visitorsapp.ui.theme.OrangePrimary
import com.eflglobal.visitorsapp.ui.theme.SlatePrimary
import com.eflglobal.visitorsapp.ui.viewmodel.AdminPanelUiState
import com.eflglobal.visitorsapp.ui.viewmodel.AdminPanelViewModel
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch

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
    var selectedVisit by remember { mutableStateOf<VisitWithPersonInfo?>(null) }
    var showPrinterSettings by remember { mutableStateOf(false) }

    // Localized context para que los diálogos (que abren nueva ventana Compose)
    // respeten el idioma seleccionado y no el locale del dispositivo.
    val rawContext = LocalContext.current
    val localizedContext = remember(selectedLanguage) {
        LanguageManager.wrapContext(rawContext, selectedLanguage)
    }

    // Estados de filtros
    var filterStatus by remember { mutableStateOf<Set<VisitFilterStatus>>(emptySet()) }
    var filterVisitorType by remember { mutableStateOf<Set<String>>(emptySet()) }
    var filterFirstName by remember { mutableStateOf("") }
    var filterLastName by remember { mutableStateOf("") }
    var filterVisitingPerson by remember { mutableStateOf("") }
    var filterStartDate by remember { mutableStateOf<Long?>(null) }
    var filterEndDate by remember { mutableStateOf<Long?>(null) }

    // Modal de detalles de visita
    selectedVisit?.let { visitWithInfo ->
        VisitDetailsModal(
            visitWithInfo = visitWithInfo,
            onDismiss = { selectedVisit = null },
            selectedLanguage = selectedLanguage
        )
    }

    // Printer settings dialog
    if (showPrinterSettings) {
        PrinterSettingsDialog(onDismiss = { showPrinterSettings = false })
    }

    // Pre-resolver strings del diálogo de logout usando el contexto localizado.
    // AlertDialog crea un AndroidView propio que NO hereda LocalContext de Compose,
    // por lo que hay que resolver los textos aquí, fuera del diálogo.
    val logoutTitle        = localizedContext.getString(R.string.logout)
    val logoutMessage      = localizedContext.getString(R.string.logout_confirmation)
    val logoutYes          = localizedContext.getString(R.string.yes)
    val logoutNo           = localizedContext.getString(R.string.no)

    // Diálogo de confirmación de logout
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = {
                Text(text = logoutTitle, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(text = logoutMessage)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        viewModel.logout { onLogout() }
                    }
                ) {
                    Text(text = logoutYes, color = OrangePrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(text = logoutNo)
                }
            }
        )
    }

    // Estado del panel lateral (para abrirlo a la DERECHA)
    var showFiltersPanel by remember { mutableStateOf(false) }

    // Estados de paginación
    var currentPage by remember { mutableStateOf(1) }
    val itemsPerPage = 20

    Box(modifier = Modifier.fillMaxSize()) {
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
                actions = {},
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
                        onOpenPrinterSettings = { showPrinterSettings = true },
                        onOpenFilters = { showFiltersPanel = true },
                        onLogout = { showLogoutDialog = true },
                        selectedLanguage = selectedLanguage,
                        filterStatus = filterStatus,
                        filterVisitorType = filterVisitorType,
                        onFilterStatusChange = { filterStatus = it },
                        onFilterVisitorTypeChange = { filterVisitorType = it },
                        onVisitClick = { selectedVisit = it },
                        currentPage = currentPage,
                        itemsPerPage = itemsPerPage,
                        onPageChange = { currentPage = it },
                        filterFirstName = filterFirstName,
                        filterLastName = filterLastName,
                        filterVisitingPerson = filterVisitingPerson
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
    } // Fin del Scaffold

    // Panel lateral de filtros a la DERECHA con animación — sin overlay oscuro
    AnimatedVisibility(
        visible = showFiltersPanel,
        enter = slideInHorizontally(
            initialOffsetX = { it },
            animationSpec = tween(300)
        ),
        exit = slideOutHorizontally(
            targetOffsetX = { it },
            animationSpec = tween(300)
        )
    ) {
        // Solo el panel, sin scrim/overlay encima del contenido
        Box(modifier = Modifier.fillMaxSize()) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.4f)
                    .align(Alignment.CenterEnd),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 12.dp,
                tonalElevation = 4.dp
            ) {
                FilterDrawerContent(
                    filterStatus = filterStatus,
                    filterVisitorType = filterVisitorType,
                    filterFirstName = filterFirstName,
                    filterLastName = filterLastName,
                    filterVisitingPerson = filterVisitingPerson,
                    filterStartDate = filterStartDate,
                    filterEndDate = filterEndDate,
                    onFilterStatusChange = { filterStatus = it },
                    onFilterVisitorTypeChange = { filterVisitorType = it },
                    onFilterFirstNameChange = { filterFirstName = it },
                    onFilterLastNameChange = { filterLastName = it },
                    onFilterVisitingPersonChange = { filterVisitingPerson = it },
                    onFilterStartDateChange = { filterStartDate = it },
                    onFilterEndDateChange = { filterEndDate = it },
                    onClearFilters = {
                        filterStatus = emptySet()
                        filterVisitorType = emptySet()
                        filterFirstName = ""
                        filterLastName = ""
                        filterVisitingPerson = ""
                        filterStartDate = null
                        filterEndDate = null
                        currentPage = 1
                    },
                    onClose = { showFiltersPanel = false },
                    selectedLanguage = selectedLanguage
                )
            }
        }
    }
    } // Fin del Box
}

@Composable
private fun AdminPanelContent(
    state: AdminPanelUiState.Success,
    onRefresh: () -> Unit,
    onOpenPrinterSettings: () -> Unit,
    onOpenFilters: () -> Unit,
    onLogout: () -> Unit,
    selectedLanguage: String,
    filterStatus: Set<VisitFilterStatus>,
    filterVisitorType: Set<String>,
    onFilterStatusChange: (Set<VisitFilterStatus>) -> Unit,
    onFilterVisitorTypeChange: (Set<String>) -> Unit,
    onVisitClick: (VisitWithPersonInfo) -> Unit,
    currentPage: Int,
    itemsPerPage: Int,
    onPageChange: (Int) -> Unit,
    filterFirstName: String,
    filterLastName: String,
    filterVisitingPerson: String
) {
    // Aplicar filtros a las visitas
    val filteredVisits = remember(
        state.recentVisits,
        filterStatus,
        filterVisitorType,
        filterFirstName,
        filterLastName,
        filterVisitingPerson
    ) {
        state.recentVisits.filter { visitWithInfo ->
            val visit = visitWithInfo.visit

            // Filtro por estado — empty set means "All"
            val statusMatch = when {
                filterStatus.isEmpty() -> true
                filterStatus.contains(VisitFilterStatus.ACTIVE) && visit.exitDate == null -> true
                filterStatus.contains(VisitFilterStatus.COMPLETED) && visit.exitDate != null -> true
                else -> false
            }

            // Filtro por tipo de visitante — empty set means "All"
            val typeMatch = filterVisitorType.isEmpty() || filterVisitorType.contains(visit.visitorType)

            // Filtro por nombre (insensible a mayúsculas/minúsculas)
            val firstNameMatch = filterFirstName.isBlank() ||
                visitWithInfo.personFirstName.contains(filterFirstName, ignoreCase = true)

            // Filtro por apellido (insensible a mayúsculas/minúsculas)
            val lastNameMatch = filterLastName.isBlank() ||
                visitWithInfo.personLastName.contains(filterLastName, ignoreCase = true)

            // Filtro por persona que visita (insensible a mayúsculas/minúsculas)
            val visitingPersonMatch = filterVisitingPerson.isBlank() ||
                visit.visitingPersonName.contains(filterVisitingPerson, ignoreCase = true)

            // Todos los filtros deben coincidir
            statusMatch && typeMatch && firstNameMatch && lastNameMatch && visitingPersonMatch
        }
    }

    // Aplicar paginación
    val totalPages = (filteredVisits.size + itemsPerPage - 1) / itemsPerPage
    val paginatedVisits = remember(filteredVisits, currentPage, itemsPerPage) {
        val startIndex = (currentPage - 1) * itemsPerPage
        val endIndex = minOf(startIndex + itemsPerPage, filteredVisits.size)
        if (startIndex < filteredVisits.size) {
            filteredVisits.subList(startIndex, endIndex)
        } else {
            emptyList()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Fila de acciones principales (arriba de todo) ──
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenPrinterSettings,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SlatePrimary),
                    border = BorderStroke(1.dp, SlatePrimary.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.printer_settings), maxLines = 1, style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(
                    onClick = onLogout,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SlatePrimary),
                    border = BorderStroke(1.dp, SlatePrimary.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.logout), maxLines = 1, style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        // ── Card de estación ──
        item {
            StationInfoCard(
                stationName = state.station.stationName,
                countryCode = state.station.countryCode,
                stationId = state.station.stationId
            )
        }

        // ── Estadísticas compactas en 4 columnas ──
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CompactStatCard(
                    title = stringResource(R.string.total_visits),
                    value = state.totalVisits.toString(),
                    icon = Icons.Default.People,
                    color = SlatePrimary,
                    modifier = Modifier.weight(1f)
                )
                CompactStatCard(
                    title = stringResource(R.string.active_visits),
                    value = state.activeVisits.toString(),
                    icon = Icons.Default.Person,
                    color = OrangePrimary,
                    modifier = Modifier.weight(1f)
                )
                CompactStatCard(
                    title = stringResource(R.string.today_visits),
                    value = state.todayVisits.toString(),
                    icon = Icons.Default.Today,
                    color = Color(0xFF4CAF50),
                    modifier = Modifier.weight(1f)
                )
                CompactStatCard(
                    title = stringResource(R.string.month_visits),
                    value = state.thisMonthVisits.toString(),
                    icon = Icons.Default.CalendarMonth,
                    color = Color(0xFF2196F3),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // ── Título "Visitas Recientes" + Filtros + Refresh ──
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.recent_visits),
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                        fontWeight = FontWeight.Bold,
                        color = SlatePrimary
                    )
                    if (filterStatus.isNotEmpty() ||
                        filterVisitorType.isNotEmpty() ||
                        filterFirstName.isNotBlank() ||
                        filterLastName.isNotBlank() ||
                        filterVisitingPerson.isNotBlank()) {
                        Surface(
                            color = OrangePrimary.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text(
                                text = "${filteredVisits.size}",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = OrangePrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onRefresh,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = SlatePrimary),
                        border = BorderStroke(1.dp, SlatePrimary.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.visits), style = MaterialTheme.typography.labelSmall)
                    }
                    Button(
                        onClick = onOpenFilters,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.filters), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // Lista de visitas recientes
        if (filteredVisits.isEmpty()) {
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
            items(paginatedVisits) { visitWithInfo ->
                VisitItemCard(
                    visitWithInfo = visitWithInfo,
                    onClick = { onVisitClick(visitWithInfo) },
                    selectedLanguage = selectedLanguage
                )
            }

            // Controles de paginación
            if (totalPages > 1) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Botón Previous
                        OutlinedButton(
                            onClick = { if (currentPage > 1) onPageChange(currentPage - 1) },
                            enabled = currentPage > 1,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = OrangePrimary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChevronLeft,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Previous")
                        }

                        // Indicador de página
                        Text(
                            text = "Página $currentPage de $totalPages",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = SlatePrimary
                        )

                        // Botón Next
                        OutlinedButton(
                            onClick = { if (currentPage < totalPages) onPageChange(currentPage + 1) },
                            enabled = currentPage < totalPages,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = OrangePrimary
                            )
                        ) {
                            Text("Next")
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
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
private fun CompactStatCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.10f)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 26.sp),
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                maxLines = 2
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
private fun VisitItemCard(
    visitWithInfo: VisitWithPersonInfo,
    onClick: () -> Unit,
    selectedLanguage: String
) {
    val visit = visitWithInfo.visit
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    val entryDateStr = dateFormat.format(Date(visit.entryDate))
    val exitDateStr = visit.exitDate?.let { dateFormat.format(Date(it)) } ?: stringResource(R.string.active)

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
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
                // Nombre completo del visitante
                Text(
                    text = visitWithInfo.fullPersonName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = SlatePrimary
                )
                Text(
                    text = "${stringResource(R.string.entry)}: $entryDateStr",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(
                    text = "${stringResource(R.string.exit)}: $exitDateStr",
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
                    text = if (visit.exitDate == null)
                        stringResource(R.string.active).uppercase()
                    else
                        stringResource(R.string.completed).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (visit.exitDate == null) OrangePrimary else Color.Gray,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}


// ============================================================
// ENUMS Y COMPOSABLES AUXILIARES
// ============================================================

enum class VisitFilterStatus {
    ALL, ACTIVE, COMPLETED
}

@Composable
private fun FiltersSection(
    filterStatus: VisitFilterStatus,
    filterVisitorType: String?,
    onFilterStatusChange: (VisitFilterStatus) -> Unit,
    onFilterVisitorTypeChange: (String?) -> Unit,
    selectedLanguage: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.filters),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = SlatePrimary
            )

            // Filtro por estado
            Text(
                text = stringResource(R.string.visit_status),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filterStatus == VisitFilterStatus.ALL,
                    onClick = { onFilterStatusChange(VisitFilterStatus.ALL) },
                    label = { Text(stringResource(R.string.all)) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = filterStatus == VisitFilterStatus.ACTIVE,
                    onClick = { onFilterStatusChange(VisitFilterStatus.ACTIVE) },
                    label = { Text(stringResource(R.string.active)) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = filterStatus == VisitFilterStatus.COMPLETED,
                    onClick = { onFilterStatusChange(VisitFilterStatus.COMPLETED) },
                    label = { Text(stringResource(R.string.completed)) },
                    modifier = Modifier.weight(1f)
                )
            }

            // Filtro por tipo de visitante
            Text(
                text = stringResource(R.string.visitor_type_filter),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )

            val visitorTypes = listOf(
                null to stringResource(R.string.all),
                "VISITOR" to stringResource(R.string.visitor),
                "CONTRACTOR" to stringResource(R.string.contractor),
                "VENDOR" to stringResource(R.string.vendor),
                "DELIVERY" to stringResource(R.string.delivery),
                "DRIVER" to stringResource(R.string.driver),
                "TEMPORARY_STAFF" to stringResource(R.string.temporary_staff)
            )

            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(visitorTypes.size) { index ->
                    val (type, label) = visitorTypes[index]
                    FilterChip(
                        selected = filterVisitorType == type,
                        onClick = { onFilterVisitorTypeChange(type) },
                        label = { Text(label) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VisitDetailsModal(
    visitWithInfo: VisitWithPersonInfo,
    onDismiss: () -> Unit,
    selectedLanguage: String
) {
    val visit = visitWithInfo.visit
    val rawContext = LocalContext.current

    // Re-wrap with the selected locale so ALL stringResource() calls inside
    // the Dialog window resolve to the correct language.
    val localizedContext = remember(selectedLanguage) {
        LanguageManager.wrapContext(rawContext, selectedLanguage)
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false, // allow us to set our own width
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        // Provide the localized context so stringResource() inside the Dialog
        // uses the user-selected language rather than the device default.
        CompositionLocalProvider(LocalContext provides localizedContext) {
            VisitDetailsContent(
                visitWithInfo    = visitWithInfo,
                onDismiss        = onDismiss,
                selectedLanguage = selectedLanguage,
                context          = localizedContext
            )
        }
    }
}

@Composable
private fun VisitDetailsContent(
    visitWithInfo: VisitWithPersonInfo,
    onDismiss: () -> Unit,
    selectedLanguage: String,
    context: android.content.Context
) {
    val visit = visitWithInfo.visit
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()) }
    val entryDateStr = dateFormat.format(Date(visit.entryDate))
    val exitDateStr  = visit.exitDate?.let { dateFormat.format(Date(it)) }
        ?: stringResource(R.string.still_active)

    val duration = if (visit.exitDate != null) {
        val durationMillis = visit.exitDate - visit.entryDate
        val hours   = durationMillis / (1000 * 60 * 60)
        val minutes = (durationMillis % (1000 * 60 * 60)) / (1000 * 60)
        "$hours h $minutes min"
    } else {
        stringResource(R.string.in_progress)
    }

    // 85 % of the screen – Dialog with usePlatformDefaultWidth=false
    // fills the entire screen, so fillMaxWidth/Height are relative to the real display.
    Card(
        modifier = Modifier
            .fillMaxWidth(0.85f)
            .fillMaxHeight(0.88f),
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Header ──────────────────────────────────────────────────────
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    text       = stringResource(R.string.visit_details),
                    style      = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color      = SlatePrimary
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                }
            }

            HorizontalDivider()

            // ── Two-column body ─────────────────────────────────────────────
            Row(
                modifier              = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {

                // LEFT COLUMN – visit info (60 %)
                LazyColumn(
                    modifier              = Modifier
                        .weight(0.6f)
                        .fillMaxHeight(),
                    verticalArrangement   = Arrangement.spacedBy(14.dp)
                ) {
                    // Status
                    item {
                        DetailRow(
                            label      = stringResource(R.string.status),
                            value      = if (visit.exitDate == null)
                                             stringResource(R.string.active)
                                         else
                                             stringResource(R.string.completed),
                            valueColor = if (visit.exitDate == null) OrangePrimary else Color.Gray
                        )
                    }

                    item { HorizontalDivider() }

                    // Visitor information section
                    item {
                        Text(
                            text       = stringResource(R.string.visitor_information),
                            style      = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color      = SlatePrimary
                        )
                    }
                    item { DetailRow(stringResource(R.string.first_name),      visitWithInfo.personFirstName) }
                    item { DetailRow(stringResource(R.string.last_name),       visitWithInfo.personLastName) }
                    if (!visitWithInfo.personCompany.isNullOrBlank()) {
                        item { DetailRow(stringResource(R.string.company),     visitWithInfo.personCompany) }
                    }
                    item { DetailRow(stringResource(R.string.visiting_person), visit.visitingPersonName) }
                    item {
                        DetailRow(
                            label = stringResource(R.string.visitor_type),
                            value = getVisitorTypeLabel(visit.visitorType, selectedLanguage)
                        )
                    }
                    item {
                        DetailRow(
                            label = stringResource(R.string.visit_reason),
                            value = getVisitReasonLabel(visit.visitReason, selectedLanguage)
                        )
                    }
                    if (!visit.visitReasonCustom.isNullOrBlank()) {
                        item { DetailRow(stringResource(R.string.custom_reason), visit.visitReasonCustom) }
                    }

                    item { HorizontalDivider() }

                    // Visit times section
                    item {
                        Text(
                            text       = stringResource(R.string.visit_times),
                            style      = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color      = SlatePrimary
                        )
                    }
                    item { DetailRow(stringResource(R.string.entry_time), entryDateStr) }
                    item { DetailRow(stringResource(R.string.exit_time),  exitDateStr) }
                    item { DetailRow(stringResource(R.string.duration),   duration) }

                    item { HorizontalDivider() }

                    // Technical info
                    item {
                        Text(
                            text       = stringResource(R.string.technical_info),
                            style      = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color      = SlatePrimary
                        )
                    }
                    item {
                        DetailRow(
                            label      = stringResource(R.string.visit_id),
                            value      = visit.visitId.take(12) + "…",
                            valueStyle = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                        )
                    }
                    item {
                        DetailRow(
                            label      = stringResource(R.string.person_id),
                            value      = visit.personId.take(12) + "…",
                            valueStyle = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                        )
                    }
                    item {
                        DetailRow(
                            label      = "QR Code",
                            value      = visit.qrCodeValue.take(20) + "…",
                            valueStyle = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                        )
                    }

                    item { Spacer(Modifier.height(8.dp)) }

                    // Print badge button
                    item {
                        VisitorBadgeButton(
                            visitorName    = "${visitWithInfo.personFirstName} ${visitWithInfo.personLastName}",
                            company        = visitWithInfo.personCompany,
                            visitingPerson = visit.visitingPersonName,
                            visitDate      = visit.entryDate,
                            printedDate    = System.currentTimeMillis(),
                            qrBitmap       = remember {
                                com.google.zxing.BarcodeFormat.QR_CODE.let { format ->
                                    val writer    = com.google.zxing.qrcode.QRCodeWriter()
                                    val bitMatrix = writer.encode(visit.qrCodeValue, format, 512, 512)
                                    val w = bitMatrix.width; val h = bitMatrix.height
                                    val pixels = IntArray(w * h)
                                    for (y in 0 until h) for (x in 0 until w)
                                        pixels[y * w + x] = if (bitMatrix.get(x, y))
                                            android.graphics.Color.BLACK else android.graphics.Color.WHITE
                                    android.graphics.Bitmap.createBitmap(w, h,
                                        android.graphics.Bitmap.Config.ARGB_8888)
                                        .apply { setPixels(pixels, 0, w, 0, 0, w, h) }
                                }
                            },
                            profileBitmap  = remember(visitWithInfo.personProfilePhotoPath) {
                                visitWithInfo.personProfilePhotoPath
                                    ?.let { java.io.File(it) }
                                    ?.takeIf { it.exists() }
                                    ?.let { android.graphics.BitmapFactory.decodeFile(it.absolutePath) }
                            },
                            visitorType      = visit.visitorType,
                            selectedLanguage = selectedLanguage,
                            modifier         = Modifier.fillMaxWidth()
                        )
                    }

                    item { Spacer(Modifier.height(8.dp)) }

                    // Close button
                    item {
                        Button(
                            onClick  = onDismiss,
                            modifier = Modifier.fillMaxWidth(),
                            colors   = ButtonDefaults.buttonColors(containerColor = SlatePrimary),
                            shape    = RoundedCornerShape(8.dp)
                        ) {
                            Text(stringResource(R.string.close))
                        }
                    }
                }

                // RIGHT COLUMN – photos (40 %)
                LazyColumn(
                    modifier            = Modifier
                        .weight(0.4f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Personal photo
                    item {
                        Text(
                            text       = stringResource(R.string.personal_photo),
                            style      = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color      = SlatePrimary
                        )
                    }
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            shape  = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Box(Modifier.fillMaxSize(), Alignment.Center) {
                                // Prefer visit-specific snapshot; fall back to person registration photo
                                val photoFile = remember(
                                    visitWithInfo.visitProfilePhotoPath,
                                    visitWithInfo.personProfilePhotoPath
                                ) {
                                    (visitWithInfo.visitProfilePhotoPath
                                        ?: visitWithInfo.personProfilePhotoPath)
                                        ?.let { java.io.File(it) }
                                        ?.takeIf { it.exists() }
                                }
                                if (photoFile != null) {
                                    Image(
                                        bitmap           = android.graphics.BitmapFactory
                                            .decodeFile(photoFile.absolutePath).asImageBitmap(),
                                        contentDescription = stringResource(R.string.personal_photo),
                                        modifier         = Modifier.fillMaxSize(),
                                        contentScale     = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Person, null,
                                        Modifier.size(64.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                    }

                    item { Spacer(Modifier.height(4.dp)) }

                    // Documents section
                    item {
                        Text(
                            text       = stringResource(R.string.documents_verified),
                            style      = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color      = SlatePrimary
                        )
                    }

                    // Front document photo
                    item {
                        Text(
                            text  = stringResource(R.string.front_of_document),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Spacer(Modifier.height(4.dp))
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp),
                            shape  = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Box(Modifier.fillMaxSize(), Alignment.Center) {
                                val frontDoc = remember(
                                    visitWithInfo.visitDocumentFrontPath,
                                    visitWithInfo.personDocumentFrontPath
                                ) {
                                    (visitWithInfo.visitDocumentFrontPath
                                        ?: visitWithInfo.personDocumentFrontPath)
                                        ?.let { java.io.File(it) }
                                        ?.takeIf { it.exists() }
                                }
                                if (frontDoc != null) {
                                    Image(
                                        bitmap           = android.graphics.BitmapFactory
                                            .decodeFile(frontDoc.absolutePath).asImageBitmap(),
                                        contentDescription = stringResource(R.string.front_of_document),
                                        modifier         = Modifier.fillMaxSize(),
                                        contentScale     = ContentScale.Fit
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Badge, null, Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                    }

                    // Back document photo
                    item {
                        Text(
                            text  = stringResource(R.string.back_of_document),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Spacer(Modifier.height(4.dp))
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp),
                            shape  = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Box(Modifier.fillMaxSize(), Alignment.Center) {
                                val backDoc = remember(
                                    visitWithInfo.visitDocumentBackPath,
                                    visitWithInfo.personDocumentBackPath
                                ) {
                                    (visitWithInfo.visitDocumentBackPath
                                        ?: visitWithInfo.personDocumentBackPath)
                                        ?.let { java.io.File(it) }
                                        ?.takeIf { it.exists() }
                                }
                                if (backDoc != null) {
                                    Image(
                                        bitmap           = android.graphics.BitmapFactory
                                            .decodeFile(backDoc.absolutePath).asImageBitmap(),
                                        contentDescription = stringResource(R.string.back_of_document),
                                        modifier         = Modifier.fillMaxSize(),
                                        contentScale     = ContentScale.Fit
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Badge, null, Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    valueStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value,
            style = valueStyle,
            color = valueColor,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(0.6f)
        )
    }
}

@Composable
private fun getVisitorTypeLabel(type: String, language: String): String {
    return when (type) {
        "VISITOR" -> stringResource(R.string.visitor)
        "CONTRACTOR" -> stringResource(R.string.contractor)
        "VENDOR" -> stringResource(R.string.vendor)
        "DELIVERY" -> stringResource(R.string.delivery)
        "DRIVER" -> stringResource(R.string.driver)
        "TEMPORARY_STAFF" -> stringResource(R.string.temporary_staff)
        else -> type
    }
}

@Composable
private fun getVisitReasonLabel(reason: String, language: String): String {
    return when (reason) {
        "VISITOR" -> stringResource(R.string.visitor)
        "CONTRACTOR" -> stringResource(R.string.contractor)
        "VENDOR" -> stringResource(R.string.vendor)
        "DELIVERY" -> stringResource(R.string.delivery)
        "DRIVER" -> stringResource(R.string.driver)
        "TEMPORARY_STAFF" -> stringResource(R.string.temporary_staff)
        "OTHER" -> stringResource(R.string.other)
        else -> reason
    }
}

// ============================================================
// PANEL LATERAL DE FILTROS (DRAWER)
// ============================================================

@Composable
private fun FilterDrawerContent(
    filterStatus: Set<VisitFilterStatus>,
    filterVisitorType: Set<String>,
    filterFirstName: String,
    filterLastName: String,
    filterVisitingPerson: String,
    filterStartDate: Long?,
    filterEndDate: Long?,
    onFilterStatusChange: (Set<VisitFilterStatus>) -> Unit,
    onFilterVisitorTypeChange: (Set<String>) -> Unit,
    onFilterFirstNameChange: (String) -> Unit,
    onFilterLastNameChange: (String) -> Unit,
    onFilterVisitingPersonChange: (String) -> Unit,
    onFilterStartDateChange: (Long?) -> Unit,
    onFilterEndDateChange: (Long?) -> Unit,
    onClearFilters: () -> Unit,
    onClose: () -> Unit,
    selectedLanguage: String
) {
    var statusExpanded    by remember { mutableStateOf(false) }
    var visitorTypeExpanded by remember { mutableStateOf(false) }

    val statusOptions = listOf(
        VisitFilterStatus.ACTIVE    to stringResource(R.string.active),
        VisitFilterStatus.COMPLETED to stringResource(R.string.completed)
    )
    val visitorTypeOptions = listOf(
        "VISITOR"        to stringResource(R.string.visitor),
        "CONTRACTOR"     to stringResource(R.string.contractor),
        "VENDOR"         to stringResource(R.string.vendor),
        "DELIVERY"       to stringResource(R.string.delivery),
        "DRIVER"         to stringResource(R.string.driver),
        "TEMPORARY_STAFF" to stringResource(R.string.temporary_staff),
        "OTHER"          to stringResource(R.string.other)
    )

    // Label shown on the dropdown button (summary of selections)
    val statusLabel = when {
        filterStatus.isEmpty() -> stringResource(R.string.all)
        filterStatus.size == 1 -> statusOptions.first { it.first in filterStatus }.second
        else -> statusOptions.filter { it.first in filterStatus }.joinToString(", ") { it.second }
    }
    val typeLabel = when {
        filterVisitorType.isEmpty() -> stringResource(R.string.all)
        filterVisitorType.size == 1 -> visitorTypeOptions.first { it.first in filterVisitorType }.second
        else -> "${filterVisitorType.size} ${stringResource(R.string.selected)}"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(
                text       = stringResource(R.string.filters),
                style      = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color      = SlatePrimary
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
            }
        }

        HorizontalDivider()

        // Scrollable content
        LazyColumn(
            modifier            = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── Text filters ─────────────────────────────────────────────────
            item {
                OutlinedTextField(
                    value         = filterFirstName,
                    onValueChange = onFilterFirstNameChange,
                    label         = { Text(stringResource(R.string.first_name_filter)) },
                    modifier      = Modifier.fillMaxWidth(),
                    singleLine    = true,
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = OrangePrimary,
                        focusedLabelColor  = OrangePrimary
                    )
                )
            }
            item {
                OutlinedTextField(
                    value         = filterLastName,
                    onValueChange = onFilterLastNameChange,
                    label         = { Text(stringResource(R.string.last_name_filter)) },
                    modifier      = Modifier.fillMaxWidth(),
                    singleLine    = true,
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = OrangePrimary,
                        focusedLabelColor  = OrangePrimary
                    )
                )
            }
            item {
                OutlinedTextField(
                    value         = filterVisitingPerson,
                    onValueChange = onFilterVisitingPersonChange,
                    label         = { Text(stringResource(R.string.visiting_person_filter)) },
                    modifier      = Modifier.fillMaxWidth(),
                    singleLine    = true,
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = OrangePrimary,
                        focusedLabelColor  = OrangePrimary
                    )
                )
            }

            // ── Visit Status — collapsible multi-select dropdown ─────────────
            item {
                Text(
                    text       = stringResource(R.string.visit_status),
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color      = SlatePrimary
                )
                Spacer(Modifier.height(6.dp))

                // Dropdown trigger
                OutlinedCard(
                    onClick = { statusExpanded = !statusExpanded },
                    modifier = Modifier.fillMaxWidth(),
                    border   = BorderStroke(
                        1.dp,
                        if (filterStatus.isNotEmpty()) OrangePrimary
                        else MaterialTheme.colorScheme.outline
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier              = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text(
                            text  = statusLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (filterStatus.isNotEmpty()) OrangePrimary
                                    else MaterialTheme.colorScheme.onSurface
                        )
                        Icon(
                            imageVector = if (statusExpanded) Icons.Default.ExpandLess
                                          else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Dropdown options
                AnimatedVisibility(visible = statusExpanded) {
                    Card(
                        modifier  = Modifier.fillMaxWidth().padding(top = 4.dp),
                        shape     = RoundedCornerShape(8.dp),
                        colors    = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                            statusOptions.forEach { (status, label) ->
                                val isSelected = filterStatus.contains(status)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val newSet = filterStatus.toMutableSet()
                                            if (isSelected) newSet.remove(status)
                                            else newSet.add(status)
                                            onFilterStatusChange(newSet)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Checkbox(
                                        checked  = isSelected,
                                        onCheckedChange = {
                                            val newSet = filterStatus.toMutableSet()
                                            if (isSelected) newSet.remove(status)
                                            else newSet.add(status)
                                            onFilterStatusChange(newSet)
                                        },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor   = OrangePrimary,
                                            checkmarkColor = Color.White
                                        )
                                    )
                                    Text(label, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }

            // ── Visitor Type — collapsible multi-select dropdown ─────────────
            item {
                Text(
                    text       = stringResource(R.string.visitor_type_filter),
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color      = SlatePrimary
                )
                Spacer(Modifier.height(6.dp))

                // Dropdown trigger
                OutlinedCard(
                    onClick  = { visitorTypeExpanded = !visitorTypeExpanded },
                    modifier = Modifier.fillMaxWidth(),
                    border   = BorderStroke(
                        1.dp,
                        if (filterVisitorType.isNotEmpty()) OrangePrimary
                        else MaterialTheme.colorScheme.outline
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier              = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text(
                            text  = typeLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (filterVisitorType.isNotEmpty()) OrangePrimary
                                    else MaterialTheme.colorScheme.onSurface
                        )
                        Icon(
                            imageVector = if (visitorTypeExpanded) Icons.Default.ExpandLess
                                          else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Dropdown options
                AnimatedVisibility(visible = visitorTypeExpanded) {
                    Card(
                        modifier  = Modifier.fillMaxWidth().padding(top = 4.dp),
                        shape     = RoundedCornerShape(8.dp),
                        colors    = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                            visitorTypeOptions.forEach { (type, label) ->
                                val isSelected = filterVisitorType.contains(type)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val newSet = filterVisitorType.toMutableSet()
                                            if (isSelected) newSet.remove(type)
                                            else newSet.add(type)
                                            onFilterVisitorTypeChange(newSet)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Checkbox(
                                        checked  = isSelected,
                                        onCheckedChange = {
                                            val newSet = filterVisitorType.toMutableSet()
                                            if (isSelected) newSet.remove(type)
                                            else newSet.add(type)
                                            onFilterVisitorTypeChange(newSet)
                                        },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor   = OrangePrimary,
                                            checkmarkColor = Color.White
                                        )
                                    )
                                    Text(label, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Action buttons
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick  = onClearFilters,
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(8.dp),
                border   = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Icon(Icons.Default.Clear, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.clear_filters))
            }
            Button(
                onClick  = onClose,
                modifier = Modifier.fillMaxWidth(),
                colors   = ButtonDefaults.buttonColors(containerColor = OrangePrimary),
                shape    = RoundedCornerShape(8.dp)
            ) {
                Text(stringResource(R.string.apply_filters))
            }
        }
    }
}

// ── Printer Settings Dialog ───────────────────────────────────────────────────

@Composable
private fun PrinterSettingsDialog(onDismiss: () -> Unit) {
    val context        = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val configFlow    = remember { PrinterConfigRepository.getConfig(context) }
    val currentConfig by configFlow.collectAsState(initial = PrinterConfig())

    var selectedBrand  by remember(currentConfig) { mutableStateOf(currentConfig.brand) }
    var selectedType   by remember(currentConfig) { mutableStateOf(currentConfig.connectionType) }
    var ipAddress      by remember(currentConfig) { mutableStateOf(currentConfig.networkHost ?: "") }
    var port           by remember(currentConfig) { mutableStateOf(currentConfig.networkPort.toString()) }
    var brotherModel   by remember(currentConfig) { mutableStateOf(currentConfig.brotherModel) }
    var showModelMenu  by remember { mutableStateOf(false) }
    var testResult     by remember { mutableStateOf<String?>(null) }
    var isTesting      by remember { mutableStateOf(false) }
    var isSaving       by remember { mutableStateOf(false) }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier  = Modifier.fillMaxWidth(0.76f),
            shape     = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {

                // ── Title ──────────────────────────────────────────────────
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Print, null, tint = SlatePrimary, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.printer_settings),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold, color = SlatePrimary
                    )
                }
                Spacer(Modifier.height(20.dp))

                // ── Brand selector ─────────────────────────────────────────
                Text(stringResource(R.string.printer_brand),
                    style = MaterialTheme.typography.labelLarge, color = SlatePrimary)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(
                        PrinterConfig.PrinterBrand.NONE    to stringResource(R.string.printer_brand_none),
                        PrinterConfig.PrinterBrand.ZEBRA   to stringResource(R.string.printer_brand_zebra),
                        PrinterConfig.PrinterBrand.BROTHER to stringResource(R.string.printer_brand_brother)
                    ).forEach { (brand, label) ->
                        FilterChip(
                            selected = selectedBrand == brand,
                            onClick  = { selectedBrand = brand; testResult = null },
                            label    = { Text(label, fontSize = 13.sp) },
                            colors   = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = OrangePrimary,
                                selectedLabelColor     = Color.White
                            )
                        )
                    }
                }

                // ── Brand-specific options ─────────────────────────────────
                if (selectedBrand != PrinterConfig.PrinterBrand.NONE) {
                    Spacer(Modifier.height(16.dp))

                    // Connection type
                    Text(stringResource(R.string.connection_type),
                        style = MaterialTheme.typography.labelLarge, color = SlatePrimary)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PrinterConfig.ConnectionType.entries.forEach { type ->
                            val label = if (type == PrinterConfig.ConnectionType.USB)
                                stringResource(R.string.printer_usb)
                            else stringResource(R.string.printer_network)
                            FilterChip(
                                selected = selectedType == type,
                                onClick  = { selectedType = type },
                                label    = { Text(label, fontSize = 13.sp) },
                                colors   = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = OrangePrimary,
                                    selectedLabelColor     = Color.White
                                )
                            )
                        }
                    }

                    // IP + port (NETWORK only)
                    if (selectedType == PrinterConfig.ConnectionType.NETWORK) {
                        Spacer(Modifier.height(14.dp))
                        OutlinedTextField(
                            value         = ipAddress,
                            onValueChange = { ipAddress = it },
                            label         = { Text(stringResource(R.string.printer_ip_address)) },
                            singleLine    = true,
                            modifier      = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value         = port,
                            onValueChange = { port = it.filter { c -> c.isDigit() } },
                            label         = { Text(stringResource(R.string.printer_port)) },
                            singleLine    = true,
                            modifier      = Modifier.fillMaxWidth(0.4f)
                        )
                    }

                    // Brother model picker
                    if (selectedBrand == PrinterConfig.PrinterBrand.BROTHER) {
                        Spacer(Modifier.height(14.dp))
                        Text(stringResource(R.string.brother_model),
                            style = MaterialTheme.typography.labelLarge, color = SlatePrimary)
                        Spacer(Modifier.height(6.dp))
                        Box {
                            OutlinedButton(
                                onClick  = { showModelMenu = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape    = RoundedCornerShape(8.dp)
                            ) {
                                val display = PrinterConfig.BrotherModel.entries
                                    .firstOrNull { it.name == brotherModel }?.displayName ?: brotherModel
                                Text(display, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(20.dp))
                            }
                            DropdownMenu(
                                expanded         = showModelMenu,
                                onDismissRequest = { showModelMenu = false }
                            ) {
                                PrinterConfig.BrotherModel.entries.forEach { model ->
                                    DropdownMenuItem(
                                        text    = { Text(model.displayName, fontSize = 13.sp) },
                                        onClick = {
                                            brotherModel  = model.name
                                            showModelMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Test result banner ─────────────────────────────────────
                testResult?.let { msg ->
                    Spacer(Modifier.height(12.dp))
                    val ok = msg == "OK"
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (ok) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                        ), shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            if (ok) "✓ ${stringResource(R.string.printer_connected)}" else "✗ $msg",
                            modifier = Modifier.padding(12.dp),
                            color    = if (ok) Color(0xFF2E7D32) else Color(0xFFC62828),
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // ── Action buttons ─────────────────────────────────────────
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                isTesting = true; testResult = null
                                val cfg = PrinterConfig(
                                    brand          = selectedBrand,
                                    connectionType = selectedType,
                                    networkHost    = ipAddress.ifBlank { null },
                                    networkPort    = port.toIntOrNull() ?: PrinterConfig.DEFAULT_PORT,
                                    brotherModel   = brotherModel
                                )
                                testResult = PrinterManager.testConnection(context, cfg) ?: "OK"
                                isTesting = false
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled  = !isTesting && !isSaving && selectedBrand != PrinterConfig.PrinterBrand.NONE
                    ) {
                        if (isTesting) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        else Text(stringResource(R.string.printer_test), fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isSaving = true
                                PrinterConfigRepository.saveConfig(
                                    context,
                                    PrinterConfig(
                                        brand          = selectedBrand,
                                        connectionType = selectedType,
                                        networkHost    = ipAddress.ifBlank { null },
                                        networkPort    = port.toIntOrNull() ?: PrinterConfig.DEFAULT_PORT,
                                        brotherModel   = brotherModel
                                    )
                                )
                                isSaving = false; onDismiss()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors   = ButtonDefaults.buttonColors(containerColor = OrangePrimary),
                        enabled  = !isTesting && !isSaving
                    ) {
                        if (isSaving) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        else Text(stringResource(R.string.save), fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
