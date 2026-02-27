package com.eflglobal.visitorsapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eflglobal.visitorsapp.core.ocr.DocumentDataExtractor
import com.eflglobal.visitorsapp.domain.model.Person
import com.eflglobal.visitorsapp.domain.model.VisitReasonKeys
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

    var documentType: String = "DUI"; private set

    private var visitorType: String = VisitReasonKeys.VISITOR
    private var visitReason: String = VisitReasonKeys.VISITOR
    private var visitReasonCustom: String? = null

    // Document scan paths — mirrors NewVisitViewModel
    var documentFrontPath: String? = null; private set
    var documentBackPath:  String? = null; private set

    // Profile photo taken on this visit (may differ from person.profilePhotoPath)
    var profilePhotoPath: String? = null; private set

    // ── Draft editable fields — survive popBackStack() for "Edit" flow ────────
    var draftFirstName:    String? = null; private set
    var draftLastName:     String? = null; private set
    var draftDoc:          String? = null; private set
    var draftCompany:      String? = null; private set
    var draftEmail:        String? = null; private set
    var draftPhone:        String? = null; private set
    var draftVisitingPerson: String? = null; private set
    var draftVisitReasonKey: String? = null; private set
    var draftVisitReasonCustom: String? = null; private set
    /** True once the user has submitted once — screen should restore drafts. */
    var hasDraft: Boolean = false; private set

    fun saveDraft(
        firstName: String, lastName: String, doc: String, company: String,
        email: String, phone: String, visitingPerson: String,
        visitReasonKey: String?, visitReasonCustom: String?
    ) {
        draftFirstName       = firstName
        draftLastName        = lastName
        draftDoc             = doc
        draftCompany         = company
        draftEmail           = email
        draftPhone           = phone
        draftVisitingPerson  = visitingPerson
        draftVisitReasonKey  = visitReasonKey
        draftVisitReasonCustom = visitReasonCustom
        hasDraft             = true
    }

    fun setSelectedPerson(person: Person) { selectedPerson = person }
    fun setDocumentType(type: String)     { documentType = type }
    fun setVisitorType(type: String)      { visitorType = type }
    fun setDocumentFront(path: String)    { documentFrontPath = path }
    fun setDocumentBack(path: String)     { documentBackPath  = path }
    fun setProfilePhoto(path: String)     { profilePhotoPath  = path }

    fun setVisitReason(key: String, custom: String? = null) {
        visitReason       = key
        visitReasonCustom = if (key == VisitReasonKeys.OTHER) custom else null
    }

    fun getSelectedPerson(): Person? = selectedPerson

    fun createVisit(
        visitingPersonName: String,
        editedFirstName: String? = null,
        editedLastName: String? = null
    ) {
        val person = selectedPerson
        if (person == null) {
            _uiState.value = RecurrentVisitUiState.Error("No person selected"); return
        }
        if (visitingPersonName.isBlank()) {
            _uiState.value = RecurrentVisitUiState.Error("Visiting person name is required"); return
        }
        if (visitReason == VisitReasonKeys.OTHER && visitReasonCustom.isNullOrBlank()) {
            _uiState.value = RecurrentVisitUiState.Error("Please describe the reason for the visit"); return
        }

        val displayName = buildString {
            append((editedFirstName?.trim()?.ifBlank { null } ?: person.firstName).trim())
            val ln = (editedLastName?.trim()?.ifBlank { null } ?: person.lastName).trim()
            if (ln.isNotBlank()) { append(" "); append(ln) }
        }.trim().ifBlank { person.fullName }

        _uiState.value = RecurrentVisitUiState.Loading

        viewModelScope.launch {
            try {
                val result = createVisitUseCase(
                    personId           = person.personId,
                    visitingPersonName = visitingPersonName,
                    visitorType        = visitorType,
                    visitReason        = visitReason,
                    visitReasonCustom  = visitReasonCustom
                )
                result.fold(
                    onSuccess = { visit ->
                        _uiState.value = RecurrentVisitUiState.Success(
                            qrCode           = visit.qrCodeValue,
                            personName       = displayName,
                            visitingPerson   = visitingPersonName,
                            company          = person.company,
                            profilePhotoPath = profilePhotoPath ?: person.profilePhotoPath
                        )
                    },
                    onFailure = { error ->
                        _uiState.value = RecurrentVisitUiState.Error(error.message ?: "Failed to create visit")
                    }
                )
            } catch (e: Exception) {
                _uiState.value = RecurrentVisitUiState.Error(e.message ?: "An unexpected error occurred")
            }
        }
    }

    fun resetState() {
        _uiState.value       = RecurrentVisitUiState.Idle
        selectedPerson       = null
        documentType         = "DUI"
        visitorType          = VisitReasonKeys.VISITOR
        visitReason          = VisitReasonKeys.VISITOR
        visitReasonCustom    = null
        documentFrontPath    = null
        documentBackPath     = null
        profilePhotoPath     = null
        draftFirstName       = null
        draftLastName        = null
        draftDoc             = null
        draftCompany         = null
        draftEmail           = null
        draftPhone           = null
        draftVisitingPerson  = null
        draftVisitReasonKey  = null
        draftVisitReasonCustom = null
        hasDraft             = false
    }

    /** Resets only UiState to Idle — preserves all captured data for editing. */
    fun resetToEditing() { _uiState.value = RecurrentVisitUiState.Idle }
}

sealed class RecurrentVisitUiState {
    object Idle    : RecurrentVisitUiState()
    object Loading : RecurrentVisitUiState()
    data class Success(
        val qrCode: String,
        val personName: String,
        val visitingPerson: String,
        val company: String?,
        val profilePhotoPath: String? = null
    ) : RecurrentVisitUiState()
    data class Error(val message: String) : RecurrentVisitUiState()
}
