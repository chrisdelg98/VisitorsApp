package com.eflglobal.visitorsapp.data.repository

import com.eflglobal.visitorsapp.data.local.dao.StationDao
import com.eflglobal.visitorsapp.data.local.entity.StationEntity
import com.eflglobal.visitorsapp.data.local.mapper.toDomain
import com.eflglobal.visitorsapp.domain.model.Station
import com.eflglobal.visitorsapp.domain.repository.StationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * Implementación del repositorio de estaciones.
 *
 * Maneja la lógica de negocio relacionada con estaciones/tablets,
 * incluyendo validación de PIN y activación.
 */
class StationRepositoryImpl(
    private val stationDao: StationDao
) : StationRepository {

    companion object {
        // PIN válido para fase de desarrollo (local only)
        private const val VALID_PIN = "00000000"

        // Configuración por defecto para la estación
        private const val DEFAULT_STATION_NAME = "EFL SV"
        private const val DEFAULT_COUNTRY_CODE = "SV"
    }

    override suspend fun getActiveStation(): Station? {
        return stationDao.getActiveStation()?.toDomain()
    }

    override fun getActiveStationFlow(): Flow<Station?> {
        return stationDao.getActiveStationFlow().map { entity ->
            entity?.toDomain()
        }
    }

    override suspend fun validatePin(pin: String): Boolean {
        // Validación local simple
        // En producción, esto se validaría contra un servidor
        return pin == VALID_PIN
    }

    override suspend fun createAndActivateStation(
        pin: String,
        stationName: String,
        countryCode: String
    ): Result<Station> {
        return try {
            // Validar PIN primero
            if (!validatePin(pin)) {
                return Result.failure(Exception("Invalid PIN"))
            }

            // Desactivar cualquier estación previa
            stationDao.deactivateAllStations()

            // Crear nueva estación
            val currentTime = System.currentTimeMillis()
            val newStation = StationEntity(
                stationId = UUID.randomUUID().toString(),
                pin = pin, // En producción, esto debería estar encriptado
                stationName = stationName,
                countryCode = countryCode,
                isActive = true,
                createdAt = currentTime,
                activatedAt = currentTime,
                isSynced = false,
                lastSyncAt = null
            )

            // Guardar en la base de datos
            stationDao.insertStation(newStation)

            Result.success(newStation.toDomain())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun hasActiveStation(): Boolean {
        return stationDao.getActiveStationCount() > 0
    }

    override suspend fun getActiveStationId(): String? {
        return getActiveStation()?.stationId
    }
}

