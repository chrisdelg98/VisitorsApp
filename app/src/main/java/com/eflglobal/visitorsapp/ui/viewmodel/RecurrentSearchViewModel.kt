package com.eflglobal.visitorsapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eflglobal.visitorsapp.domain.model.Person
import com.eflglobal.visitorsapp.domain.usecase.person.SearchPersonsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel para buscar visitantes recurrentes.
 */
class RecurrentSearchViewModel(
    private val searchPersonsUseCase: SearchPersonsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<RecurrentSearchUiState>(RecurrentSearchUiState.Idle)
    val uiState: StateFlow<RecurrentSearchUiState> = _uiState.asStateFlow()

    fun searchPerson(query: String) {
        if (query.length < 3) {
            _uiState.value = RecurrentSearchUiState.Idle
            return
        }

        _uiState.value = RecurrentSearchUiState.Loading

        viewModelScope.launch {
            try {
                val results = searchPersonsUseCase(query)
                if (results.isEmpty()) {
                    _uiState.value = RecurrentSearchUiState.NoResults
                } else {
                    _uiState.value = RecurrentSearchUiState.Success(results)
                }
            } catch (e: Exception) {
                _uiState.value = RecurrentSearchUiState.Error(
                    e.message ?: "Search failed"
                )
            }
        }
    }

    fun clearSearch() {
        _uiState.value = RecurrentSearchUiState.Idle
    }
}

sealed class RecurrentSearchUiState {
    object Idle : RecurrentSearchUiState()
    object Loading : RecurrentSearchUiState()
    object NoResults : RecurrentSearchUiState()
    data class Success(val persons: List<Person>) : RecurrentSearchUiState()
    data class Error(val message: String) : RecurrentSearchUiState()
}

