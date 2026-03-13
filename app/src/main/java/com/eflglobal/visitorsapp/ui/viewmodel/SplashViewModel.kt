package com.eflglobal.visitorsapp.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.eflglobal.visitorsapp.core.DependencyProvider
import com.eflglobal.visitorsapp.domain.usecase.station.HasActiveStationUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel used during the Splash Screen.
 * It checks whether a station is already configured so that
 * the splash stays on screen until the result is known, preventing
 * any visible flash of StationSetupScreen when a station already exists.
 */
class SplashViewModel(
    private val hasActiveStationUseCase: HasActiveStationUseCase
) : ViewModel() {

    sealed class State {
        /** Check still running – keep splash on screen */
        object Loading : State()
        /** Station found → go to Home */
        object HasStation : State()
        /** No station → go to StationSetup */
        object NoStation : State()
    }

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state.asStateFlow()

    /** True while the result is unknown; used by splash.setKeepOnScreenCondition. */
    val isLoading: Boolean get() = _state.value is State.Loading

    init {
        checkStation()
    }

    private fun checkStation() {
        viewModelScope.launch {
            _state.value = if (hasActiveStationUseCase()) {
                State.HasStation
            } else {
                State.NoStation
            }
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val stationRepository = DependencyProvider.provideStationRepository(context)
            return SplashViewModel(HasActiveStationUseCase(stationRepository)) as T
        }
    }
}

