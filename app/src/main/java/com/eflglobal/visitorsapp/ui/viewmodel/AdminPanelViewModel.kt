package com.eflglobal.visitorsapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eflglobal.visitorsapp.domain.model.Station
import com.eflglobal.visitorsapp.domain.model.Visit
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

    private var currentStation: Station? = null

    init {
        loadDashboard()
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

                // 2. Obtener visitas de esta estación
                val stationVisits = visitRepository.getVisitsByStationId(station.stationId)

                // 3. Si no hay visitas de esta estación, buscar visitas antiguas sin estación asignada
                val allVisits = if (stationVisits.isEmpty()) {
                    // Obtener todas las visitas y filtrar las que no tienen stationId
                    val allExistingVisits = visitRepository.getAllVisits()
                    val orphanVisits = allExistingVisits.filter { it.stationId == null }

                    // Si hay visitas sin estación, migrarlas automáticamente
                    if (orphanVisits.isNotEmpty()) {
                        migrateOrphanVisitsToStation(orphanVisits, station.stationId)
                        // Después de migrar, volver a cargar
                        visitRepository.getVisitsByStationId(station.stationId)
                    } else {
                        emptyList()
                    }
                } else {
                    stationVisits
                }

                // 4. Calcular estadísticas
                val activeVisits = allVisits.filter { it.exitDate == null }
                val todayVisits = getTodayVisits(allVisits)
                val thisMonthVisits = getThisMonthVisits(allVisits)

                _uiState.value = AdminPanelUiState.Success(
                    station = station,
                    totalVisits = allVisits.size,
                    activeVisits = activeVisits.size,
                    todayVisits = todayVisits.size,
                    thisMonthVisits = thisMonthVisits.size,
                    recentVisits = allVisits.take(20) // Últimas 20 visitas
                )
            } catch (e: Exception) {
                _uiState.value = AdminPanelUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Migra visitas antiguas sin stationId a la estación actual.
     */
    private suspend fun migrateOrphanVisitsToStation(orphanVisits: List<Visit>, stationId: String) {
        try {
            orphanVisits.forEach { visit ->
                val updatedVisit = visit.copy(stationId = stationId)
                visitRepository.updateVisit(updatedVisit)
            }
        } catch (e: Exception) {
            // Si falla la migración, continuar sin error crítico
            e.printStackTrace()
        }
    }

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

    private fun getTodayVisits(visits: List<Visit>): List<Visit> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfDay = calendar.timeInMillis

        return visits.filter { it.entryDate >= startOfDay }
    }

    private fun getThisMonthVisits(visits: List<Visit>): List<Visit> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfMonth = calendar.timeInMillis

        return visits.filter { it.entryDate >= startOfMonth }
    }

    fun refresh() {
        loadDashboard()
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
        val recentVisits: List<Visit>
    ) : AdminPanelUiState()
    data class Error(val message: String) : AdminPanelUiState()
}

