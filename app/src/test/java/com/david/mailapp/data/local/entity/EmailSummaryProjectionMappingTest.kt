package com.david.mailapp.data.local.entity

import com.david.mailapp.domain.model.EmailFolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailSummaryProjectionMappingTest {

    @Test
    fun toDomain_mapsLightweightFieldsAndEmptiesHeavyFields() {
        val projection = EmailSummaryProjection(
            id = "msg1",
            threadId = "thread1",
            from = "sender@test.com",
            fromInitials = "S",
            to = "me@test.com",
            subject = "Subject",
            snippet = "Snippet",
            timestamp = 1000L,
            isRead = true,
            isStarred = false,
            hasAttachments = true,
            labels = "IMPORTANT,WORK,,",
            folder = "inbox"
        )

        val domain = projection.toDomain()

        // Verify lightweight fields
        assertEquals("msg1", domain.id)
        assertEquals("thread1", domain.threadId)
        assertEquals("sender@test.com", domain.from)
        assertEquals("S", domain.fromInitials)
        assertEquals("me@test.com", domain.to)
        assertEquals("Subject", domain.subject)
        assertEquals("Snippet", domain.snippet)
        assertEquals(1000L, domain.timestamp)
        assertTrue(domain.isRead)
        assertFalse(domain.isStarred)
        assertTrue(domain.hasAttachments)

        // Verify label conversion (empty labels dropped)
        assertEquals(listOf("IMPORTANT", "WORK"), domain.labels)

        // Verify folder conversion
        assertEquals(EmailFolder.Inbox, domain.folder)

        // Verify heavy fields are forced to empty/null values
        assertEquals("", domain.body)
        assertEquals("", domain.cleanBody)
        assertTrue(domain.pdfAttachments.isEmpty())
        assertFalse(domain.pdfMetadataScanned)
        assertEquals(null, domain.rfcMessageId)
        assertEquals(null, domain.rfcReferences)
    }

    @Test
    fun toDomain_mapsTrashAndOtherFoldersCorrectly() {
        val trashProj = EmailSummaryProjection(
            id = "msg2",
            threadId = "t2",
            from = "a",
            fromInitials = "A",
            to = "b",
            subject = "s",
            snippet = "s",
            timestamp = 0L,
            isRead = false,
            isStarred = false,
            hasAttachments = false,
            labels = "",
            folder = "trash"
        )
        assertEquals(EmailFolder.Trash, trashProj.toDomain().folder)

        val otherProj = trashProj.copy(folder = "other")
        assertEquals(EmailFolder.Other, otherProj.toDomain().folder)
    }
}
