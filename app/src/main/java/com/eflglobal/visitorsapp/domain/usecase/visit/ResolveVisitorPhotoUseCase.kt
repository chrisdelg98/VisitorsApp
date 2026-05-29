package com.eflglobal.visitorsapp.domain.usecase.visit

import android.content.Context
import android.util.Log
import com.eflglobal.visitorsapp.data.remote.ApiClient
import com.eflglobal.visitorsapp.data.remote.safeCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Resolves the profile photo of a visitor, preferring local files and falling
 * back to the backend so faces still appear after the local visit (and its
 * `visits/{visitId}/profile.jpg`) has been purged.
 *
 * Resolution order:
 *  1. [knownLocalPath] exists on disk → use it (no network).
 *  2. Fresh entry in `photo_cache/{visitorId}.jpg` (mtime < 24 h) → use it.
 *  3. Online: `GET /v1/visitors/{id}/latest-visit` → download `personal_photo`
 *     into the cache → use it.
 *  4. Download failed but a *stale* cache file exists → use the stale copy
 *     (better a day-old face than none, e.g. offline).
 *  5. Nothing → null (UI shows the initial-letter avatar).
 *
 * The cache lives in a dedicated `photo_cache/` directory — kept out of
 * `visits/` so the PurgeWorker's orphan sweep never deletes it. PurgeWorker
 * applies its own retention to this folder.
 */
class ResolveVisitorPhotoUseCase(
    private val appContext: Context
) {
    private val api get() = ApiClient.get(appContext)

    suspend operator fun invoke(
        visitorId: String,
        knownLocalPath: String?
    ): String? = withContext(Dispatchers.IO) {
        // 1) Local file from the persons table / current session.
        if (!knownLocalPath.isNullOrBlank()) {
            val local = File(knownLocalPath)
            if (local.exists() && local.length() > 0L) return@withContext knownLocalPath
        }

        val cacheFile = File(cacheDir(), "$visitorId.jpg")
        val cacheUsable = cacheFile.exists() && cacheFile.length() > 0L

        // 2) Fresh cache.
        if (cacheUsable &&
            System.currentTimeMillis() - cacheFile.lastModified() < FRESH_TTL_MS
        ) {
            return@withContext cacheFile.absolutePath
        }

        // 3) Download the latest visit's personal photo.
        downloadLatestPhoto(visitorId, cacheFile)?.let { return@withContext it }

        // 4) Offline / download failed — fall back to a stale cached copy.
        if (cacheUsable) return@withContext cacheFile.absolutePath

        // 5) Give up — caller renders the initial-letter avatar.
        null
    }

    private suspend fun downloadLatestPhoto(visitorId: String, target: File): String? {
        return try {
            val visit = safeCall { api.latestVisit(visitorId) }
            val hasPhoto = visit.images?.any { it.type == PERSONAL_PHOTO } ?: false
            if (!hasPhoto) return null

            val body = api.downloadVisitImage(visit.id, PERSONAL_PHOTO)
            target.parentFile?.mkdirs()
            body.byteStream().use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            if (target.length() > 0L) target.absolutePath else null
        } catch (e: Exception) {
            Log.w(TAG, "Could not resolve remote photo for visitor $visitorId: ${e.message}")
            null
        }
    }

    private fun cacheDir(): File =
        File(appContext.filesDir, CACHE_DIR).apply { if (!exists()) mkdirs() }

    companion object {
        private const val TAG = "ResolveVisitorPhoto"
        private const val PERSONAL_PHOTO = "personal_photo"

        /** Dedicated cache dir, kept outside `visits/`. */
        const val CACHE_DIR = "photo_cache"

        /** Freshness window before we try to re-download. */
        private val FRESH_TTL_MS = TimeUnit.HOURS.toMillis(24)
    }
}
