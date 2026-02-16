package com.eflglobal.visitorsapp.data.repository

import com.eflglobal.visitorsapp.data.local.dao.PersonDao
import com.eflglobal.visitorsapp.data.local.entity.PersonEntity
import com.eflglobal.visitorsapp.domain.repository.PersonRepository
import kotlinx.coroutines.flow.Flow

/**
 * Implementación del repositorio de personas/visitantes.
 *
 * Maneja toda la lógica de negocio relacionada con visitantes,
 * incluyendo CRUD operations y búsquedas.
 */
class PersonRepositoryImpl(
    private val personDao: PersonDao
) : PersonRepository {

    override suspend fun createPerson(person: PersonEntity): Result<PersonEntity> {
        return try {
            // Verificar que no exista ya una persona con el mismo documento
            val existing = personDao.getPersonByDocumentNumber(person.documentNumber)
            if (existing != null) {
                return Result.failure(Exception("Person with document ${person.documentNumber} already exists"))
            }

            personDao.insertPerson(person)
            Result.success(person)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getPersonById(personId: String): PersonEntity? {
        return try {
            personDao.getPersonById(personId)
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun getPersonByDocumentNumber(documentNumber: String): PersonEntity? {
        return try {
            personDao.getPersonByDocumentNumber(documentNumber)
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun searchPersons(query: String): List<PersonEntity> {
        return try {
            if (query.isBlank()) {
                emptyList()
            } else {
                personDao.searchPersons(query)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun searchPersonsFlow(query: String): Flow<List<PersonEntity>> {
        return personDao.searchPersonsFlow(query)
    }

    override suspend fun getAllPersons(): List<PersonEntity> {
        return try {
            personDao.getAllPersons()
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getRecentPersons(limit: Int): List<PersonEntity> {
        return try {
            personDao.getRecentPersons(limit)
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun updatePerson(person: PersonEntity): Result<Unit> {
        return try {
            personDao.updatePerson(person)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deletePerson(personId: String): Result<Unit> {
        return try {
            personDao.deletePersonById(personId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun existsByDocumentNumber(documentNumber: String): Boolean {
        return try {
            personDao.getPersonByDocumentNumber(documentNumber) != null
        } catch (e: Exception) {
            false
        }
    }
}

