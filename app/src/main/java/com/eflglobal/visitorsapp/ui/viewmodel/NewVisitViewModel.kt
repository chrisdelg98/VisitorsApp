package com.eflglobal.visitorsapp.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eflglobal.visitorsapp.core.ocr.DocumentDataExtractor
import com.eflglobal.visitorsapp.domain.model.VisitReasonKeys
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

    // ── Transient flow data ────────────────────────────────────────────────────
    private var personId: String? = null

    /**
     * Pre-generated visitId — generated on first call to getVisitId().
     * Photos are saved to visits/{visitId}/ for an immutable audit trail.
     */
    private var visitId: String? = null

    private var documentType: String = ""

    /** "Yo soy un:" — who the visitor IS (VISITOR, CONTRACTOR, VENDOR, DELIVERY…) */
    private var visitorType: String = VisitReasonKeys.VISITOR

    /** "Motivo de visita" — WHY they are visiting (from PersonDataScreen) */
    private var visitReason: String = VisitReasonKeys.MEETING

    /** Free-text description — only used when visitReason == "OTHER". */
    private var visitReasonCustom: String? = null

    var documentFrontPath: String? by mutableStateOf(null); private set
    var documentBackPath:  String? by mutableStateOf(null); private set

    /** First name detected by OCR (from document front). */
    private var detectedFirstName: String? = null

    /** Last name detected by OCR (from document front). */
    private var detectedLastName: String? = null

    /** Document number detected by OCR — may be null. */
    private var documentNumber: String? = null

    /** Source layer that produced the name/docNumber (MRZ, OCR_KEYED, HEURISTIC, NONE). */
    private var extractionSource: DocumentDataExtractor.ExtractionSource =
        DocumentDataExtractor.ExtractionSource.NONE

    /** Confidence level of the extraction. */
    private var extractionConfidence: DocumentDataExtractor.Confidence =
        DocumentDataExtractor.Confidence.NONE

    // ── Public accessors ──────────────────────────────────────────────────────

    fun getPersonId(): String {
        if (personId == null) personId = java.util.UUID.randomUUID().toString()
        return personId!!
    }

    /**
     * Returns the pre-generated visitId for this visit flow.
     * All photos should be saved to visits/{visitId}/ using this ID.
     */
    fun getVisitId(): String {
        if (visitId == null) visitId = java.util.UUID.randomUUID().toString()
        return visitId!!
    }

    fun setDocumentType(type: String) { documentType = type }

    /** Sets "Yo soy un:" — who the visitor IS. Called from DocumentScanScreen. */
    fun setVisitorType(type: String) { visitorType = type }

    fun setVisitReason(key: String, custom: String? = null) {
        visitReason       = key
        visitReasonCustom = if (key == VisitReasonKeys.OTHER) custom else null
    }

    fun setDocumentFront(
        path: String,
        firstName: String? = null,
        lastName: String? = null,
        docNumber: String? = null,
        source: DocumentDataExtractor.ExtractionSource = DocumentDataExtractor.ExtractionSource.NONE,
        confidence: DocumentDataExtractor.Confidence   = DocumentDataExtractor.Confidence.NONE
    ) {
        documentFrontPath    = path
        documentNumber       = docNumber?.ifBlank { null }
        detectedFirstName    = firstName?.ifBlank { null }
        detectedLastName     = lastName?.ifBlank { null }
        extractionSource     = source
        extractionConfidence = confidence
    }

    fun setDocumentBack(path: String) { documentBackPath = path }

    fun getDetectedFirstName(): String? = detectedFirstName
    fun getDetectedLastName(): String?  = detectedLastName
    /** Convenience — joined for backward-compat display. */
    fun getDetectedName(): String? =
        listOfNotNull(detectedFirstName, detectedLastName)
            .joinToString(" ")
            .ifBlank { null }

    fun getDocumentNumber(): String?                               = documentNumber
    fun getExtractionSource(): DocumentDataExtractor.ExtractionSource = extractionSource
    fun getExtractionConfidence(): DocumentDataExtractor.Confidence   = extractionConfidence

    // ── Business logic ────────────────────────────────────────────────────────

    fun createPersonAndVisit(
        firstName: String,
        lastName: String,
        email: String,
        phoneNumber: String,
        company: String?,
        visitingPersonName: String,
        profilePhotoPath: String?
    ) {
        if (firstName.isBlank() || email.isBlank() || phoneNumber.isBlank() || visitingPersonName.isBlank()) {
            _uiState.value = NewVisitUiState.Error("All required fields must be filled")
            return
        }
        if (visitReason == VisitReasonKeys.OTHER && visitReasonCustom.isNullOrBlank()) {
            _uiState.value = NewVisitUiState.Error("Please describe the reason for your visit")
            return
        }

        _uiState.value = NewVisitUiState.Loading

        viewModelScope.launch {
            try {
                // Look up existing person by document number (if available).
                // If found: reuse their personId — do NOT update PersonEntity.
                // PersonEntity is an immutable identity record after first registration.
                val existingPerson = documentNumber?.let { getPersonByDocumentUseCase(it) }

                val resolvedPersonId = if (existingPerson != null) {
                    existingPerson.personId
                } else {
                    // Brand-new person — create identity record with today's photos
                    createPersonUseCase(
                        firstName         = firstName,
                        lastName          = lastName,
                        documentNumber    = documentNumber,
                        documentType      = documentType,
                        email             = email,
                        phoneNumber       = phoneNumber,
                        company           = company,
                        profilePhotoPath  = profilePhotoPath,
                        documentFrontPath = documentFrontPath,
                        documentBackPath  = documentBackPath
                    ).fold(
                        onSuccess = { it.personId },
                        onFailure = { error ->
                            _uiState.value = NewVisitUiState.Error(
                                error.message ?: "Failed to create person"
                            )
                            return@launch
                        }
                    )
                }

                // Always create a new visit with its own immutable photo snapshots
                createVisitUseCase(
                    personId               = resolvedPersonId,
                    visitingPersonName     = visitingPersonName,
                    visitorType            = visitorType,
                    visitReason            = visitReason,
                    visitReasonCustom      = visitReasonCustom,
                    visitId                = getVisitId(),
                    visitProfilePhotoPath  = profilePhotoPath,
                    visitDocumentFrontPath = documentFrontPath,
                    visitDocumentBackPath  = documentBackPath
                ).fold(
                    onSuccess = { visit ->
                        _uiState.value = NewVisitUiState.Success(
                            qrCode           = visit.qrCodeValue,
                            personName       = "$firstName $lastName".trim(),
                            firstName        = firstName,
                            lastName         = lastName,
                            visitingPerson   = visitingPersonName,
                            company          = company,
                            profilePhotoPath = profilePhotoPath,
                            visitorType      = visitorType
                        )
                    },
                    onFailure = { error ->
                        _uiState.value = NewVisitUiState.Error(
                            error.message ?: "Failed to create visit"
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = NewVisitUiState.Error(e.message ?: "An unexpected error occurred")
            }
        }
    }

    fun resetState() {
        _uiState.value       = NewVisitUiState.Idle
        documentType         = ""
        visitorType          = VisitReasonKeys.VISITOR
        visitReason          = VisitReasonKeys.MEETING
        visitReasonCustom    = null
        documentFrontPath    = null
        documentBackPath     = null
        detectedFirstName    = null
        detectedLastName     = null
        documentNumber       = null
        extractionSource     = DocumentDataExtractor.ExtractionSource.NONE
        extractionConfidence = DocumentDataExtractor.Confidence.NONE
        personId             = null
        visitId              = null   // new visitId will be generated on next flow
    }

    /**
     * Called when user taps "Edit" on ConfirmScreen.
     * Resets ONLY the UiState back to Idle so PersonDataScreen doesn't
     * immediately re-navigate forward — all scanned/entered data is preserved.
     */
    fun resetToEditing() {
        _uiState.value = NewVisitUiState.Idle
    }

    /**
     * Resets document scan state only.
     * Called when user navigates back from PersonDataScreen to DocumentScanScreen.
     * Clears paths + OCR data so scan cards show as "not scanned" and user must re-scan.
     * Also resets visitId so photos are saved under a fresh visitId on retry.
     */
    fun resetDocuments() {
        documentFrontPath    = null
        documentBackPath     = null
        detectedFirstName    = null
        detectedLastName     = null
        documentNumber       = null
        extractionSource     = DocumentDataExtractor.ExtractionSource.NONE
        extractionConfidence = DocumentDataExtractor.Confidence.NONE
        visitId              = null
    }
}

sealed class NewVisitUiState {
    object Idle    : NewVisitUiState()
    object Loading : NewVisitUiState()
    data class Success(
        val qrCode: String,
        val personName: String,
        val firstName: String,
        val lastName: String,
        val visitingPerson: String,
        val company: String?,
        val profilePhotoPath: String? = null,
        val visitorType: String = "VISITOR"
    ) : NewVisitUiState()
    data class Error(val message: String) : NewVisitUiState()
}
