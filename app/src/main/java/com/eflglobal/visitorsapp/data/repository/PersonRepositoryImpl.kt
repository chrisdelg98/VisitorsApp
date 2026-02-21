package com.eflglobal.visitorsapp.data.repository

import com.eflglobal.visitorsapp.data.local.dao.PersonDao
import com.eflglobal.visitorsapp.data.local.mapper.toDomain
import com.eflglobal.visitorsapp.data.local.mapper.toEntity
import com.eflglobal.visitorsapp.domain.model.Person
import com.eflglobal.visitorsapp.domain.repository.PersonRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Implementación del repositorio de personas/visitantes.
 *
 * Maneja toda la lógica de negocio relacionada con visitantes,
 * incluyendo CRUD operations y búsquedas.
 */
class PersonRepositoryImpl(
    private val personDao: PersonDao
) : PersonRepository {

    override suspend fun createPerson(person: Person): Result<Person> {
        return try {
            // Verificar que no exista ya una persona con el mismo documento
            val existing = personDao.getPersonByDocumentNumber(person.documentNumber)
            if (existing != null) {
                return Result.failure(Exception("Person with document ${person.documentNumber} already exists"))
            }

            personDao.insertPerson(person.toEntity())
            Result.success(person)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getPersonById(personId: String): Person? {
        return try {
            personDao.getPersonById(personId)?.toDomain()
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun getPersonByDocumentNumber(documentNumber: String): Person? {
        return try {
            personDao.getPersonByDocumentNumber(documentNumber)?.toDomain()
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun searchPersons(query: String): List<Person> {
        return try {
            if (query.isBlank()) {
                emptyList()
            } else {
                personDao.searchPersons(query).map { it.toDomain() }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun searchPersonsFlow(query: String): Flow<List<Person>> {
        return personDao.searchPersonsFlow(query).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getAllPersons(): List<Person> {
        return try {
            personDao.getAllPersons().map { it.toDomain() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getRecentPersons(limit: Int): List<Person> {
        return try {
            personDao.getRecentPersons(limit).map { it.toDomain() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun updatePerson(person: Person): Result<Unit> {
        return try {
            personDao.updatePerson(person.toEntity())
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

