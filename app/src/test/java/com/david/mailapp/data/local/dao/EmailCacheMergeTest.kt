package com.david.mailapp.data.local.dao

import com.david.mailapp.data.local.entity.EmailEntity
import com.david.mailapp.domain.model.EmailFolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [mergeWithExisting] — the pure function that merges incoming
 * sync entities with stored entities to preserve PDF metadata and body data.
 */
class EmailCacheMergeTest {

    private fun entity(
        id: String = "msg_1",
        body: String = "",
        cleanBody: String = "",
        pdfJson: String = "[]",
        pdfScanned: Boolean = false,
        hasAtt: Boolean = false
    ): EmailEntity = EmailEntity(
        id = id, threadId = "t1", from = "a", fromInitials = "A",
        to = "b", subject = "s", snippet = "s", timestamp = 1L,
        isRead = true, isStarred = false, hasAttachments = hasAtt,
        labels = "", folder = "inbox",
        body = body, cleanBody = cleanBody,
        pdfAttachmentsJson = pdfJson, pdfMetadataScanned = pdfScanned
    )

    @Test
    fun `incoming scanned and empty clears obsolete PDF metadata`() {
        val existing = entity(
            pdfJson = """[{"fileName":"old.pdf","mimeType":"application/pdf","attachmentId":"att_old","sizeBytes":100}]""",
            pdfScanned = true,
            hasAtt = true
        )
        // Incoming has no body but is newly scanned (format=full) — authoritative empty
        val incoming = entity(
            body = "",
            cleanBody = "",
            pdfJson = "[]",
            pdfScanned = true,
            hasAtt = false
        )

        val merged = mergeWithExisting(incoming, existing)

        assertEquals("[]", merged.pdfAttachmentsJson)
        assertTrue(merged.pdfMetadataScanned)
        assertFalse(merged.hasAttachments)
    }

    @Test
    fun `incoming unscanned preserves existing PDF metadata`() {
        val existing = entity(
            pdfJson = """[{"fileName":"preserved.pdf","mimeType":"application/pdf","attachmentId":"att_pres","sizeBytes":200}]""",
            pdfScanned = true,
            hasAtt = true
        )
        val incoming = entity(
            pdfJson = "[]",
            pdfScanned = false,
            hasAtt = false
        )

        val merged = mergeWithExisting(incoming, existing)

        assertTrue(merged.pdfAttachmentsJson.contains("preserved.pdf"))
        assertTrue(merged.pdfMetadataScanned)
        assertTrue(merged.hasAttachments)
    }

    @Test
    fun `preserves body and cleanBody and PDFs simultaneously`() {
        val existing = entity(
            body = "<html>existing body</html>",
            cleanBody = "existing body",
            pdfJson = """[{"fileName":"keep.pdf","mimeType":"application/pdf","attachmentId":"att_k","sizeBytes":300}]""",
            pdfScanned = true,
            hasAtt = true
        )
        val incoming = entity(
            body = "",
            cleanBody = "",
            pdfJson = "[]",
            pdfScanned = false,
            hasAtt = false
        )

        val merged = mergeWithExisting(incoming, existing)

        assertEquals("<html>existing body</html>", merged.body)
        assertEquals("existing body", merged.cleanBody)
        assertTrue(merged.pdfAttachmentsJson.contains("keep.pdf"))
        assertTrue(merged.pdfMetadataScanned)
        assertTrue(merged.hasAttachments)
    }

    @Test
    fun `incoming scanned body overrides existing body`() {
        val existing = entity(
            body = "<html>old</html>",
            cleanBody = "old",
            pdfScanned = true, hasAtt = false
        )
        val incoming = entity(
            body = "<html>new</html>",
            cleanBody = "new",
            pdfScanned = true, hasAtt = false
        )

        val merged = mergeWithExisting(incoming, existing)

        assertEquals("<html>new</html>", merged.body)
        assertEquals("new", merged.cleanBody)
    }

    @Test
    fun `blank body with pdfMetadataScanned true prevents re-fetch via merge`() {
        // Simulates: email with blank body that was already fetched & scanned
        // (BodyError was delivered, delivered = true in ViewModel).
        // A Room re-emission triggers the merge — the existing entity was already
        // scanned (pdfMetadataScanned = true), incoming sync is also unscanned.
        // The merge must preserve pdfMetadataScanned = true so the ViewModel's
        // !delivered guard is the only thing preventing a re-fetch — the merge
        // itself should never reset the scanned flag.

        val existing = entity(
            body = "",
            cleanBody = "",
            pdfJson = "[]",
            pdfScanned = true,
            hasAtt = false
        )
        val incoming = entity(
            body = "",
            cleanBody = "",
            pdfJson = "[]",
            pdfScanned = false,
            hasAtt = false
        )

        val merged = mergeWithExisting(incoming, existing)

        // The merge must preserve the scanned flag — otherwise
        // needsRemoteFetch = (baseHtml.isBlank() || !pdfMetadataScanned)
        // would evaluate to true and the ViewModel would re-fetch.
        assertTrue("pdfMetadataScanned must be preserved after merge",
            merged.pdfMetadataScanned)
        assertEquals("blank body stays blank", "", merged.body)
    }
}
