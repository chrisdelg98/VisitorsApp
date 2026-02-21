package com.eflglobal.visitorsapp.domain.model

/**
 * Modelo para visitas activas mostradas en búsqueda de finalización.
 */
data class ActiveVisit(
    val visitId: String,
    val personName: String,
    val visitingPerson: String,
    val entryTime: String
)

