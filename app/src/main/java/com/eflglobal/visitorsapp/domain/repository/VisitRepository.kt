package com.eflglobal.visitorsapp.domain.repository

import com.eflglobal.visitorsapp.domain.model.Visit
import kotlinx.coroutines.flow.Flow

/**
 * Repositorio para el manejo de visitas.
 * Define las operaciones de negocio relacionadas con el registro de entradas/salidas.
 */
interface VisitRepository {

    /**
     * Crea una nueva visita.
     */
    suspend fun createVisit(visit: Visit): Result<Visit>

    /**
     * Obtiene una visita por su ID.
     */
    suspend fun getVisitById(visitId: String): Visit?

    /**
     * Obtiene una visita por el código QR.
     */
    suspend fun getVisitByQRCode(qrCode: String): Visit?

    /**
     * Obtiene todas las visitas de una persona.
     */
    suspend fun getVisitsByPersonId(personId: String): List<Visit>

    /**
     * Obtiene la última visita de una persona.
     */
    suspend fun getLastVisitByPersonId(personId: String): Visit?

    /**
     * Obtiene todas las visitas activas (sin fecha de salida).
     */
    suspend fun getActiveVisits(): List<Visit>

    /**
     * Obtiene visitas activas como Flow (observable).
     */
    fun getActiveVisitsFlow(): Flow<List<Visit>>

    /**
     * Busca en visitas activas por nombre de visitante o persona visitada.
     */
    suspend fun searchActiveVisits(query: String): List<Visit>

    /**
     * Obtiene las visitas del día actual.
     */
    suspend fun getTodayVisits(): List<Visit>

    /**
     * Obtiene visitas en un rango de fechas.
     */
    suspend fun getVisitsByDateRange(startDate: Long, endDate: Long): List<Visit>

    /**
     * Registra la salida de una visita.
     */
    suspend fun endVisit(visitId: String, exitDate: Long): Result<Unit>

    /**
     * Registra la salida de una visita usando el QR code.
     */
    suspend fun endVisitByQRCode(qrCode: String, exitDate: Long): Result<Unit>

    /**
     * Actualiza una visita.
     */
    suspend fun updateVisit(visit: Visit): Result<Unit>

    /**
     * Obtiene el conteo de visitas activas.
     */
    suspend fun getActiveVisitsCount(): Int

    /**
     * Genera un código QR único para una visita.
     */
    fun generateQRCode(visitId: String): String
}

