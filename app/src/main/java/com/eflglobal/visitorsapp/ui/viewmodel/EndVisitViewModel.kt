package com.eflglobal.visitorsapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eflglobal.visitorsapp.domain.model.ActiveVisit
import com.eflglobal.visitorsapp.domain.usecase.visit.EndVisitByQRUseCase
import com.eflglobal.visitorsapp.domain.usecase.visit.SearchActiveVisitsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel para finalizar visitas mediante QR o búsqueda manual.
 */
class EndVisitViewModel(
    private val endVisitByQRUseCase: EndVisitByQRUseCase,
    private val searchActiveVisitsUseCase: SearchActiveVisitsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<EndVisitUiState>(EndVisitUiState.Idle)
    val uiState: StateFlow<EndVisitUiState> = _uiState.asStateFlow()

    /**
     * Busca visitas activas por nombre del visitante.
     */
    fun searchActiveVisits(query: String) {
        if (query.length < 3) {
            _uiState.value = EndVisitUiState.Idle
            return
        }

        _uiState.value = EndVisitUiState.Loading

        viewModelScope.launch {
            try {
                val results = searchActiveVisitsUseCase(query)
                if (results.isEmpty()) {
                    _uiState.value = EndVisitUiState.SearchNoResults
                } else {
                    // Convertir Visit a ActiveVisit
                    val activeVisits = results.map { visit ->
                        ActiveVisit(
                            visitId = visit.visitId,
                            personName = visit.personName ?: "Unknown",
                            visitingPerson = visit.visitingPersonName,
                            entryTime = formatTime(visit.entryDate)
                        )
                    }
                    _uiState.value = EndVisitUiState.SearchResults(activeVisits)
                }
            } catch (e: Exception) {
                _uiState.value = EndVisitUiState.Error(
                    e.message ?: "Search failed"
                )
            }
        }
    }

    /**
     * Finaliza una visita por visitId.
     */
    fun endVisit(visitId: String) {
        _uiState.value = EndVisitUiState.Loading

        viewModelScope.launch {
            try {
                // Generar QR code del visitId para usar endVisitByQRUseCase
                val qrCode = "VISIT-$visitId"
                val result = endVisitByQRUseCase(qrCode)

                result.fold(
                    onSuccess = {
                        _uiState.value = EndVisitUiState.Success
                    },
                    onFailure = { error ->
                        _uiState.value = EndVisitUiState.Error(
                            error.message ?: "Failed to end visit"
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = EndVisitUiState.Error(
                    e.message ?: "An unexpected error occurred"
                )
            }
        }
    }

    fun scanQRCode(qrCode: String) {
        _uiState.value = EndVisitUiState.Loading

        viewModelScope.launch {
            try {
                val result = endVisitByQRUseCase(qrCode)

                result.fold(
                    onSuccess = {
                        _uiState.value = EndVisitUiState.QRSuccess
                    },
                    onFailure = { error ->
                        _uiState.value = EndVisitUiState.Error(
                            error.message ?: "Failed to end visit"
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = EndVisitUiState.Error(
                    e.message ?: "An unexpected error occurred"
                )
            }
        }
    }

    fun endVisitManually(qrCode: String) {
        scanQRCode(qrCode)
    }

    fun resetState() {
        _uiState.value = EndVisitUiState.Idle
    }

    private fun formatTime(timestamp: Long): String {
        val sdf = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }
}

sealed class EndVisitUiState {
    object Idle : EndVisitUiState()
    object Loading : EndVisitUiState()
    object Success : EndVisitUiState()
    object QRSuccess : EndVisitUiState()
    object SearchNoResults : EndVisitUiState()
    data class SearchResults(val visits: List<ActiveVisit>) : EndVisitUiState()
    data class Error(val message: String) : EndVisitUiState()
}

