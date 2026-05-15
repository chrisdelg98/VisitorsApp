package com.eflglobal.visitorsapp.data.remote

/**
 * Tagged error codes returned by the Visitors API inside the standard
 * envelope (`ApiResponse.code`).
 *
 * Keep this list 1-to-1 with the backend contract in
 * `docs/ANDROID_INTEGRATION.md`. The exhaustive list of codes is:
 *
 *  | HTTP | code                          | Notes                                          |
 *  | ---  | ---                           | ---                                            |
 *  | 401  | API_KEY_MISSING               | header absent — wipe SecureStore, go to setup  |
 *  | 401  | API_KEY_INVALID               | key revoked   — wipe SecureStore, go to setup  |
 *  | 401  | INVALID_CREDENTIALS           | admin login                                    |
 *  | 401  | STATION_INVALID               | wrong code in setup screen                     |
 *  | 403  | ACCOUNT_DISABLED              | admin login                                    |
 *  | 403  | FORBIDDEN                     | admin role insufficient                        |
 *  | 403  | SELF_DEACTIVATION_FORBIDDEN   | admin users                                    |
 *  | 403  | SELF_ROLE_CHANGE_FORBIDDEN    | admin users                                    |
 *  | 403  | VISIT_FOREIGN_STATION         | trying to mutate a visit owned by another stn  |
 *  | 404  | IMAGE_NOT_FOUND               | image missing on backend                       |
 *  | 404  | NO_VISITS                     | visitor has no prior visits                    |
 *  | 409  | VISIT_ALREADY_COMPLETED       | checkout on a closed visit                     |
 *  | 422  | (no code, see `errors`)       | Laravel FormRequest validation                 |
 *  | 429  | (no code)                     | rate limit                                     |
 */
object ApiErrorCode {
    const val API_KEY_MISSING = "API_KEY_MISSING"
    const val API_KEY_INVALID = "API_KEY_INVALID"
    const val INVALID_CREDENTIALS = "INVALID_CREDENTIALS"
    const val STATION_INVALID = "STATION_INVALID"

    const val ACCOUNT_DISABLED = "ACCOUNT_DISABLED"
    const val FORBIDDEN = "FORBIDDEN"
    const val SELF_DEACTIVATION_FORBIDDEN = "SELF_DEACTIVATION_FORBIDDEN"
    const val SELF_ROLE_CHANGE_FORBIDDEN = "SELF_ROLE_CHANGE_FORBIDDEN"
    const val VISIT_FOREIGN_STATION = "VISIT_FOREIGN_STATION"

    const val IMAGE_NOT_FOUND = "IMAGE_NOT_FOUND"
    const val NO_VISITS = "NO_VISITS"

    const val VISIT_ALREADY_COMPLETED = "VISIT_ALREADY_COMPLETED"

    // ── Client-synthesised codes (never returned by backend) ──────────────────
    /** Network failure / timeout / DNS / etc. — request never reached the server. */
    const val NETWORK_UNAVAILABLE = "NETWORK_UNAVAILABLE"
    /** HTTP 422 with no `code` — read `errors` map for field-level messages. */
    const val VALIDATION_ERROR = "VALIDATION_ERROR"
    /** HTTP 429 with no `code`. */
    const val RATE_LIMIT_EXCEEDED = "RATE_LIMIT_EXCEEDED"
    /** Anything else: 5xx, unexpected JSON shape, etc. */
    const val UNKNOWN = "UNKNOWN"
}

