package com.eflglobal.visitorsapp.domain.model

data class Station(
    val stationId: String,
    val pin: String,
    val stationName: String,
    val countryCode: String,
    val createdAt: Long,
    val isActive: Boolean
)

