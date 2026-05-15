package com.eflglobal.visitorsapp.data.local.entity

/**
 * Per-row sync state used by [PersonEntity] and [VisitEntity].
 *
 *  - [PENDING]  → created locally, not yet uploaded. Picked up by SyncWorker (FIFO).
 *  - [SYNCING]  → reserved for future use (currently the worker mutates state in
 *                 a single transaction so this transient state is not persisted).
 *  - [SYNCED]   → confirmed in the backend. `remoteId` is non-null.
 *  - [FAILED]   → rejected by the backend with a non-retryable error (4xx
 *                 validation, foreign-station, etc.). Surfaced in admin panel
 *                 for manual intervention; not retried automatically.
 *
 * Stored as a plain string in SQLite to keep migrations trivial.
 */
object SyncStatus {
    const val PENDING = "pending"
    const val SYNCING = "syncing"
    const val SYNCED  = "synced"
    const val FAILED  = "failed"
}

