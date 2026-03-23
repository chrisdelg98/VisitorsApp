package com.eflglobal.visitorsapp.core.printing

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.printerDataStore by preferencesDataStore(name = "printer_config")

/**
 * Persists and retrieves printer connection settings using DataStore.
 * Singleton — call via companion object functions.
 */
object PrinterConfigRepository {

    private val KEY_BRAND           = stringPreferencesKey("printer_brand")
    private val KEY_CONNECTION_TYPE = stringPreferencesKey("connection_type")
    private val KEY_NETWORK_HOST    = stringPreferencesKey("network_host")
    private val KEY_NETWORK_PORT    = intPreferencesKey("network_port")
    private val KEY_BROTHER_MODEL   = stringPreferencesKey("brother_model")
    private val KEY_PRINTER_ID      = stringPreferencesKey("printer_identifier")
    private val KEY_PRINTER_DISPLAY = stringPreferencesKey("printer_display_name")
    private val KEY_LAST_DISCOVERY  = longPreferencesKey("last_discovery_timestamp")
    private val KEY_AUTO_DISCOVERY  = booleanPreferencesKey("auto_discovery_enabled")

    /** Returns a Flow that emits the current config (and subsequent updates). */
    fun getConfig(context: Context): Flow<PrinterConfig> =
        context.printerDataStore.data.map { prefs ->
            PrinterConfig(
                brand = runCatching {
                    PrinterConfig.PrinterBrand.valueOf(
                        prefs[KEY_BRAND] ?: PrinterConfig.PrinterBrand.NONE.name
                    )
                }.getOrDefault(PrinterConfig.PrinterBrand.NONE),
                connectionType = runCatching {
                    PrinterConfig.ConnectionType.valueOf(
                        prefs[KEY_CONNECTION_TYPE] ?: PrinterConfig.ConnectionType.USB.name
                    )
                }.getOrDefault(PrinterConfig.ConnectionType.USB),
                networkHost          = prefs[KEY_NETWORK_HOST]?.ifBlank { null },
                networkPort          = prefs[KEY_NETWORK_PORT] ?: PrinterConfig.DEFAULT_PORT,
                brotherModel         = prefs[KEY_BROTHER_MODEL] ?: PrinterConfig.BrotherModel.QL_810W.name,
                printerIdentifier    = prefs[KEY_PRINTER_ID] ?: "",
                printerDisplayName   = prefs[KEY_PRINTER_DISPLAY] ?: "",
                lastDiscoveryTimestamp = prefs[KEY_LAST_DISCOVERY] ?: 0L
            )
        }

    /** Saves the config. Call from a coroutine scope. */
    suspend fun saveConfig(context: Context, config: PrinterConfig) {
        context.printerDataStore.edit { prefs ->
            prefs[KEY_BRAND]           = config.brand.name
            prefs[KEY_CONNECTION_TYPE] = config.connectionType.name
            prefs[KEY_NETWORK_HOST]    = config.networkHost ?: ""
            prefs[KEY_NETWORK_PORT]    = config.networkPort
            prefs[KEY_BROTHER_MODEL]   = config.brotherModel
            prefs[KEY_PRINTER_ID]      = config.printerIdentifier
            prefs[KEY_PRINTER_DISPLAY] = config.printerDisplayName
            prefs[KEY_LAST_DISCOVERY]  = config.lastDiscoveryTimestamp
        }
    }

    /** Returns whether auto-discovery is enabled. */
    fun isAutoDiscoveryEnabled(context: Context): Flow<Boolean> =
        context.printerDataStore.data.map { prefs ->
            prefs[KEY_AUTO_DISCOVERY] ?: true   // enabled by default
        }

    /** Saves auto-discovery preference. */
    suspend fun setAutoDiscoveryEnabled(context: Context, enabled: Boolean) {
        context.printerDataStore.edit { prefs ->
            prefs[KEY_AUTO_DISCOVERY] = enabled
        }
    }

    /** One-shot read of the current config. */
    suspend fun getConfigOnce(context: Context): PrinterConfig =
        getConfig(context).first()
}
