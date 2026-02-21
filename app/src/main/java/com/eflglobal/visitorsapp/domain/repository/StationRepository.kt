package com.eflglobal.visitorsapp.domain.repository

import com.eflglobal.visitorsapp.domain.model.Station
import kotlinx.coroutines.flow.Flow

/**
 * Repositorio para el manejo de estaciones.
 * Define las operaciones de negocio relacionadas con las estaciones/tablets.
 */
interface StationRepository {

    /**
     * Obtiene la estación activa actual.
     * Solo puede haber una estación activa a la vez.
     */
    suspend fun getActiveStation(): Station?

    /**
     * Obtiene la estación activa como Flow para observar cambios.
     */
    fun getActiveStationFlow(): Flow<Station?>

    /**
     * Valida un PIN y retorna true si es correcto.
     * Para fase inicial, solo acepta "00000000".
     */
    suspend fun validatePin(pin: String): Boolean

    /**
     * Crea una nueva estación y la activa.
     * Desactiva cualquier estación previa.
     */
    suspend fun createAndActivateStation(
        pin: String,
        stationName: String,
        countryCode: String
    ): Result<Station>

    /**
     * Verifica si ya existe una estación activa.
     */
    suspend fun hasActiveStation(): Boolean

    /**
     * Obtiene el ID de la estación activa (para asociar visitas).
     */
    suspend fun getActiveStationId(): String?
}

