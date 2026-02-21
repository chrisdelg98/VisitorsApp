package com.eflglobal.visitorsapp.domain.usecase.person

import com.eflglobal.visitorsapp.domain.model.Person
import com.eflglobal.visitorsapp.domain.repository.PersonRepository
import java.util.UUID

/**
 * Use Case para crear una nueva persona/visitante.
 */
class CreatePersonUseCase(
    private val personRepository: PersonRepository
) {
    suspend operator fun invoke(
        fullName: String,
        documentNumber: String,
        documentType: String,
        email: String,
        phoneNumber: String,
        company: String? = null,
        profilePhotoPath: String? = null,
        documentFrontPath: String? = null,
        documentBackPath: String? = null
    ): Result<Person> {
        val person = Person(
            personId = UUID.randomUUID().toString(),
            fullName = fullName,
            documentNumber = documentNumber,
            documentType = documentType,
            email = email,
            phoneNumber = phoneNumber,
            company = company,
            profilePhotoPath = profilePhotoPath,
            documentFrontPath = documentFrontPath,
            documentBackPath = documentBackPath,
            createdAt = System.currentTimeMillis(),
            isSynced = false,
            lastSyncAt = null
        )

        return personRepository.createPerson(person)
    }
}

