package com.eflglobal.visitorsapp.domain.usecase.person

import com.eflglobal.visitorsapp.domain.model.Person
import com.eflglobal.visitorsapp.domain.repository.PersonRepository

/**
 * Use Case para buscar personas por nombre, documento o empresa.
 */
class SearchPersonsUseCase(
    private val personRepository: PersonRepository
) {
    suspend operator fun invoke(query: String): List<Person> {
        if (query.length < 3) {
            return emptyList()
        }
        return personRepository.searchPersons(query)
    }
}

