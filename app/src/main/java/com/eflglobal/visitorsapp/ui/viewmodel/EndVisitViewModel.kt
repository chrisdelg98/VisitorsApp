package com.eflglobal.visitorsapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eflglobal.visitorsapp.domain.model.Visit
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

    fun searchActiveVisit(query: String) {
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
                    _uiState.value = EndVisitUiState.SearchResults(results)
                }
            } catch (e: Exception) {
                _uiState.value = EndVisitUiState.Error(
                    e.message ?: "Search failed"
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
}

sealed class EndVisitUiState {
    object Idle : EndVisitUiState()
    object Loading : EndVisitUiState()
    object QRSuccess : EndVisitUiState()
    object SearchNoResults : EndVisitUiState()
    data class SearchResults(val visits: List<Visit>) : EndVisitUiState()
    data class Error(val message: String) : EndVisitUiState()
}

