package com.eflglobal.visitorsapp.data.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.eflglobal.visitorsapp.data.repository.TemplateRepository

/**
 * Syncs the OCR template catalog once per day (and on app start) via a
 * conditional `GET /v1/ocr/templates`. See [TemplateRepository.sync].
 *
 * All error handling lives in the repository (offline-first); this worker just
 * translates the outcome into a WorkManager [Result]:
 *   - transient failure → [Result.retry] (WorkManager backoff)
 *   - everything else   → [Result.success] (nothing to retry / already applied)
 */
class TemplateSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val tag = "TemplateSyncWorker"

    override suspend fun doWork(): Result {
        return when (val result = TemplateRepository.get(applicationContext).sync()) {
            is TemplateRepository.SyncResult.Updated -> {
                Log.i(tag, "catalog updated: ${result.count} templates")
                Result.success()
            }
            TemplateRepository.SyncResult.NotModified -> Result.success()
            TemplateRepository.SyncResult.Skipped -> Result.success()
            is TemplateRepository.SyncResult.Failed -> {
                Log.w(tag, "catalog sync failed (${result.reason}) — will retry")
                Result.retry()
            }
        }
    }
}
