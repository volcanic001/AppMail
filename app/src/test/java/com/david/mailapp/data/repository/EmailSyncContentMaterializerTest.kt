package com.david.mailapp.data.repository

import com.david.mailapp.domain.model.Email
import com.david.mailapp.domain.model.EmailBodyKind
import com.david.mailapp.domain.model.EmailContentState
import com.david.mailapp.domain.model.EmailFolder
import com.david.mailapp.domain.model.EmailInlineReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailSyncContentMaterializerTest {

    @Test
    fun `HTML is persisted raw before deferred cleaning`() {
        val raw = "<p style=\"color:red\">body</p>"
        val prepared = email(body = raw, bodyKind = EmailBodyKind.HTML).materializeForMailboxSync()

        assertEquals(raw, prepared.body)
        assertEquals("", prepared.cleanBody)
        assertEquals(EmailContentState.READY, prepared.contentState)
        assertEquals(raw.toByteArray().size.toLong() + 2L, prepared.cachedContentBytes)
    }

    @Test
    fun `plain text is immediately usable without Jsoup`() {
        val body = "first line\nsecond line"
        val prepared = email(body = body, bodyKind = EmailBodyKind.PLAIN_TEXT).materializeForMailboxSync()

        assertEquals(body, prepared.cleanBody)
        assertEquals(body.toByteArray().size.toLong() * 2L + 2L, prepared.cachedContentBytes)
    }

    @Test
    fun `missing payload remains partial and cannot replace cached content`() {
        val prepared = email(
            body = "",
            bodyKind = EmailBodyKind.UNKNOWN,
            contentState = EmailContentState.EMPTY,
            pdfMetadataScanned = false
        ).materializeForMailboxSync()

        assertEquals(EmailContentState.NOT_FETCHED, prepared.contentState)
        assertEquals(EmailBodyKind.UNKNOWN, prepared.bodyKind)
        assertTrue(prepared.body.isEmpty())
    }

    @Test
    fun `content larger than its budget keeps metadata but is not cached`() {
        val prepared = email(
            body = "body",
            bodyKind = EmailBodyKind.HTML
        ).materializeForMailboxSync(maxBudgetBytes = 5L)

        assertEquals(EmailContentState.NOT_FETCHED, prepared.contentState)
        assertEquals(EmailBodyKind.UNKNOWN, prepared.bodyKind)
        assertEquals("", prepared.body)
        assertTrue(prepared.pdfMetadataScanned)
    }

    @Test
    fun `deferred HTML result includes raw clean and CID bytes`() {
        val refs = listOf(EmailInlineReference("image", "attachment", "image/png"))
        val source = email(
            body = "<p>raw</p>",
            bodyKind = EmailBodyKind.HTML,
            inlineReferences = refs
        ).materializeForMailboxSync()

        val cleaned = source.toCleanedSyncContent { "<p>clean</p>" }!!

        val refsBytes = com.david.mailapp.data.local.converter.InlineContentReferenceCodec
            .encode(refs)
            .toByteArray()
            .size
        assertEquals("<p>raw</p>", cleaned.expectedRawBody)
        assertEquals("<p>clean</p>", cleaned.cleanBody)
        assertEquals(
            "<p>raw</p>".toByteArray().size.toLong() +
                "<p>clean</p>".toByteArray().size.toLong() + refsBytes,
            cleaned.cachedContentBytes
        )
    }

    private fun email(
        body: String,
        bodyKind: EmailBodyKind,
        contentState: EmailContentState = EmailContentState.READY,
        pdfMetadataScanned: Boolean = true,
        inlineReferences: List<EmailInlineReference> = emptyList()
    ) = Email(
        id = "mail",
        threadId = "thread",
        from = "sender@example.com",
        fromInitials = "S",
        to = "recipient@example.com",
        subject = "subject",
        snippet = "snippet",
        timestamp = 1L,
        isRead = true,
        isStarred = false,
        hasAttachments = false,
        labels = listOf("INBOX"),
        folder = EmailFolder.Inbox,
        body = body,
        pdfMetadataScanned = pdfMetadataScanned,
        contentState = contentState,
        bodyKind = bodyKind,
        inlineReferences = inlineReferences
    )
}
