package com.eflglobal.visitorsapp.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eflglobal.visitorsapp.domain.model.Person
import com.eflglobal.visitorsapp.domain.model.Visit
import com.eflglobal.visitorsapp.domain.model.VisitReasonKeys
import com.eflglobal.visitorsapp.domain.repository.PersonRepository
import com.eflglobal.visitorsapp.domain.repository.VisitRepository
import com.eflglobal.visitorsapp.domain.usecase.visit.CreateVisitUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel para crear visita recurrente (visitante ya registrado).
 */
class RecurrentVisitViewModel(
    private val createVisitUseCase: CreateVisitUseCase,
    private val personRepository: PersonRepository,
    private val visitRepository: VisitRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<RecurrentVisitUiState>(RecurrentVisitUiState.Idle)
    val uiState: StateFlow<RecurrentVisitUiState> = _uiState.asStateFlow()

    /** Emits the last visit once it is loaded — screen observes to pre-fill fields. */
    private val _lastVisitPreFill = MutableStateFlow<Visit?>(null)
    val lastVisitPreFill: StateFlow<Visit?> = _lastVisitPreFill.asStateFlow()

    private var selectedPerson: Person? = null

    /** Last visit data for this person — pre-fills form fields. */
    private var lastVisit: Visit? = null

    var documentType: String = "DUI"; private set

    private var visitorType: String = VisitReasonKeys.VISITOR
    private var visitReason: String = VisitReasonKeys.VISITOR
    private var visitReasonCustom: String? = null

    // Document scan paths — mirrors NewVisitViewModel (observable for Compose)
    var documentFrontPath: String? by mutableStateOf(null); private set
    var documentBackPath:  String? by mutableStateOf(null); private set

    // Profile photo taken on this visit (may differ from person.profilePhotoPath)
    var profilePhotoPath: String? = null; private set

    /**
     * Pre-generated visitId for this recurring visit flow.
     * Photos are saved to visits/{visitId}/ for an immutable audit trail.
     */
    private var visitId: String? = null

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

    fun setSelectedPerson(person: Person) {
        selectedPerson = person
        _lastVisitPreFill.value = null  // reset while loading
        // Load last visit to pre-fill form fields
        viewModelScope.launch {
            val last = visitRepository.getLastVisitByPersonId(person.personId)
            lastVisit = last
            if (last != null) {
                // Pre-fill visitor type and visit reason from most recent visit
                visitorType         = last.visitorType
                visitReason         = last.visitReason
                visitReasonCustom   = last.visitReasonCustom
                draftVisitingPerson = last.visitingPersonName
                _lastVisitPreFill.value = last
            }
        }
    }

    fun setDocumentType(type: String)     { documentType = type }
    fun setVisitorType(type: String)      { visitorType = type }
    fun setDocumentFront(path: String)    { documentFrontPath = path }
    fun setDocumentBack(path: String)     { documentBackPath  = path }
    fun setProfilePhoto(path: String)     { profilePhotoPath  = path }

    /**
     * Returns the pre-generated visitId for this recurring visit flow.
     * All photos should be saved to visits/{visitId}/ using this ID.
     */
    fun getVisitId(): String {
        if (visitId == null) visitId = java.util.UUID.randomUUID().toString()
        return visitId!!
    }

    fun setVisitReason(key: String, custom: String? = null) {
        visitReason       = key
        visitReasonCustom = if (key == VisitReasonKeys.OTHER) custom else null
    }

    fun getSelectedPerson(): Person? = selectedPerson
    fun getLastVisit(): Visit? = lastVisit

    fun createVisit(
        visitingPersonName: String,
        editedFirstName: String? = null,
        editedLastName: String? = null,
        editedCompany: String? = null,
        editedEmail: String? = null,
        editedPhone: String? = null
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

        val finalFirstName = (editedFirstName?.trim()?.ifBlank { null } ?: person.firstName).trim()
        val finalLastName = (editedLastName?.trim()?.ifBlank { null } ?: person.lastName).trim()
        val finalCompany = editedCompany?.trim()?.ifBlank { null } ?: person.company
        val finalEmail = (editedEmail?.trim()?.ifBlank { null } ?: person.email).trim()
        val finalPhone = (editedPhone?.trim()?.ifBlank { null } ?: person.phoneNumber).trim()

        val displayName = buildString {
            append(finalFirstName)
            if (finalLastName.isNotBlank()) { append(" "); append(finalLastName) }
        }.trim().ifBlank { person.fullName }

        _uiState.value = RecurrentVisitUiState.Loading

        viewModelScope.launch {
            try {
                val newPhoto  = profilePhotoPath
                val newFront  = documentFrontPath?.ifBlank { null }
                val newBack   = documentBackPath?.ifBlank  { null }

                // Update PersonEntity only for personal data changes (name, contact, company).
                // Profile photo in PersonEntity is updated so list views show the current face.
                // Document photos are NOT updated — they belong to VisitEntity as audit snapshots.
                val hasPersonChanges =
                    finalFirstName != person.firstName ||
                    finalLastName  != person.lastName  ||
                    finalCompany   != person.company   ||
                    finalEmail     != person.email     ||
                    finalPhone     != person.phoneNumber ||
                    (newPhoto != null && newPhoto != person.profilePhotoPath)

                if (hasPersonChanges) {
                    personRepository.updatePerson(
                        person.copy(
                            firstName        = finalFirstName,
                            lastName         = finalLastName,
                            company          = finalCompany,
                            email            = finalEmail,
                            phoneNumber      = finalPhone,
                            profilePhotoPath = newPhoto ?: person.profilePhotoPath
                            // documentFrontPath / documentBackPath intentionally NOT updated:
                            // visit-specific docs go into VisitEntity.visitDocumentFrontPath/Back
                        )
                    )
                }

                // Create the new visit with its own immutable photo snapshots
                val result = createVisitUseCase(
                    personId               = person.personId,
                    visitingPersonName     = visitingPersonName,
                    visitorType            = visitorType,
                    visitReason            = visitReason,
                    visitReasonCustom      = visitReasonCustom,
                    visitId                = getVisitId(),
                    visitProfilePhotoPath  = newPhoto,
                    visitDocumentFrontPath = newFront,
                    visitDocumentBackPath  = newBack
                )
                result.fold(
                    onSuccess = { visit ->
                        _uiState.value = RecurrentVisitUiState.Success(
                            qrCode           = visit.qrCodeValue,
                            personName       = displayName,
                            firstName        = finalFirstName,
                            lastName         = finalLastName,
                            visitingPerson   = visitingPersonName,
                            company          = finalCompany,
                            profilePhotoPath = newPhoto ?: person.profilePhotoPath,
                            visitorType      = visitorType
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
        _uiState.value          = RecurrentVisitUiState.Idle
        _lastVisitPreFill.value = null
        selectedPerson          = null
        lastVisit               = null
        documentType            = "DUI"
        visitorType          = VisitReasonKeys.VISITOR
        visitReason          = VisitReasonKeys.VISITOR
        visitReasonCustom    = null
        documentFrontPath    = null
        documentBackPath     = null
        profilePhotoPath     = null
        visitId              = null
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

    /**
     * Resets document scan + photo state only.
     * Called when the user goes back from RecurrentDocumentScanScreen.
     * Also resets visitId so photos are saved under a fresh visitId on retry.
     */
    fun resetDocuments() {
        documentFrontPath = null
        documentBackPath  = null
        profilePhotoPath  = null
        documentType      = "DUI"
        visitorType       = VisitReasonKeys.VISITOR
        visitId           = null  // fresh visitId for next scan attempt
    }
}

sealed class RecurrentVisitUiState {
    object Idle    : RecurrentVisitUiState()
    object Loading : RecurrentVisitUiState()
    data class Success(
        val qrCode: String,
        val personName: String,
        val firstName: String,
        val lastName: String,
        val visitingPerson: String,
        val company: String?,
        val profilePhotoPath: String? = null,
        val visitorType: String = "VISITOR"
    ) : RecurrentVisitUiState()
    data class Error(val message: String) : RecurrentVisitUiState()
}
