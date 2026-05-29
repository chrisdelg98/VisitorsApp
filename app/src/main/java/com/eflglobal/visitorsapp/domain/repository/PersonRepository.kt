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
     * Busca visitantes contra el servidor (`GET /v1/visitors/search`).
     *
     * El servidor es la fuente de verdad: a diferencia de [searchPersons]
     * (limitada a la caché local de ~10 días), esta búsqueda encuentra a
     * visitantes que ya fueron purgados localmente. Cuando un visitante ya
     * existe en local se devuelve el [Person] local (datos más ricos: foto
     * cacheada, contacto editado); el resto se construye desde el DTO remoto.
     *
     * Devuelve [Result.failure] ante fallo de red para que el llamador pueda
     * degradar a [searchPersons] (búsqueda local).
     */
    suspend fun searchVisitorsRemote(query: String): Result<List<Person>>

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

