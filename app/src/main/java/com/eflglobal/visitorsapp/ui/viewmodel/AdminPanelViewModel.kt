package com.eflglobal.visitorsapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eflglobal.visitorsapp.domain.model.Station
import com.eflglobal.visitorsapp.domain.model.Visit
import com.eflglobal.visitorsapp.domain.model.VisitWithPersonInfo
import com.eflglobal.visitorsapp.domain.repository.StationRepository
import com.eflglobal.visitorsapp.domain.repository.VisitRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * ViewModel para el panel de administración.
 * Muestra estadísticas y visitas de la estación actual.
 */
class AdminPanelViewModel(
    private val stationRepository: StationRepository,
    private val visitRepository: VisitRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<AdminPanelUiState>(AdminPanelUiState.Loading)
    val uiState: StateFlow<AdminPanelUiState> = _uiState.asStateFlow()

    /** True while a server→local pull is running (drives the button spinner). */
    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    /** One-shot user-facing result of a manual sync; cleared once shown. */
    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    private var currentStation: Station? = null

    init {
        loadDashboard()
        // Pull the server's truth on open so visits closed from the portal
        // stop showing as active — silently, without the button spinner.
        syncWithServer(silent = true)
    }

    companion object {
        /** Local retention window (days) — must mirror PurgeWorker.RETENTION_VISITS_DAYS. */
        private const val RETENTION_DAYS = 10
    }

    fun loadDashboard() {
        viewModelScope.launch {
            try {
                // 1. Obtener estación activa
                val station = stationRepository.getActiveStation()
                if (station == null) {
                    _uiState.value = AdminPanelUiState.Error("No active station found")
                    return@launch
                }
                currentStation = station

                // 2. Obtener TODAS las visitas de la base de datos
                val allExistingVisits = visitRepository.getAllVisits()

                // 3. Buscar visitas que necesitan migración (null, vacío, o stationId diferente)
                val visitsToMigrate = allExistingVisits.filter {
                    it.stationId == null || it.stationId.isBlank() || it.stationId != station.stationId
                }

                // 4. Si hay visitas que migrar, hacerlo automáticamente
                if (visitsToMigrate.isNotEmpty()) {
                    migrateVisitsToStation(visitsToMigrate, station.stationId)
                }

                // 5. Obtener visitas con información de personas
                val visitsWithPersonInfo = visitRepository.getVisitsWithPersonInfoByStationId(station.stationId)

                // 6. Calcular estadísticas
                val activeVisits = visitsWithPersonInfo.filter { it.visit.exitDate == null }
                val todayVisits = getTodayVisitsWithInfo(visitsWithPersonInfo)
                val thisMonthVisits = getRecentRetentionVisits(visitsWithPersonInfo)

                _uiState.value = AdminPanelUiState.Success(
                    station = station,
                    totalVisits = visitsWithPersonInfo.size,
                    activeVisits = activeVisits.size,
                    todayVisits = todayVisits.size,
                    thisMonthVisits = thisMonthVisits.size,
                    recentVisits = visitsWithPersonInfo.take(50) // Últimas 50 visitas
                )
            } catch (e: Exception) {
                _uiState.value = AdminPanelUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Migra visitas a la estación actual.
     */
    private suspend fun migrateVisitsToStation(visits: List<Visit>, stationId: String) {
        try {
            visits.forEach { visit ->
                val updatedVisit = visit.copy(stationId = stationId)
                visitRepository.updateVisit(updatedVisit)
            }
        } catch (e: Exception) {
            // Si falla la migración, continuar sin error crítico
            e.printStackTrace()
        }
    }

    /*
    fun loadVisitsInDateRange(startDate: Long, endDate: Long) {
        viewModelScope.launch {
            try {
                val station = currentStation ?: return@launch
                val visits = visitRepository.getVisitsByStationAndDateRange(
                    stationId = station.stationId,
                    startDate = startDate,
                    endDate = endDate
                )

                // Actualizar solo las visitas mostradas
                val currentState = _uiState.value
                if (currentState is AdminPanelUiState.Success) {
                    _uiState.value = currentState.copy(recentVisits = visits)
                }
            } catch (e: Exception) {
                _uiState.value = AdminPanelUiState.Error(e.message ?: "Failed to load visits")
            }
        }
    }
    */


    private fun getTodayVisitsWithInfo(visits: List<VisitWithPersonInfo>): List<VisitWithPersonInfo> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfDay = calendar.timeInMillis

        return visits.filter { it.visit.entryDate >= startOfDay }
    }

    /**
     * Visits from the last [RETENTION_DAYS] days. This window matches the
     * PurgeWorker retention (synced visits older than that are deleted locally),
     * so showing "this month" would be misleading — there is never more than
     * ~10 days of local history.
     */
    private fun getRecentRetentionVisits(visits: List<VisitWithPersonInfo>): List<VisitWithPersonInfo> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        calendar.add(Calendar.DAY_OF_YEAR, -(RETENTION_DAYS - 1))  // include today
        val windowStart = calendar.timeInMillis

        return visits.filter { it.visit.entryDate >= windowStart }
    }

    /**
     * Toggles a visit between active (exitDate = null) and completed (exitDate = now).
     * Useful for testing without having to print badges every time.
     * Returns the updated VisitWithPersonInfo so the caller can refresh the detail view.
     */
    fun toggleVisitStatus(visitId: String, onUpdated: (VisitWithPersonInfo?) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val visit = visitRepository.getVisitById(visitId) ?: return@launch
                val updatedVisit: Visit
                if (visit.exitDate == null) {
                    // Closing: go through endVisit so the checkout reaches the
                    // backend (marks the row pending-resync + enqueues the
                    // SyncWorker). updateVisit alone would only change it locally.
                    val now = System.currentTimeMillis()
                    visitRepository.endVisit(visitId, now)
                    updatedVisit = visit.copy(exitDate = now)
                } else {
                    // Reopening: registerReentry clears the checkout on the
                    // backend and re-activates the visit (also enqueues sync).
                    visitRepository.registerReentry(visitId)
                    updatedVisit = visit.copy(exitDate = null)
                }

                // Immediately build the updated VisitWithPersonInfo from current state
                // so the modal can refresh right away (loadDashboard is async).
                val state = _uiState.value
                if (state is AdminPanelUiState.Success) {
                    val current = state.recentVisits.find { it.visit.visitId == visitId }
                    if (current != null) {
                        val updatedInfo = current.copy(visit = updatedVisit)
                        onUpdated(updatedInfo)
                    } else {
                        onUpdated(null)
                    }
                } else {
                    onUpdated(null)
                }

                // Refresh dashboard in background so stats + list also update
                loadDashboard()
            } catch (e: Exception) {
                e.printStackTrace()
                onUpdated(null)
            }
        }
    }

    fun refresh() {
        loadDashboard()
    }

    /**
     * Reconciles local visits with the server's active list, then reloads the
     * dashboard. The server is the source of truth for whether a visit is still
     * open, so this is what unsticks the "everything shows as active" case after
     * closing visits from the portal. Only pulls remote state — the upload of
     * local rows is handled separately by the SyncWorker / SyncStatusPanel.
     *
     * @param silent when true, runs without the button spinner or result toast
     *        (used for the automatic pull when the panel opens).
     */
    fun syncWithServer(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) _syncing.value = true
            visitRepository.reconcileWithServer()
                .onSuccess { changed ->
                    if (!silent) {
                        _syncMessage.value = if (changed > 0) {
                            "Se actualizaron $changed visita(s) desde el servidor."
                        } else {
                            "Todo al día con el servidor."
                        }
                    }
                }
                .onFailure {
                    if (!silent) _syncMessage.value = "No se pudo conectar con el servidor."
                }
            loadDashboard()
            _syncing.value = false
        }
    }

    /** Clears the one-shot sync message after the UI has shown it. */
    fun clearSyncMessage() {
        _syncMessage.value = null
    }

    fun logout(onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                stationRepository.deactivateCurrentStation()
                onSuccess()
            } catch (e: Exception) {
                _uiState.value = AdminPanelUiState.Error(e.message ?: "Failed to logout")
            }
        }
    }
}

sealed class AdminPanelUiState {
    object Loading : AdminPanelUiState()
    data class Success(
        val station: Station,
        val totalVisits: Int,
        val activeVisits: Int,
        val todayVisits: Int,
        val thisMonthVisits: Int,
        val recentVisits: List<VisitWithPersonInfo>
    ) : AdminPanelUiState()
    data class Error(val message: String) : AdminPanelUiState()
}

