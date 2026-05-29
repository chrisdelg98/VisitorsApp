package com.eflglobal.visitorsapp.data.remote

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * Formats [epochMillis] as ISO-8601 with the device's zone offset
 * (e.g. `2026-05-28T13:42:11-06:00`). Mirrors the parser used in
 * [com.eflglobal.visitorsapp.domain.usecase.visit.ContinueVisitUseCase].
 *
 * Used for `check_out` and `last_reentry_at` so the backend records the
 * real time the event happened on the device — not the moment the worker
 * eventually pushed it.
 */
fun formatIso8601(epochMillis: Long): String =
    OffsetDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault())
        .toString()

/**
 * Best-effort parser for ISO-8601 timestamps with an offset returned by the
 * backend (e.g. `2026-05-15T08:30:00-06:00`). Returns null on any parse error.
 */
fun parseIso8601(value: String): Long? = try {
    OffsetDateTime.parse(value).toInstant().toEpochMilli()
} catch (_: Exception) {
    null
}
