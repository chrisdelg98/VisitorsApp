package com.eflglobal.visitorsapp.domain.repository

import com.eflglobal.visitorsapp.data.local.entity.VisitEntity
import kotlinx.coroutines.flow.Flow

/**
 * Repositorio para el manejo de visitas.
 * Define las operaciones de negocio relacionadas con el registro de entradas/salidas.
 */
interface VisitRepository {

    /**
     * Crea una nueva visita.
     */
    suspend fun createVisit(visit: VisitEntity): Result<VisitEntity>

    /**
     * Obtiene una visita por su ID.
     */
    suspend fun getVisitById(visitId: String): VisitEntity?

    /**
     * Obtiene una visita por el código QR.
     */
    suspend fun getVisitByQRCode(qrCode: String): VisitEntity?

    /**
     * Obtiene todas las visitas de una persona.
     */
    suspend fun getVisitsByPersonId(personId: String): List<VisitEntity>

    /**
     * Obtiene la última visita de una persona.
     */
    suspend fun getLastVisitByPersonId(personId: String): VisitEntity?

    /**
     * Obtiene todas las visitas activas (sin fecha de salida).
     */
    suspend fun getActiveVisits(): List<VisitEntity>

    /**
     * Obtiene visitas activas como Flow (observable).
     */
    fun getActiveVisitsFlow(): Flow<List<VisitEntity>>

    /**
     * Busca en visitas activas por nombre de visitante o persona visitada.
     */
    suspend fun searchActiveVisits(query: String): List<VisitEntity>

    /**
     * Obtiene las visitas del día actual.
     */
    suspend fun getTodayVisits(): List<VisitEntity>

    /**
     * Obtiene visitas en un rango de fechas.
     */
    suspend fun getVisitsByDateRange(startDate: Long, endDate: Long): List<VisitEntity>

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
    suspend fun updateVisit(visit: VisitEntity): Result<Unit>

    /**
     * Obtiene el conteo de visitas activas.
     */
    suspend fun getActiveVisitsCount(): Int

    /**
     * Genera un código QR único para una visita.
     */
    fun generateQRCode(visitId: String): String
}

