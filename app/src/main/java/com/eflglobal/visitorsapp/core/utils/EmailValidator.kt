package com.eflglobal.visitorsapp.core.utils

/**
 * Lightweight email-format check shared by the UI (inline field validation)
 * and the sync layer (so an invalid address is dropped rather than shipped to
 * the backend, which rejects it with a 422 and parks the whole visit as failed).
 *
 * Email is an optional field everywhere by product decision — this only guards
 * the *format* when a value is actually present. A blank string is not "valid",
 * so callers should treat blank as "no email" before checking.
 *
 * The pattern is intentionally permissive (a single `@`, a dotted domain with a
 * 2+ char TLD, no spaces). We are not trying to be an RFC-5322 validator — just
 * catching the common typos (missing `@`, trailing dot) that make the backend
 * reject the row.
 */
object EmailValidator {

    private val EMAIL_REGEX = Regex(
        "^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$"
    )

    /** True when [value] is a non-blank, well-formed email address. */
    fun isValid(value: String?): Boolean {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isEmpty()) return false
        return EMAIL_REGEX.matches(trimmed)
    }
}
