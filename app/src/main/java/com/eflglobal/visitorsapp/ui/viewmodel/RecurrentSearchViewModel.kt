package com.eflglobal.visitorsapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eflglobal.visitorsapp.domain.model.Person
import com.eflglobal.visitorsapp.domain.repository.PersonRepository
import com.eflglobal.visitorsapp.domain.usecase.person.SearchPersonsUseCase
import com.eflglobal.visitorsapp.domain.usecase.visit.ResolveVisitorPhotoUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * ViewModel para buscar visitantes recurrentes.
 *
 * La búsqueda va contra el servidor (fuente de verdad, encuentra visitantes ya
 * purgados localmente) con fallback a la base local cuando no hay conexión.
 * La foto de cada resultado se resuelve de forma diferida (local → servidor).
 */
class RecurrentSearchViewModel(
    private val personRepository: PersonRepository,
    private val searchPersonsUseCase: SearchPersonsUseCase,
    private val resolveVisitorPhoto: ResolveVisitorPhotoUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<RecurrentSearchUiState>(RecurrentSearchUiState.Idle)
    val uiState: StateFlow<RecurrentSearchUiState> = _uiState.asStateFlow()

    // In-memory cache of resolved photo paths so recompositions / re-renders
    // don't re-hit the network for a person we already resolved this session.
    private val photoCache = mutableMapOf<String, String?>()
    private val photoMutex = Mutex()

    fun searchPerson(query: String) {
        if (query.length < 3) {
            _uiState.value = RecurrentSearchUiState.Idle
            return
        }

        _uiState.value = RecurrentSearchUiState.Loading

        viewModelScope.launch {
            try {
                // Server is the source of truth; fall back to local search when
                // the request fails (offline / backend error).
                val results = personRepository.searchVisitorsRemote(query)
                    .getOrElse { searchPersonsUseCase(query) }

                // ── Deduplicate by full name ────────────────────────────────
                // We normalise to lowercase+trimmed to treat "Christian Arevalo"
                // and "christian arevalo" as the same person. Remote results are
                // already ordered by the backend; local ones by most recent visit.
                val distinctByName = results
                    .distinctBy { "${it.firstName.trim()} ${it.lastName.trim()}".lowercase() }

                if (distinctByName.isEmpty()) {
                    _uiState.value = RecurrentSearchUiState.NoResults
                } else {
                    _uiState.value = RecurrentSearchUiState.Success(distinctByName)
                }
            } catch (e: Exception) {
                _uiState.value = RecurrentSearchUiState.Error(
                    e.message ?: "Search failed"
                )
            }
        }
    }

    /**
     * Resolves the profile photo for a search result. Local file wins; if the
     * file is gone (purged) the photo is downloaded from the backend and cached.
     * Returns an absolute file path or null. Safe to call from the UI per card.
     */
    suspend fun resolvePhoto(person: Person): String? {
        photoCache[person.personId]?.let { return it }
        return photoMutex.withLock {
            // Re-check inside the lock in case a concurrent card resolved it.
            photoCache[person.personId]?.let { return it }
            val resolved = resolveVisitorPhoto(person.personId, person.profilePhotoPath)
            photoCache[person.personId] = resolved
            resolved
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
