package com.eflglobal.visitorsapp.domain.model

data class Person(
    val personId: String,
    /**
     * UUID assigned by the backend (mirrors [personId] for visitors that
     * originate remotely). Null until the visitor has been pushed via
     * `POST /v1/visitors`. The SyncWorker uses this to avoid re-creating a
     * visitor that already exists in the backend.
     */
    val remoteId: String? = null,
    val firstName: String,
    val lastName: String,
    val documentNumber: String?,
    val documentType: String,
    val profilePhotoPath: String?,
    val documentFrontPath: String?,
    val documentBackPath: String?,
    val company: String?,
    val email: String,
    val phoneNumber: String,
    val createdAt: Long,
    val isSynced: Boolean,
    val lastSyncAt: Long?
) {
    /** Full display name — computed from firstName + lastName. */
    val fullName: String get() = "$firstName $lastName".trim()
}
