package com.eflglobal.visitorsapp.data.local

import android.content.Context
import android.content.SharedPreferences

/**
 * Tiny persistence for the OCR template catalog's `catalog_version` (used as the
 * `If-None-Match` ETag on conditional catalog syncs) and the last successful
 * sync time.
 *
 * The catalog version is not secret, so plain [SharedPreferences] is fine (no
 * need for the encrypted [com.eflglobal.visitorsapp.data.remote.SecureStore]).
 */
object TemplateCatalogPrefs {

    private const val PREFS_FILE = "ocr_template_catalog_prefs"
    private const val KEY_CATALOG_VERSION = "catalog_version"
    private const val KEY_LAST_SYNC_AT = "last_sync_at"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    /** The stored ETag to send as `If-None-Match`; null before the first sync. */
    fun catalogVersion(ctx: Context): String? =
        prefs(ctx).getString(KEY_CATALOG_VERSION, null)?.takeIf { it.isNotBlank() }

    fun setCatalogVersion(ctx: Context, version: String?) {
        prefs(ctx).edit()
            .putString(KEY_CATALOG_VERSION, version)
            .putLong(KEY_LAST_SYNC_AT, System.currentTimeMillis())
            .apply()
    }

    fun lastSyncAt(ctx: Context): Long = prefs(ctx).getLong(KEY_LAST_SYNC_AT, 0L)

    fun clear(ctx: Context) {
        prefs(ctx).edit().clear().apply()
    }
}
