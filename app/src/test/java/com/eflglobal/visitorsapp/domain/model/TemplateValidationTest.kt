package com.eflglobal.visitorsapp.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [TemplateValidation.score] — the per-field validator that feeds
 * confidence for template-extracted values. Pure JVM.
 */
class TemplateValidationTest {

    @Test
    fun `no validators declared is a neutral pass`() {
        val v = TemplateValidation()
        assertEquals(1f, v.score("anything"), 0f)
    }

    @Test
    fun `blank value always scores zero`() {
        val v = TemplateValidation(regex = "\\d+")
        assertEquals(0f, v.score("   "), 0f)
        assertEquals(0f, v.score(null), 0f)
    }

    @Test
    fun `full pass across all rules`() {
        val v = TemplateValidation(regex = "\\d{8}-\\d", length = 10, charset = "0123456789-")
        assertEquals(1f, v.score("01234567-8"), 0f)
    }

    @Test
    fun `partial pass averages the checks`() {
        // "12345678-9": regex passes (8 digits-1 digit), length 10 != 11 → 1 of 2.
        val v = TemplateValidation(regex = "\\d{8}-\\d", length = 11)
        assertEquals(0.5f, v.score("12345678-9"), 0.001f)
    }

    @Test
    fun `charset violation fails that check`() {
        val v = TemplateValidation(charset = "ABC")
        assertEquals(0f, v.score("ABX"), 0f)
    }
}
