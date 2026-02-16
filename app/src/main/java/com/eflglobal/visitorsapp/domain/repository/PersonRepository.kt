package com.eflglobal.visitorsapp.domain.repository

import com.eflglobal.visitorsapp.data.local.entity.PersonEntity
import kotlinx.coroutines.flow.Flow

/**
 * Repositorio para el manejo de personas/visitantes.
 * Define las operaciones de negocio relacionadas con visitantes.
 */
interface PersonRepository {

    /**
     * Crea una nueva persona en la base de datos.
     */
    suspend fun createPerson(person: PersonEntity): Result<PersonEntity>

    /**
     * Obtiene una persona por su ID.
     */
    suspend fun getPersonById(personId: String): PersonEntity?

    /**
     * Obtiene una persona por su número de documento.
     */
    suspend fun getPersonByDocumentNumber(documentNumber: String): PersonEntity?

    /**
     * Busca personas por nombre, documento o empresa.
     */
    suspend fun searchPersons(query: String): List<PersonEntity>

    /**
     * Busca personas como Flow (observable).
     */
    fun searchPersonsFlow(query: String): Flow<List<PersonEntity>>

    /**
     * Obtiene todas las personas registradas.
     */
    suspend fun getAllPersons(): List<PersonEntity>

    /**
     * Obtiene las personas recientes (últimas N).
     */
    suspend fun getRecentPersons(limit: Int): List<PersonEntity>

    /**
     * Actualiza los datos de una persona.
     */
    suspend fun updatePerson(person: PersonEntity): Result<Unit>

    /**
     * Elimina una persona.
     */
    suspend fun deletePerson(personId: String): Result<Unit>

    /**
     * Verifica si existe una persona con el documento dado.
     */
    suspend fun existsByDocumentNumber(documentNumber: String): Boolean
}

