package com.eflglobal.visitorsapp.domain.repository

import com.eflglobal.visitorsapp.domain.model.Person
import kotlinx.coroutines.flow.Flow

/**
 * Repositorio para el manejo de personas/visitantes.
 * Define las operaciones de negocio relacionadas con visitantes.
 */
interface PersonRepository {

    /**
     * Crea una nueva persona en la base de datos.
     */
    suspend fun createPerson(person: Person): Result<Person>

    /**
     * Obtiene una persona por su ID.
     */
    suspend fun getPersonById(personId: String): Person?

    /**
     * Obtiene una persona por su número de documento.
     */
    suspend fun getPersonByDocumentNumber(documentNumber: String): Person?

    /**
     * Busca personas por nombre, documento o empresa.
     */
    suspend fun searchPersons(query: String): List<Person>

    /**
     * Busca personas como Flow (observable).
     */
    fun searchPersonsFlow(query: String): Flow<List<Person>>

    /**
     * Obtiene todas las personas registradas.
     */
    suspend fun getAllPersons(): List<Person>

    /**
     * Obtiene las personas recientes (últimas N).
     */
    suspend fun getRecentPersons(limit: Int): List<Person>

    /**
     * Actualiza los datos de una persona.
     */
    suspend fun updatePerson(person: Person): Result<Unit>

    /**
     * Elimina una persona.
     */
    suspend fun deletePerson(personId: String): Result<Unit>

    /**
     * Verifica si existe una persona con el documento dado.
     */
    suspend fun existsByDocumentNumber(documentNumber: String): Boolean
}

