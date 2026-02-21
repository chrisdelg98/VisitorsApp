package com.eflglobal.visitorsapp.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.eflglobal.visitorsapp.core.DependencyProvider
import com.eflglobal.visitorsapp.domain.usecase.person.CreatePersonUseCase
import com.eflglobal.visitorsapp.domain.usecase.person.GetPersonByDocumentUseCase
import com.eflglobal.visitorsapp.domain.usecase.person.SearchPersonsUseCase
import com.eflglobal.visitorsapp.domain.usecase.station.CreateStationUseCase
import com.eflglobal.visitorsapp.domain.usecase.station.HasActiveStationUseCase
import com.eflglobal.visitorsapp.domain.usecase.visit.CreateVisitUseCase
import com.eflglobal.visitorsapp.domain.usecase.visit.EndVisitByQRUseCase
import com.eflglobal.visitorsapp.domain.usecase.visit.SearchActiveVisitsUseCase

/**
 * Factory para crear ViewModels con sus dependencias.
 */
class ViewModelFactory(private val context: Context) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        // Obtener repositorios
        val personRepository = DependencyProvider.providePersonRepository(context)
        val visitRepository = DependencyProvider.provideVisitRepository(context)
        val stationRepository = DependencyProvider.provideStationRepository(context)

        return when {
            modelClass.isAssignableFrom(StationSetupViewModel::class.java) -> {
                StationSetupViewModel(
                    hasActiveStationUseCase = HasActiveStationUseCase(stationRepository),
                    createStationUseCase = CreateStationUseCase(stationRepository)
                ) as T
            }

            modelClass.isAssignableFrom(NewVisitViewModel::class.java) -> {
                NewVisitViewModel(
                    createPersonUseCase = CreatePersonUseCase(personRepository),
                    getPersonByDocumentUseCase = GetPersonByDocumentUseCase(personRepository),
                    createVisitUseCase = CreateVisitUseCase(visitRepository, stationRepository)
                ) as T
            }

            modelClass.isAssignableFrom(RecurrentSearchViewModel::class.java) -> {
                RecurrentSearchViewModel(
                    searchPersonsUseCase = SearchPersonsUseCase(personRepository)
                ) as T
            }

            modelClass.isAssignableFrom(RecurrentVisitViewModel::class.java) -> {
                RecurrentVisitViewModel(
                    createVisitUseCase = CreateVisitUseCase(visitRepository, stationRepository)
                ) as T
            }

            modelClass.isAssignableFrom(EndVisitViewModel::class.java) -> {
                EndVisitViewModel(
                    endVisitByQRUseCase = EndVisitByQRUseCase(visitRepository),
                    searchActiveVisitsUseCase = SearchActiveVisitsUseCase(visitRepository),
                    personRepository = personRepository
                ) as T
            }

            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}


