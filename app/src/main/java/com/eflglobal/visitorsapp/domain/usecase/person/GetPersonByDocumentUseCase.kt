package com.eflglobal.visitorsapp.domain.usecase.person

import com.eflglobal.visitorsapp.domain.model.Person
import com.eflglobal.visitorsapp.domain.repository.PersonRepository

/**
 * Use Case para obtener una persona por su número de documento.
 */
class GetPersonByDocumentUseCase(
    private val personRepository: PersonRepository
) {
    suspend operator fun invoke(documentNumber: String): Person? {
        return personRepository.getPersonByDocumentNumber(documentNumber)
    }
}

