package com.eflglobal.visitorsapp.core.printing

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.printerDataStore by preferencesDataStore(name = "printer_config")

/**
 * Persists and retrieves printer connection settings using DataStore.
 * Singleton — call via companion object functions.
 */
object PrinterConfigRepository {

    private val KEY_CONNECTION_TYPE = stringPreferencesKey("connection_type")
    private val KEY_NETWORK_HOST    = stringPreferencesKey("network_host")
    private val KEY_NETWORK_PORT    = intPreferencesKey("network_port")

    /** Returns a Flow that emits the current config (and subsequent updates). */
    fun getConfig(context: Context): Flow<PrinterConfig> =
        context.printerDataStore.data.map { prefs ->
            PrinterConfig(
                connectionType = runCatching {
                    PrinterConfig.ConnectionType.valueOf(
                        prefs[KEY_CONNECTION_TYPE] ?: PrinterConfig.ConnectionType.USB.name
                    )
                }.getOrDefault(PrinterConfig.ConnectionType.USB),
                networkHost = prefs[KEY_NETWORK_HOST]?.ifBlank { null },
                networkPort = prefs[KEY_NETWORK_PORT] ?: PrinterConfig.DEFAULT_PORT
            )
        }

    /** Saves the config. Call from a coroutine scope. */
    suspend fun saveConfig(context: Context, config: PrinterConfig) {
        context.printerDataStore.edit { prefs ->
            prefs[KEY_CONNECTION_TYPE] = config.connectionType.name
            prefs[KEY_NETWORK_HOST]    = config.networkHost ?: ""
            prefs[KEY_NETWORK_PORT]    = config.networkPort
        }
    }
}

