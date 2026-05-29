package com.eflglobal.visitorsapp.data.repository

import android.content.Context
import com.eflglobal.visitorsapp.data.local.dao.PersonDao
import com.eflglobal.visitorsapp.data.local.mapper.toDomain
import com.eflglobal.visitorsapp.data.local.mapper.toEntity
import com.eflglobal.visitorsapp.data.remote.ApiClient
import com.eflglobal.visitorsapp.data.remote.dto.VisitorDto
import com.eflglobal.visitorsapp.data.remote.safeCall
import com.eflglobal.visitorsapp.domain.model.Person
import com.eflglobal.visitorsapp.domain.repository.PersonRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar

/**
 * Implementación del repositorio de personas/visitantes.
 *
 * Maneja toda la lógica de negocio relacionada con visitantes,
 * incluyendo CRUD operations y búsquedas.
 */
class PersonRepositoryImpl(
    private val appContext: Context,
    private val personDao: PersonDao
) : PersonRepository {

    private val api get() = ApiClient.get(appContext)

    override suspend fun createPerson(person: Person): Result<Person> {
        return try {
            // Only check for duplicates when a document number is present
            val docNum = person.documentNumber
            if (!docNum.isNullOrBlank()) {
                val existing = personDao.getPersonByDocumentNumber(docNum)
                if (existing != null) {
                    return Result.failure(
                        Exception("Person with document $docNum already exists")
                    )
                }
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
                // Calculate timestamp for 3 months ago
                val threeMonthsAgo = Calendar.getInstance().apply {
                    add(Calendar.MONTH, -3)
                }.timeInMillis

                personDao.searchPersons(query, threeMonthsAgo).map { it.toDomain() }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun searchVisitorsRemote(query: String): Result<List<Person>> {
        if (query.isBlank()) return Result.success(emptyList())
        return try {
            val remote = safeCall { api.searchVisitors(query) }
            // Prefer the local record when we still have it (richer data:
            // cached photo path, locally-edited contact info). Otherwise build
            // a lightweight Person from the remote DTO.
            val persons = remote.map { dto ->
                resolveLocalPerson(dto) ?: dto.toDomainPerson()
            }
            Result.success(persons)
        } catch (e: Exception) {
            // ApiException (network / 4xx / 5xx) or anything unexpected —
            // surface as failure so the ViewModel can fall back to local.
            Result.failure(e)
        }
    }

    /** Returns the locally-stored Person matching this DTO, or null. */
    private suspend fun resolveLocalPerson(dto: VisitorDto): Person? {
        // personId mirrors the backend visitor id (kept 1-to-1 across the app).
        getPersonById(dto.id)?.let { return it }
        return dto.documentNumber
            ?.takeIf { it.isNotBlank() }
            ?.let { getPersonByDocumentNumber(it) }
    }

    override fun searchPersonsFlow(query: String): Flow<List<Person>> {
        // Calculate timestamp for 3 months ago
        val threeMonthsAgo = Calendar.getInstance().apply {
            add(Calendar.MONTH, -3)
        }.timeInMillis

        return personDao.searchPersonsFlow(query, threeMonthsAgo).map { entities ->
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
        if (documentNumber.isBlank()) return false
        return try {
            personDao.getPersonByDocumentNumber(documentNumber) != null
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * Lightweight mapping for visitors found via the backend search that are no
 * longer present locally. `profilePhotoPath` is intentionally null — the photo
 * is resolved lazily (and cached) by ResolveVisitorPhotoUseCase. `personId`
 * mirrors the backend visitor id so the rest of the app stays 1-to-1.
 */
private fun VisitorDto.toDomainPerson(): Person = Person(
    personId          = id,
    firstName         = firstName,
    lastName          = lastName,
    documentNumber    = documentNumber,
    documentType      = documentType,
    profilePhotoPath  = null,
    documentFrontPath = null,
    documentBackPath  = null,
    company           = company,
    email             = email ?: "",
    phoneNumber       = phone ?: "",
    createdAt         = System.currentTimeMillis(),
    isSynced          = true,
    lastSyncAt        = System.currentTimeMillis()
)

