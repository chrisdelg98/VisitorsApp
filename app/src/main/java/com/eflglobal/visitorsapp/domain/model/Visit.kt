package com.eflglobal.visitorsapp.domain.model

data class Visit(
    val visitId: String,
    val personId: String,
    val stationId: String?,
    val visitingPersonName: String,
    val visitorType: String,
    val entryDate: Long,
    val exitDate: Long?,
    val qrCodeValue: String,
    val isSynced: Boolean,
    val lastSyncAt: Long?
)

