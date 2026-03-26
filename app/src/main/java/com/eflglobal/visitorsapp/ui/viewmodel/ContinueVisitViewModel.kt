package com.eflglobal.visitorsapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eflglobal.visitorsapp.domain.model.Visit
import com.eflglobal.visitorsapp.domain.usecase.visit.ContinueVisitUseCase
import com.eflglobal.visitorsapp.domain.usecase.visit.VisitLookupResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class ContinueVisitUiState {
    object Idle : ContinueVisitUiState()
    object Loading : ContinueVisitUiState()

    /** QR resolved — show confirmation modal. */
    data class ConfirmationNeeded(
        val lookup: VisitLookupResult
    ) : ContinueVisitUiState()

    /** Same-station re-entry committed. */
    data class ReentryConfirmed(
        val personName: String,
        val reentryCount: Int
    ) : ContinueVisitUiState()

    /**
     * Cross-station continuation committed.
     * The new visit carries a fresh QR — offer badge reprint.
     */
    data class ContinuationConfirmed(
        val personName: String,
        val newVisit: Visit
    ) : ContinueVisitUiState()

    data class Error(val message: String) : ContinueVisitUiState()
}

/**
 * ViewModel for the "Continue Visit" flow.
 *
 * Responsibilities:
 *  1. Scan a QR code → look up visit context ([lookupByQR]).
 *  2. Display confirmation modal (drives [ContinueVisitUiState.ConfirmationNeeded]).
 *  3. Commit re-entry ([confirmReentry]) or continuation ([confirmContinuation]).
 */
class ContinueVisitViewModel(
    private val continueVisitUseCase: ContinueVisitUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<ContinueVisitUiState>(ContinueVisitUiState.Idle)
    val uiState: StateFlow<ContinueVisitUiState> = _uiState.asStateFlow()

    // ── Phase 1: QR Scan ─────────────────────────────────────────────────

    fun lookupByQR(qrCode: String) {
        if (_uiState.value is ContinueVisitUiState.Loading) return
        _uiState.value = ContinueVisitUiState.Loading
        viewModelScope.launch {
            continueVisitUseCase.lookupVisit(qrCode).fold(
                onSuccess = { lookup ->
                    _uiState.value = ContinueVisitUiState.ConfirmationNeeded(lookup)
                },
                onFailure = { err ->
                    _uiState.value = ContinueVisitUiState.Error(
                        err.message ?: "lookup_failed"
                    )
                }
            )
        }
    }

    // ── Phase 2a: Same-station re-entry ──────────────────────────────────

    fun confirmReentry() {
        val state = _uiState.value as? ContinueVisitUiState.ConfirmationNeeded ?: return
        val lookup = state.lookup
        _uiState.value = ContinueVisitUiState.Loading
        viewModelScope.launch {
            continueVisitUseCase.confirmReentry(lookup.visit.visitId).fold(
                onSuccess = {
                    _uiState.value = ContinueVisitUiState.ReentryConfirmed(
                        personName   = lookup.personFullName,
                        reentryCount = lookup.visit.reentryCount + 1
                    )
                },
                onFailure = { err ->
                    _uiState.value = ContinueVisitUiState.Error(
                        err.message ?: "reentry_failed"
                    )
                }
            )
        }
    }

    // ── Phase 2b: Cross-station continuation ─────────────────────────────

    fun confirmContinuation() {
        val state = _uiState.value as? ContinueVisitUiState.ConfirmationNeeded ?: return
        val lookup = state.lookup
        _uiState.value = ContinueVisitUiState.Loading
        viewModelScope.launch {
            continueVisitUseCase.confirmContinuation(lookup).fold(
                onSuccess = { newVisit ->
                    _uiState.value = ContinueVisitUiState.ContinuationConfirmed(
                        personName = lookup.personFullName,
                        newVisit   = newVisit
                    )
                },
                onFailure = { err ->
                    _uiState.value = ContinueVisitUiState.Error(
                        err.message ?: "continuation_failed"
                    )
                }
            )
        }
    }

    // ── Shared confirm dispatch (called by auto-timer OR manual button) ──

    fun confirm() {
        val state = _uiState.value as? ContinueVisitUiState.ConfirmationNeeded ?: return
        if (state.lookup.isSameStation) confirmReentry() else confirmContinuation()
    }

    fun resetState() {
        _uiState.value = ContinueVisitUiState.Idle
    }
}

