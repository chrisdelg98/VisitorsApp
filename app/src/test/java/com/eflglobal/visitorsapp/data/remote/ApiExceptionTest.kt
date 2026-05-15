package com.eflglobal.visitorsapp.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [ApiException].
 *
 * Locks the [ApiException.isTransient] / [ApiException.isAuthFailure]
 * truth tables so future tweaks to error codes don't silently break the
 * retry / auth-wipe contracts that [SyncWorker] relies on.
 */
class ApiExceptionTest {

    // ── isTransient ─────────────────────────────────────────────────────────

    @Test
    fun `network_unavailable is transient`() {
        val e = ApiException(code = ApiErrorCode.NETWORK_UNAVAILABLE, message = "no net")
        assertTrue(e.isTransient)
        assertFalse(e.isAuthFailure)
    }

    @Test
    fun `rate_limit_exceeded is transient`() {
        val e = ApiException(code = ApiErrorCode.RATE_LIMIT_EXCEEDED, message = "slow down", httpStatus = 429)
        assertTrue(e.isTransient)
    }

    @Test
    fun `5xx with UNKNOWN code is transient`() {
        val e = ApiException(code = ApiErrorCode.UNKNOWN, message = "server boom", httpStatus = 503)
        assertTrue(e.isTransient)
    }

    @Test
    fun `4xx with UNKNOWN code is NOT transient`() {
        val e = ApiException(code = ApiErrorCode.UNKNOWN, message = "bad req", httpStatus = 400)
        assertFalse(e.isTransient)
    }

    @Test
    fun `UNKNOWN without httpStatus is NOT transient`() {
        val e = ApiException(code = ApiErrorCode.UNKNOWN, message = "weird")
        assertFalse(e.isTransient)
    }

    @Test
    fun `validation_error is NOT transient`() {
        val e = ApiException(code = ApiErrorCode.VALIDATION_ERROR, message = "bad input", httpStatus = 422)
        assertFalse(e.isTransient)
    }

    @Test
    fun `api_key_invalid is NOT transient`() {
        val e = ApiException(code = ApiErrorCode.API_KEY_INVALID, message = "revoked", httpStatus = 401)
        assertFalse(e.isTransient)
    }

    // ── isAuthFailure ───────────────────────────────────────────────────────

    @Test
    fun `api_key_missing is an auth failure`() {
        val e = ApiException(code = ApiErrorCode.API_KEY_MISSING, message = "no key", httpStatus = 401)
        assertTrue(e.isAuthFailure)
        assertFalse(e.isTransient)
    }

    @Test
    fun `api_key_invalid is an auth failure`() {
        val e = ApiException(code = ApiErrorCode.API_KEY_INVALID, message = "revoked", httpStatus = 401)
        assertTrue(e.isAuthFailure)
    }

    @Test
    fun `station_invalid is NOT an auth failure`() {
        // STATION_INVALID is operator typo on setup screen, NOT a wipe trigger.
        val e = ApiException(code = ApiErrorCode.STATION_INVALID, message = "wrong code", httpStatus = 401)
        assertFalse(e.isAuthFailure)
    }

    @Test
    fun `invalid_credentials is NOT an auth failure for tablet flow`() {
        // INVALID_CREDENTIALS is admin-login only.
        val e = ApiException(code = ApiErrorCode.INVALID_CREDENTIALS, message = "bad pw", httpStatus = 401)
        assertFalse(e.isAuthFailure)
    }

    // ── firstFieldError ─────────────────────────────────────────────────────

    @Test
    fun `firstFieldError returns the first message of the first field`() {
        val errors = linkedMapOf(
            "email" to listOf("Email is required", "Email must be valid"),
            "phone" to listOf("Phone is required")
        )
        val e = ApiException(
            code = ApiErrorCode.VALIDATION_ERROR,
            message = "validation",
            fieldErrors = errors
        )
        assertEquals("Email is required", e.firstFieldError())
    }

    @Test
    fun `firstFieldError returns null when no fieldErrors`() {
        val e = ApiException(code = ApiErrorCode.UNKNOWN, message = "x")
        assertNull(e.firstFieldError())
    }
}

