package com.eflglobal.visitorsapp.data.repository

import android.content.Context
import android.util.Log
import com.eflglobal.visitorsapp.data.local.AppDatabase
import com.eflglobal.visitorsapp.data.local.TemplateCatalogPrefs
import com.eflglobal.visitorsapp.data.local.mapper.TemplateMapper
import com.eflglobal.visitorsapp.data.remote.ApiClient
import com.eflglobal.visitorsapp.data.remote.SecureStore
import com.eflglobal.visitorsapp.domain.model.DocumentTemplate

/**
 * ═══════════════════════════════════════════════════════════════════════════════
 * TemplateRepository — single source of truth for the OCR template catalog.
 *
 *  - [sync] performs a CONDITIONAL `GET /v1/ocr/templates` (`If-None-Match`):
 *      · 304 → keep the local copy untouched.
 *      · 200 → replace the whole local catalog and store the new `catalog_version`.
 *    Reuses the existing Retrofit/OkHttp stack (auth `X-API-Key` interceptor,
 *    base URL, timeouts). Network / auth failures are swallowed — the pipeline
 *    keeps working with whatever is cached (offline-first).
 *
 *  - [getTemplates] returns the cached catalog as domain models, memoised in
 *    memory so the OCR pipeline (hot path) never blocks on disk/JSON per scan.
 *    The cache is invalidated after every successful [sync].
 * ═══════════════════════════════════════════════════════════════════════════════
 */
class TemplateRepository private constructor(
    private val appContext: Context
) {

    private val tag = "TemplateRepo"
    private val dao by lazy { AppDatabase.getInstance(appContext).documentTemplateDao() }

    @Volatile private var cache: List<DocumentTemplate>? = null

    // ── Sync ───────────────────────────────────────────────────────────────────

    sealed class SyncResult {
        data class Updated(val count: Int, val catalogVersion: String?) : SyncResult()
        object NotModified : SyncResult()
        object Skipped : SyncResult()            // no station / not activated yet
        data class Failed(val reason: String) : SyncResult()
    }

    suspend fun sync(): SyncResult {
        if (!SecureStore.hasStation(appContext)) {
            Log.d(tag, "sync skipped — station not activated")
            return SyncResult.Skipped
        }

        val api = ApiClient.get(appContext)
        val etag = TemplateCatalogPrefs.catalogVersion(appContext)

        return try {
            val response = api.getOcrTemplates(etag)

            when {
                response.code() == 304 -> {
                    Log.d(tag, "catalog unchanged (304)")
                    SyncResult.NotModified
                }
                response.isSuccessful -> {
                    val body = response.body()
                    if (body == null || !body.success) {
                        Log.w(tag, "catalog 2xx but empty/unsuccessful body")
                        return SyncResult.Failed("empty body")
                    }
                    val entities = body.data.map { TemplateMapper.toEntity(it) }
                    dao.replaceAll(entities)
                    // Prefer the server-sent catalog_version; fall back to the
                    // ETag header if the body omitted it.
                    val newVersion = body.catalogVersion
                        ?: response.headers()["ETag"]
                    TemplateCatalogPrefs.setCatalogVersion(appContext, newVersion)
                    cache = null // invalidate — reloaded lazily on next read
                    Log.i(tag, "catalog replaced: ${entities.size} templates, version=$newVersion")
                    SyncResult.Updated(entities.size, newVersion)
                }
                else -> {
                    Log.w(tag, "catalog sync HTTP ${response.code()}")
                    SyncResult.Failed("http ${response.code()}")
                }
            }
        } catch (e: Exception) {
            // Offline-first: never propagate — the pipeline degrades gracefully.
            Log.w(tag, "catalog sync failed: ${e.message}")
            SyncResult.Failed(e.message ?: "unknown")
        }
    }

    // ── Read ───────────────────────────────────────────────────────────────────

    /** All cached templates as domain models (memoised). Empty when never synced. */
    suspend fun getTemplates(): List<DocumentTemplate> {
        cache?.let { return it }
        return try {
            val loaded = dao.getAll().map { TemplateMapper.toDomain(it) }
            cache = loaded
            loaded
        } catch (e: Exception) {
            Log.w(tag, "getTemplates failed: ${e.message}")
            emptyList()
        }
    }

    suspend fun isEmpty(): Boolean = getTemplates().isEmpty()

    companion object {
        @Volatile private var INSTANCE: TemplateRepository? = null

        fun get(context: Context): TemplateRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: TemplateRepository(context.applicationContext).also { INSTANCE = it }
            }
    }
}
