package com.eflglobal.visitorsapp.core.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [AamvaParser] — the AAMVA PDF417 parser used for US/CA
 * driver licences & IDs. Pure JVM (no Android deps).
 */
class AamvaParserTest {

    // A representative AAMVA payload (control separators as \n, as ML Kit returns).
    private val sample = buildString {
        append("@\n")
        append("\r")
        append("ANSI 636000090002DL00410288ZV03290015DL\n")
        append("DAQD12345678\n")
        append("DCSSMITH\n")
        append("DACJOHN\n")
        append("DADQUINCY\n")
        append("DBB01151985\n")
        append("DBA01152027\n")
        append("DBC1\n")
        append("DAG123 MAIN ST\n")
    }

    @Test
    fun `detects aamva by header`() {
        assertTrue(AamvaParser.isAamva(sample))
        assertFalse(AamvaParser.isAamva("random text with no ansi marker"))
    }

    @Test
    fun `parses core identity fields`() {
        val data = AamvaParser.parse(sample)
        assertNotNull(data)
        requireNotNull(data)
        assertEquals("Smith", data.lastName)
        assertEquals("John", data.firstName)
        assertEquals("Quincy", data.middleName)
        assertEquals("D12345678", data.documentNumber)
        assertEquals("M", data.sex)
        assertTrue(data.isUsable)
    }

    @Test
    fun `normalises US date MMDDCCYY to iso`() {
        val data = AamvaParser.parse(sample)
        requireNotNull(data)
        // 01 15 1985 → 1985-01-15
        assertEquals("1985-01-15", data.dateOfBirth)
        assertEquals("2027-01-15", data.expiryDate)
    }

    @Test
    fun `returns null for non-aamva content`() {
        assertNull(AamvaParser.parse("DUI 01234567-8 EL SALVADOR"))
    }
}
