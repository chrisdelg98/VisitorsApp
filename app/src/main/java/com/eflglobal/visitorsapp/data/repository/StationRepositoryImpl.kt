package com.eflglobal.visitorsapp.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.eflglobal.visitorsapp.data.local.dao.StationDao
import com.eflglobal.visitorsapp.data.local.entity.StationEntity
import com.eflglobal.visitorsapp.data.local.mapper.toDomain
import com.eflglobal.visitorsapp.data.remote.ApiClient
import com.eflglobal.visitorsapp.data.remote.ApiErrorCode
import com.eflglobal.visitorsapp.data.remote.ApiException
import com.eflglobal.visitorsapp.data.remote.SecureStore
import com.eflglobal.visitorsapp.data.remote.dto.ValidateStationBody
import com.eflglobal.visitorsapp.data.remote.safeCall
import com.eflglobal.visitorsapp.domain.model.Station
import com.eflglobal.visitorsapp.domain.repository.StationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Implementación del repositorio de estaciones.
 *
 * Phase 2 — la activación deja de ser local. El PIN tipeado por el operador
 * viaja como `code` al endpoint `POST /v1/auth/station`; si el backend lo
 * acepta devuelve `station_id`, `name`, `code` y un `api_key` que se persiste
 * en [SecureStore] para autenticar TODAS las llamadas posteriores.
 */
class StationRepositoryImpl(
    private val appContext: Context,
    private val stationDao: StationDao
) : StationRepository {

    private val api get() = ApiClient.get(appContext)

    override suspend fun getActiveStation(): Station? =
        stationDao.getActiveStation()?.toDomain()

    override fun getActiveStationFlow(): Flow<Station?> =
        stationDao.getActiveStationFlow().map { it?.toDomain() }

    /**
     * No se valida más localmente — el backend es la única fuente de verdad.
     * Aceptamos cualquier cadena no vacía y diferimos al servidor.
     */
    override suspend fun validatePin(pin: String): Boolean = pin.isNotBlank()

    override suspend fun createAndActivateStation(
        pin: String,
        stationName: String,
        countryCode: String
    ): Result<Station> {
        if (pin.isBlank()) {
            return Result.failure(IllegalArgumentException("PIN cannot be empty"))
        }

        return try {
            // 1. Backend activation. safeCall traduce cualquier fallo HTTP / red
            //    en un ApiException con `code` tipado.
            val remote = safeCall {
                api.validateStation(
                    ValidateStationBody(
                        pin             = pin,
                        deviceImei      = readImei(),
                        deviceAndroidId = readAndroidId(),
                        deviceModel     = "${Build.MANUFACTURER} ${Build.MODEL}",
                        deviceIp        = readLocalIp()
                    )
                )
            }

            // 2. Persistir credenciales antes de tocar Room — si Room falla
            //    no queremos quedarnos con un api_key huérfano más adelante.
            SecureStore.saveStation(appContext, remote.apiKey, remote.station.id)

            // 3. Reflejar localmente con los datos reales devueltos por el backend.
            stationDao.deactivateAllStations()
            val now = System.currentTimeMillis()
            val entity = StationEntity(
                stationId   = remote.station.id,
                pin         = pin, // referencia para la UI; el secreto real es api_key.
                stationName = remote.station.name,
                countryCode = countryCode,
                isActive    = true,
                createdAt   = now,
                activatedAt = now,
                isSynced    = true,
                lastSyncAt  = now
            )
            stationDao.insertStation(entity)

            Result.success(entity.toDomain())
        } catch (e: ApiException) {
            val msg = when (e.code) {
                ApiErrorCode.STATION_INVALID,
                ApiErrorCode.API_KEY_INVALID          -> "Invalid station code"
                ApiErrorCode.STATION_ALREADY_REGISTERED -> "Contacta al administrador"
                ApiErrorCode.NETWORK_UNAVAILABLE      -> "No connection to the server"
                ApiErrorCode.RATE_LIMIT_EXCEEDED      -> "Too many attempts. Wait 15 minutes and try again"
                else                                  -> e.message ?: "Activation failed"
            }
            Result.failure(Exception(msg, e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun hasActiveStation(): Boolean =
        stationDao.getActiveStationCount() > 0

    override suspend fun getActiveStationId(): String? = getActiveStation()?.stationId

    override suspend fun deactivateCurrentStation(): Result<Unit> = try {
        stationDao.deactivateAllStations()
        // Wipe credenciales remotas también — si no, una próxima llamada
        // viajaría con un api_key huérfano.
        SecureStore.clearStation(appContext)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    // ── Device info helpers ───────────────────────────────────────────────────

    private fun readAndroidId(): String? = try {
        Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
    } catch (_: Exception) { null }

    private fun readImei(): String? = try {
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_PHONE_STATE)
            == PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        ) {
            (appContext.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager)
                ?.getImei(0)
        } else null
    } catch (_: Exception) { null }

    @Suppress("DEPRECATION")
    private fun readLocalIp(): String? = try {
        val ip = (appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)
            ?.connectionInfo?.ipAddress ?: 0
        if (ip == 0) null
        else "${ip and 0xff}.${ip shr 8 and 0xff}.${ip shr 16 and 0xff}.${ip shr 24 and 0xff}"
    } catch (_: Exception) { null }
}
