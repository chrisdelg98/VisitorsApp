package com.eflglobal.visitorsapp.data.sync

import android.content.Context
import android.util.Log
import com.eflglobal.visitorsapp.data.remote.ApiClient
import com.eflglobal.visitorsapp.data.remote.ApiException
import com.eflglobal.visitorsapp.data.remote.dto.VisitDto
import com.eflglobal.visitorsapp.data.remote.safeCall
import okhttp3.ResponseBody
import java.io.File

/**
 * Cross-station visit fetcher used by the Continue Visit flow (Phase 6).
 *
 * When the operator scans a QR whose visit does not exist locally we hit
 * `GET /v1/visits/{id}` against the backend (any active station of the same
 * tenant is allowed to read). On success the helper also caches the three
 * visit images on disk so the confirmation modal can render them without
 * waiting for a second roundtrip.
 *
 * Every method is best-effort and never throws — callers receive `null` /
 * empty results so the UI can degrade gracefully when offline.
 */
class RemoteVisitLookup(
    private val appContext: Context
) {

    private val tag = "RemoteVisitLookup"
    private val api get() = ApiClient.get(appContext)

    /** Fetches the remote visit; returns `null` on any failure. */
    suspend fun lookup(visitId: String): VisitDto? = try {
        safeCall { api.getVisit(visitId) }
    } catch (e: ApiException) {
        Log.w(tag, "Remote lookup for visit $visitId failed: ${e.code} ${e.message}")
        null
    } catch (e: Exception) {
        Log.e(tag, "Unexpected error looking up visit $visitId", e)
        null
    }

    /**
     * Downloads `personal_photo`, `doc_front` and `doc_back` into the cache
     * directory associated with [localVisitId]. Returns a map keyed by image
     * type with the absolute paths of the files written; missing or failed
     * images are simply absent from the map.
     */
    suspend fun cacheImages(
        remoteVisitId: String,
        localVisitId: String
    ): Map<String, String> {
        val targetDir = imageCacheDir(localVisitId)
        val result = mutableMapOf<String, String>()
        for (type in listOf("personal_photo", "doc_front", "doc_back")) {
            try {
                val body: ResponseBody = api.downloadVisitImage(remoteVisitId, type)
                val file = File(targetDir, "$type.jpg")
                body.byteStream().use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
                if (file.length() > 0L) result[type] = file.absolutePath
            } catch (e: Exception) {
                Log.w(tag, "Could not cache image '$type' for visit $remoteVisitId: ${e.message}")
            }
        }
        return result
    }

    private fun imageCacheDir(localVisitId: String): File {
        val dir = File(appContext.filesDir, "visits/$localVisitId")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
}

