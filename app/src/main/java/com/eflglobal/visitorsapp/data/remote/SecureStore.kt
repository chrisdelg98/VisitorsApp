package com.eflglobal.visitorsapp.data.remote

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Encrypted, on-device store for the API credentials and per-device settings
 * that must never be visible in plain-text on disk.
 *
 * Backed by [EncryptedSharedPreferences] (AES256-SIV for keys, AES256-GCM for
 * values), so a rooted device or `adb pull` of the app's data folder cannot
 * read these values without the device's master key.
 *
 * Stored entries:
 *  - [KEY_API_KEY]            – the `X-API-Key` issued by the backend on station setup.
 *  - [KEY_STATION_ID]         – the remote UUID of this station (for reference / debugging).
 *  - [KEY_API_BASE_OVERRIDE]  – optional URL override set from the hidden "tech settings"
 *                                screen, used to point a deployed APK to a different
 *                                backend without recompiling.
 */
object SecureStore {

    private const val PREFS_FILE = "visitors_secure_prefs"

    private const val KEY_API_KEY = "api_key"
    private const val KEY_STATION_ID = "station_id"
    private const val KEY_API_BASE_OVERRIDE = "api_base_override"

    @Volatile
    private var cached: SharedPreferences? = null

    private fun prefs(ctx: Context): SharedPreferences {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val masterKey = MasterKey.Builder(ctx.applicationContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val sp = EncryptedSharedPreferences.create(
                ctx.applicationContext,
                PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            cached = sp
            return sp
        }
    }

    // ── Station credentials ───────────────────────────────────────────────────

    /** Persists the credentials returned by `POST /v1/auth/validate-station`. */
    fun saveStation(ctx: Context, apiKey: String, stationId: String) {
        prefs(ctx).edit()
            .putString(KEY_API_KEY, apiKey)
            .putString(KEY_STATION_ID, stationId)
            .apply()
    }

    fun apiKey(ctx: Context): String? = prefs(ctx).getString(KEY_API_KEY, null)

    fun stationId(ctx: Context): String? = prefs(ctx).getString(KEY_STATION_ID, null)

    /** True once the device has been activated against the backend. */
    fun hasStation(ctx: Context): Boolean = !apiKey(ctx).isNullOrBlank()

    /** Wipes the API key (e.g. on `API_KEY_INVALID` or admin "Reconfigure station"). */
    fun clearStation(ctx: Context) {
        prefs(ctx).edit()
            .remove(KEY_API_KEY)
            .remove(KEY_STATION_ID)
            .apply()
    }

    // ── Base URL override (hidden tech-settings screen) ───────────────────────

    fun apiBaseOverride(ctx: Context): String? =
        prefs(ctx).getString(KEY_API_BASE_OVERRIDE, null)?.takeIf { it.isNotBlank() }

    fun setApiBaseOverride(ctx: Context, url: String?) {
        prefs(ctx).edit().apply {
            if (url.isNullOrBlank()) remove(KEY_API_BASE_OVERRIDE)
            else putString(KEY_API_BASE_OVERRIDE, url.trim().ensureTrailingSlash())
        }.apply()
    }

    /** Full nuke (factory-reset of the remote layer). */
    fun clearAll(ctx: Context) {
        prefs(ctx).edit().clear().apply()
    }

    private fun String.ensureTrailingSlash(): String = if (endsWith("/")) this else "$this/"
}

