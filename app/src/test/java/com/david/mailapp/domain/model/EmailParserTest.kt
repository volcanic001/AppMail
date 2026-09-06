package com.david.mailapp.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class EmailParserTest {

    @Test
    fun `parseEmailSender con nombre y correo entre corchetes angulares`() {
        val result = parseEmailSender("John Doe <john@example.com>")
        assertEquals("John Doe", result.displayCollapsed)
        assertEquals("John Doe", result.displayFull)
        assertEquals("john@example.com", result.email)
    }

    @Test
    fun `parseEmailSender con nombre entre comillas`() {
        val result = parseEmailSender("\"Jane Smith\" <jane@example.com>")
        assertEquals("Jane Smith", result.displayCollapsed)
        assertEquals("Jane Smith", result.displayFull)
        assertEquals("jane@example.com", result.email)
    }

    @Test
    fun `parseEmailSender con correo sin nombre`() {
        val result = parseEmailSender("<user@example.com>")
        assertEquals("user@example.com", result.displayCollapsed)
        assertEquals("user@example.com", result.displayFull)
        assertEquals("", result.email)
    }

    @Test
    fun `parseEmailSender con correo directo sin corchetes angulares`() {
        val result = parseEmailSender("direct@example.com")
        assertEquals("direct@example.com", result.displayCollapsed)
        assertEquals("direct@example.com", result.displayFull)
        assertEquals("", result.email)
    }

    @Test
    fun `parseEmailSender con truncamiento actual de direcciones largas`() {
        val input = "<verylonglocalname@verylongdomainname.com>"
        val result = parseEmailSender(input)
        assertEquals("verylo..@verylong..", result.displayCollapsed)
        assertEquals("verylonglocalname@verylongdomainname.com", result.displayFull)
        assertEquals("", result.email)
    }

    @Test
    fun `parseEmailSender con nombres Unicode`() {
        val result = parseEmailSender("José María Pérez <jose@example.com>")
        assertEquals("José María Pérez", result.displayCollapsed)
        assertEquals("José María Pérez", result.displayFull)
        assertEquals("jose@example.com", result.email)

        val japaneseResult = parseEmailSender("山田太郎 <yamada@example.com>")
        assertEquals("山田太郎", japaneseResult.displayCollapsed)
        assertEquals("山田太郎", japaneseResult.displayFull)
        assertEquals("yamada@example.com", japaneseResult.email)
    }
}
