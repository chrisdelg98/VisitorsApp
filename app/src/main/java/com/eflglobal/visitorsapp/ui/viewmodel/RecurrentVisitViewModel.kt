package com.eflglobal.visitorsapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eflglobal.visitorsapp.domain.model.Person
import com.eflglobal.visitorsapp.domain.usecase.visit.CreateVisitUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel para crear visita recurrente (visitante ya registrado).
 */
class RecurrentVisitViewModel(
    private val createVisitUseCase: CreateVisitUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<RecurrentVisitUiState>(RecurrentVisitUiState.Idle)
    val uiState: StateFlow<RecurrentVisitUiState> = _uiState.asStateFlow()

    private var selectedPerson: Person? = null
    private var visitorType: String = "Visitante"

    fun setSelectedPerson(person: Person) {
        selectedPerson = person
    }

    fun setVisitorType(type: String) {
        visitorType = type
    }

    fun getSelectedPerson(): Person? = selectedPerson

    fun createVisit(visitingPersonName: String) {
        val person = selectedPerson
        if (person == null) {
            _uiState.value = RecurrentVisitUiState.Error("No person selected")
            return
        }

        if (visitingPersonName.isBlank()) {
            _uiState.value = RecurrentVisitUiState.Error("Visiting person name is required")
            return
        }

        _uiState.value = RecurrentVisitUiState.Loading

        viewModelScope.launch {
            try {
                val result = createVisitUseCase(
                    personId = person.personId,
                    visitingPersonName = visitingPersonName,
                    visitorType = visitorType
                )

                result.fold(
                    onSuccess = { visit ->
                        _uiState.value = RecurrentVisitUiState.Success(
                            qrCode = visit.qrCodeValue,
                            personName = person.fullName,
                            visitingPerson = visitingPersonName,
                            company = person.company
                        )
                    },
                    onFailure = { error ->
                        _uiState.value = RecurrentVisitUiState.Error(
                            error.message ?: "Failed to create visit"
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = RecurrentVisitUiState.Error(
                    e.message ?: "An unexpected error occurred"
                )
            }
        }
    }

    fun resetState() {
        _uiState.value = RecurrentVisitUiState.Idle
        selectedPerson = null
        visitorType = "Visitante"
    }
}

sealed class RecurrentVisitUiState {
    object Idle : RecurrentVisitUiState()
    object Loading : RecurrentVisitUiState()
    data class Success(
        val qrCode: String,
        val personName: String,
        val visitingPerson: String,
        val company: String?
    ) : RecurrentVisitUiState()
    data class Error(val message: String) : RecurrentVisitUiState()
}

