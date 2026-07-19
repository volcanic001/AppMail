package com.david.mailapp.data.local.entity

import com.david.mailapp.domain.model.Email
import com.david.mailapp.domain.model.EmailFolder
import com.david.mailapp.domain.model.PdfAttachmentMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [EmailEntity] PDF metadata mapping.
 */
class EmailEntityPdfMetadataTest {

    private val samplePdf = PdfAttachmentMetadata(
        "report.pdf",
        "application/pdf",
        "att_1",
        4096L,
        partId = "mime_part_1"
    )

    @Test
    fun `round-trip preserves all PDF metadata fields`() {
        val email = Email(
            id = "msg_1",
            threadId = "thread_1",
            from = "sender@example.com",
            fromInitials = "S",
            to = "me@example.com",
            subject = "Test",
            snippet = "Hello",
            timestamp = 1000L,
            isRead = true,
            isStarred = false,
            hasAttachments = true,
            labels = emptyList(),
            folder = EmailFolder.Inbox,
            body = "<html>body</html>",
            cleanBody = "body",
            pdfAttachments = listOf(samplePdf),
            pdfMetadataScanned = true
        )

        val entity = EmailEntity.fromDomain(email, EmailFolder.Inbox)
        val restored = entity.toDomain()

        assertEquals(email.pdfAttachments.size, restored.pdfAttachments.size)
        assertEquals(email.pdfAttachments[0].fileName, restored.pdfAttachments[0].fileName)
        assertEquals(email.pdfAttachments[0].attachmentId, restored.pdfAttachments[0].attachmentId)
        assertEquals(email.pdfAttachments[0].sizeBytes, restored.pdfAttachments[0].sizeBytes)
        assertEquals(email.pdfAttachments[0].partId, restored.pdfAttachments[0].partId)
        assertEquals(email.pdfMetadataScanned, restored.pdfMetadataScanned)
        assertTrue(restored.hasAttachments)
    }

    @Test
    fun `hasAttachments is true only when pdfAttachments is not empty`() {
        val withPdfs = Email(
            id = "msg_a", threadId = "t", from = "a", fromInitials = "A",
            to = "b", subject = "s", snippet = "s", timestamp = 1L,
            isRead = true, isStarred = false, hasAttachments = false,
            labels = emptyList(), folder = EmailFolder.Inbox,
            body = "", cleanBody = "",
            pdfAttachments = listOf(samplePdf), pdfMetadataScanned = true
        )
        val entityWith = EmailEntity.fromDomain(withPdfs, EmailFolder.Inbox)
        assertTrue("hasAttachments should be true when PDFs exist", entityWith.hasAttachments)

        val withoutPdfs = Email(
            id = "msg_b", threadId = "t", from = "a", fromInitials = "A",
            to = "b", subject = "s", snippet = "s", timestamp = 2L,
            isRead = true, isStarred = false, hasAttachments = true,
            labels = emptyList(), folder = EmailFolder.Inbox,
            body = "", cleanBody = "",
            pdfAttachments = emptyList(), pdfMetadataScanned = true
        )
        val entityWithout = EmailEntity.fromDomain(withoutPdfs, EmailFolder.Inbox)
        assertFalse("hasAttachments should be false when PDFs list is empty", entityWithout.hasAttachments)

        // Round-trip: toDomain also derives hasAttachments from pdfAttachments
        val restoredWithout = entityWithout.toDomain()
        assertFalse(restoredWithout.hasAttachments)
    }
}
