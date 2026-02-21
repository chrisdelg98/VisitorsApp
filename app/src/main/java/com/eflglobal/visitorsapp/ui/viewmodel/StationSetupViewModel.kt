package com.eflglobal.visitorsapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eflglobal.visitorsapp.domain.usecase.station.CreateStationUseCase
import com.eflglobal.visitorsapp.domain.usecase.station.HasActiveStationUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel para la pantalla de configuración de estación.
 */
class StationSetupViewModel(
    private val hasActiveStationUseCase: HasActiveStationUseCase,
    private val createStationUseCase: CreateStationUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<StationSetupUiState>(StationSetupUiState.Idle)
    val uiState: StateFlow<StationSetupUiState> = _uiState.asStateFlow()

    init {
        checkActiveStation()
    }

    private fun checkActiveStation() {
        viewModelScope.launch {
            try {
                val hasStation = hasActiveStationUseCase()
                if (hasStation) {
                    _uiState.value = StationSetupUiState.StationExists
                }
            } catch (e: Exception) {
                // Silently fail, stay in Idle state
            }
        }
    }

    fun validatePin(pin: String) {
        if (pin.length != 8) {
            _uiState.value = StationSetupUiState.Error("PIN must be 8 digits")
            return
        }

        _uiState.value = StationSetupUiState.Loading

        viewModelScope.launch {
            try {
                val result = createStationUseCase(
                    pin = pin,
                    stationName = "EFL SV",
                    countryCode = "SV"
                )

                result.fold(
                    onSuccess = {
                        _uiState.value = StationSetupUiState.Success
                    },
                    onFailure = { error ->
                        _uiState.value = StationSetupUiState.Error(
                            error.message ?: "Invalid PIN"
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = StationSetupUiState.Error(
                    e.message ?: "An error occurred"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = StationSetupUiState.Idle
    }
}

sealed class StationSetupUiState {
    object Idle : StationSetupUiState()
    object Loading : StationSetupUiState()
    object Success : StationSetupUiState()
    object StationExists : StationSetupUiState()
    data class Error(val message: String) : StationSetupUiState()
}

