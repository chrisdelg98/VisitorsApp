package com.eflglobal.visitorsapp.data.repository

import com.eflglobal.visitorsapp.data.local.dao.VisitDao
import com.eflglobal.visitorsapp.data.local.mapper.toDomain
import com.eflglobal.visitorsapp.data.local.mapper.toEntity
import com.eflglobal.visitorsapp.domain.model.Visit
import com.eflglobal.visitorsapp.domain.repository.VisitRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar

/**
 * Implementación del repositorio de visitas.
 *
 * Maneja toda la lógica de negocio relacionada con el registro
 * de entradas y salidas de visitantes.
 */
class VisitRepositoryImpl(
    private val visitDao: VisitDao
) : VisitRepository {

    override suspend fun createVisit(visit: Visit): Result<Visit> {
        return try {
            visitDao.insertVisit(visit.toEntity())
            Result.success(visit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getVisitById(visitId: String): Visit? {
        return try {
            visitDao.getVisitById(visitId)?.toDomain()
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun getVisitByQRCode(qrCode: String): Visit? {
        return try {
            visitDao.getVisitByQRCode(qrCode)?.toDomain()
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun getVisitsByPersonId(personId: String): List<Visit> {
        return try {
            visitDao.getVisitsByPersonId(personId).map { it.toDomain() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getLastVisitByPersonId(personId: String): Visit? {
        return try {
            visitDao.getLastVisitByPersonId(personId)?.toDomain()
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun getActiveVisits(): List<Visit> {
        return try {
            visitDao.getActiveVisits().map { it.toDomain() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun getActiveVisitsFlow(): Flow<List<Visit>> {
        return visitDao.getActiveVisitsFlow().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun searchActiveVisits(query: String): List<Visit> {
        return try {
            if (query.isBlank()) {
                emptyList()
            } else {
                visitDao.searchActiveVisits(query).map { it.toDomain() }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getTodayVisits(): List<Visit> {
        return try {
            val calendar = Calendar.getInstance()

            // Inicio del día (00:00:00)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val startOfDay = calendar.timeInMillis

            // Fin del día (23:59:59)
            calendar.set(Calendar.HOUR_OF_DAY, 23)
            calendar.set(Calendar.MINUTE, 59)
            calendar.set(Calendar.SECOND, 59)
            calendar.set(Calendar.MILLISECOND, 999)
            val endOfDay = calendar.timeInMillis

            visitDao.getTodayVisits(startOfDay, endOfDay).map { it.toDomain() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getVisitsByDateRange(startDate: Long, endDate: Long): List<Visit> {
        return try {
            visitDao.getVisitsByDateRange(startDate, endDate).map { it.toDomain() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun endVisit(visitId: String, exitDate: Long): Result<Unit> {
        return try {
            visitDao.updateExitDate(visitId, exitDate)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun endVisitByQRCode(qrCode: String, exitDate: Long): Result<Unit> {
        return try {
            val visit = visitDao.getVisitByQRCode(qrCode)
                ?: return Result.failure(Exception("Visit not found"))

            // Verificar que la visita sea del día actual
            if (!isFromToday(visit.entryDate)) {
                return Result.failure(Exception("Visit is not from today"))
            }

            // Verificar que la visita no haya terminado ya
            if (visit.exitDate != null) {
                return Result.failure(Exception("Visit already ended"))
            }

            visitDao.updateExitDate(visit.visitId, exitDate)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateVisit(visit: Visit): Result<Unit> {
        return try {
            visitDao.updateVisit(visit.toEntity())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getActiveVisitsCount(): Int {
        return try {
            visitDao.getActiveVisitsCount()
        } catch (e: Exception) {
            0
        }
    }

    override fun generateQRCode(visitId: String): String {
        // Formato: VISIT-{visitId}-{timestamp}
        // Esto hace que cada QR sea único y trazable
        val timestamp = System.currentTimeMillis()
        return "VISIT-$visitId-$timestamp"
    }

    /**
     * Verifica si una fecha pertenece al día actual.
     */
    private fun isFromToday(timestamp: Long): Boolean {
        val calendar = Calendar.getInstance()
        val today = calendar.get(Calendar.DAY_OF_YEAR)

        calendar.timeInMillis = timestamp
        val visitDay = calendar.get(Calendar.DAY_OF_YEAR)

        return today == visitDay
    }
}

