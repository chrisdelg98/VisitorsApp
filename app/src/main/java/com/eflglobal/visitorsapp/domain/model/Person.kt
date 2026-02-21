package com.eflglobal.visitorsapp.domain.model

data class Person(
    val personId: String,
    val fullName: String,
    val documentNumber: String,
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
)

