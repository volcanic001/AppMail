package com.david.mailapp.data.local.entity

import com.david.mailapp.domain.model.Email
import com.david.mailapp.domain.model.EmailFolder
import com.david.mailapp.domain.model.PdfAttachmentMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests for RFC header field round-trip through EmailEntity.
 *
 * Verifies that rfcMessageId and rfcReferences survive
 * fromDomain() → toDomain() unchanged, and that omitting
 * them keeps both fields null.
 *
 * Target: Fase 1.2 — Room v6 with RFC headers.
 */
class EmailEntityRfcMappingTest {

    @Test
    fun `rfcMessageId y rfcReferences no nulos sobreviven round-trip`() {
        val original = Email(
            id = "e1",
            threadId = "thread_1",
            from = "sender@test.com",
            fromInitials = "S",
            to = "recipient@test.com",
            subject = "Test RFC",
            snippet = "RFC round-trip test",
            timestamp = 1000L,
            isRead = false,
            isStarred = false,
            hasAttachments = false,
            labels = emptyList(),
            folder = EmailFolder.Inbox,
            rfcMessageId = "<abc123@mail.gmail.com>",
            rfcReferences = "<parent@mail.gmail.com> <grandparent@mail.gmail.com>"
        )

        val entity = EmailEntity.fromDomain(original, EmailFolder.Inbox)
        val restored = entity.toDomain()

        assertEquals("rfcMessageId preserved", "<abc123@mail.gmail.com>", restored.rfcMessageId)
        assertEquals("rfcReferences preserved", "<parent@mail.gmail.com> <grandparent@mail.gmail.com>", restored.rfcReferences)
    }

    @Test
    fun `rfcMessageId y rfcReferences nulos por defecto tras round-trip`() {
        val original = Email(
            id = "e2",
            threadId = "thread_2",
            from = "sender@test.com",
            fromInitials = "S",
            to = "recipient@test.com",
            subject = "No RFC",
            snippet = "Default null test",
            timestamp = 1000L,
            isRead = false,
            isStarred = false,
            hasAttachments = false,
            labels = emptyList(),
            folder = EmailFolder.Trash
        )

        val entity = EmailEntity.fromDomain(original, EmailFolder.Trash)
        val restored = entity.toDomain()

        assertNull("rfcMessageId should be null by default", restored.rfcMessageId)
        assertNull("rfcReferences should be null by default", restored.rfcReferences)
    }
}
