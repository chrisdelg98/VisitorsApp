package com.eflglobal.visitorsapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eflglobal.visitorsapp.domain.model.Person
import com.eflglobal.visitorsapp.domain.usecase.person.CreatePersonUseCase
import com.eflglobal.visitorsapp.domain.usecase.person.GetPersonByDocumentUseCase
import com.eflglobal.visitorsapp.domain.usecase.visit.CreateVisitUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel para el flujo de nueva visita (escaneo de documento + datos personales).
 */
class NewVisitViewModel(
    private val createPersonUseCase: CreatePersonUseCase,
    private val getPersonByDocumentUseCase: GetPersonByDocumentUseCase,
    private val createVisitUseCase: CreateVisitUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<NewVisitUiState>(NewVisitUiState.Idle)
    val uiState: StateFlow<NewVisitUiState> = _uiState.asStateFlow()

    // Datos temporales del visitante durante el flujo
    private var personId: String? = null
    private var documentType: String = ""
    private var visitorType: String = "Visitante"
    private var documentFrontPath: String? = null
    private var documentBackPath: String? = null
    private var detectedName: String? = null
    private var documentNumber: String? = null

    /**
     * Obtiene o genera un personId único para esta visita.
     */
    fun getPersonId(): String {
        if (personId == null) {
            personId = java.util.UUID.randomUUID().toString()
        }
        return personId!!
    }

    fun setDocumentType(type: String) {
        documentType = type
    }

    fun setVisitorType(type: String) {
        visitorType = type
    }

    fun setDocumentFront(path: String, name: String? = null, docNumber: String? = null) {
        documentFrontPath = path
        detectedName = name
        documentNumber = docNumber
    }

    fun setDocumentBack(path: String) {
        documentBackPath = path
    }

    fun getDetectedName(): String? = detectedName

    fun getDocumentNumber(): String? = documentNumber

    fun createPersonAndVisit(
        fullName: String,
        email: String,
        phoneNumber: String,
        company: String?,
        visitingPersonName: String,
        profilePhotoPath: String?
    ) {
        if (fullName.isBlank() || email.isBlank() || phoneNumber.isBlank() || visitingPersonName.isBlank()) {
            _uiState.value = NewVisitUiState.Error("All required fields must be filled")
            return
        }

        if (documentNumber.isNullOrBlank()) {
            _uiState.value = NewVisitUiState.Error("Document number is required")
            return
        }

        _uiState.value = NewVisitUiState.Loading

        viewModelScope.launch {
            try {
                // Verificar si ya existe la persona
                val existingPerson = getPersonByDocumentUseCase(documentNumber!!)

                val personId = if (existingPerson != null) {
                    // Persona ya existe, usar su ID
                    existingPerson.personId
                } else {
                    // Crear nueva persona
                    val createResult = createPersonUseCase(
                        fullName = fullName,
                        documentNumber = documentNumber!!,
                        documentType = documentType,
                        email = email,
                        phoneNumber = phoneNumber,
                        company = company,
                        profilePhotoPath = profilePhotoPath,
                        documentFrontPath = documentFrontPath,
                        documentBackPath = documentBackPath
                    )

                    createResult.fold(
                        onSuccess = { it.personId },
                        onFailure = { error ->
                            _uiState.value = NewVisitUiState.Error(
                                error.message ?: "Failed to create person"
                            )
                            return@launch
                        }
                    )
                }

                // Crear visita
                val visitResult = createVisitUseCase(
                    personId = personId,
                    visitingPersonName = visitingPersonName,
                    visitorType = visitorType
                )

                visitResult.fold(
                    onSuccess = { visit ->
                        _uiState.value = NewVisitUiState.Success(
                            qrCode = visit.qrCodeValue,
                            personName = fullName,
                            visitingPerson = visitingPersonName,
                            company = company
                        )
                    },
                    onFailure = { error ->
                        _uiState.value = NewVisitUiState.Error(
                            error.message ?: "Failed to create visit"
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = NewVisitUiState.Error(
                    e.message ?: "An unexpected error occurred"
                )
            }
        }
    }

    fun resetState() {
        _uiState.value = NewVisitUiState.Idle
        documentType = ""
        visitorType = "Visitante"
        documentFrontPath = null
        documentBackPath = null
        detectedName = null
        documentNumber = null
    }
}

sealed class NewVisitUiState {
    object Idle : NewVisitUiState()
    object Loading : NewVisitUiState()
    data class Success(
        val qrCode: String,
        val personName: String,
        val visitingPerson: String,
        val company: String?
    ) : NewVisitUiState()
    data class Error(val message: String) : NewVisitUiState()
}

