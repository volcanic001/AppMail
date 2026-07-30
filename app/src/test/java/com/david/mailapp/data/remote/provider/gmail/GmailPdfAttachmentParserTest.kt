package com.david.mailapp.data.remote.provider.gmail

import com.david.mailapp.domain.model.PdfAttachmentMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [Payload.collectPdfAttachments].
 *
 * All tests construct [Payload] trees locally — no real Gmail API, no mocks,
 * no Android Context.
 */
class GmailPdfAttachmentParserTest {

    // ── Helpers ──────────────────────────────────────────────────

    private fun pdfPart(
        filename: String,
        attachmentId: String,
        partId: String = "part_$attachmentId",
        size: Int? = 1024,
        headers: List<Header>? = null,
        parts: List<Payload>? = null
    ): Payload = Payload(
        headers = headers,
        mimeType = "application/pdf",
        body = MessagePartBody(
            size = size,
            attachmentId = attachmentId,
            data = null
        ),
        parts = parts,
        filename = filename,
        partId = partId
    )

    private fun nonPdfPart(
        mimeType: String = "image/jpeg",
        filename: String? = null,
        attachmentId: String? = null,
        parts: List<Payload>? = null
    ): Payload = Payload(
        headers = null,
        mimeType = mimeType,
        body = if (attachmentId != null) MessagePartBody(
            size = 512,
            attachmentId = attachmentId,
            data = null
        ) else null,
        parts = parts,
        filename = filename
    )

    private fun header(name: String, value: String): Header = Header(name, value)

    // ── Test 1: PDF válido en la raíz ────────────────────────────

    @Test
    fun `detects a valid PDF at root level`() {
        val payload = pdfPart(filename = "report.pdf", attachmentId = "att_1")

        val result = payload.collectPdfAttachments()

        assertEquals(1, result.size)
        with(result[0]) {
            assertEquals("report.pdf", fileName)
            assertEquals("application/pdf", mimeType)
            assertEquals("att_1", attachmentId)
            assertEquals(1024L, sizeBytes)
            assertEquals("part_att_1", partId)
        }
    }

    @Test
    fun `stable identity uses immutable partId when attachmentId changes`() {
        val first = pdfPart(
            filename = "report.pdf",
            attachmentId = "temporary_token_1",
            partId = "mime_part_2"
        ).collectPdfAttachments().single()
        val refreshed = pdfPart(
            filename = "report.pdf",
            attachmentId = "temporary_token_2",
            partId = "mime_part_2"
        ).collectPdfAttachments().single()

        assertEquals("mime_part_2", first.stableId)
        assertEquals(first.stableId, refreshed.stableId)
    }

    // ── Test 2: Varios PDFs en distintos niveles anidados ────────

    @Test
    fun `detects multiple PDFs at different nesting levels`() {
        val nestedPdf = pdfPart(filename = "nested.pdf", attachmentId = "att_nested")
        val payload = Payload(
            headers = null,
            mimeType = "multipart/mixed",
            body = null,
            parts = listOf(
                pdfPart(filename = "top.pdf", attachmentId = "att_top"),
                Payload(
                    headers = null,
                    mimeType = "multipart/alternative",
                    body = null,
                    parts = listOf(nestedPdf)
                )
            ),
            filename = null
        )

        val result = payload.collectPdfAttachments()

        assertEquals(2, result.size)
        assertEquals("top.pdf", result[0].fileName)
        assertEquals("nested.pdf", result[1].fileName)
    }

    // ── Test 3: Extensión .PDF en mayúsculas ─────────────────────

    @Test
    fun `accepts uppercase PDF extension`() {
        val payload = pdfPart(filename = "document.PDF", attachmentId = "att_pdf")
        val result = payload.collectPdfAttachments()
        assertEquals(1, result.size)
        assertEquals("document.PDF", result[0].fileName)
    }

    // ── Test 4: Conserva PDF en árbol con JPG, PNG y ZIP ────────

    @Test
    fun `preserves PDF in a tree that also contains JPG, PNG and ZIP`() {
        val payload = Payload(
            headers = null,
            mimeType = "multipart/mixed",
            body = null,
            parts = listOf(
                nonPdfPart(mimeType = "image/jpeg", filename = "photo.jpg", attachmentId = "att_jpg"),
                pdfPart(filename = "doc.pdf", attachmentId = "att_pdf"),
                nonPdfPart(mimeType = "image/png", filename = "image.png", attachmentId = "att_png"),
                nonPdfPart(mimeType = "application/zip", filename = "archive.zip", attachmentId = "att_zip")
            ),
            filename = null
        )

        val result = payload.collectPdfAttachments()

        assertEquals(1, result.size)
        assertEquals("doc.pdf", result[0].fileName)
    }

    // ── Test 5: Rechaza MIME incorrecto ──────────────────────────

    @Test
    fun `rejects wrong MIME types`() {
        // "Application/Pdf" (capital A)
        val wrongCase = Payload(
            headers = null,
            mimeType = "Application/Pdf",
            body = MessagePartBody(size = 100, attachmentId = "att", data = null),
            parts = null,
            filename = "file.pdf"
        )
        // application/octet-stream
        val octet = nonPdfPart(
            mimeType = "application/octet-stream",
            filename = "file.pdf",
            attachmentId = "att_octet"
        )
        // text/plain
        val text = nonPdfPart(
            mimeType = "text/plain",
            filename = "file.pdf",
            attachmentId = "att_text"
        )

        assertEquals(0, wrongCase.collectPdfAttachments().size)
        assertEquals(0, octet.collectPdfAttachments().size)
        assertEquals(0, text.collectPdfAttachments().size)
    }

    // ── Test 6: Rechaza nombres inválidos ────────────────────────

    @Test
    fun `rejects invalid filenames - txt, empty, no extension`() {
        val txtFile = pdfPart(filename = "notes.txt", attachmentId = "att_txt")
        val emptyName = pdfPart(filename = "", attachmentId = "att_empty")
        val noExt = pdfPart(filename = "readme", attachmentId = "att_noext")

        assertTrue(txtFile.collectPdfAttachments().isEmpty())
        assertTrue(emptyName.collectPdfAttachments().isEmpty())
        assertTrue(noExt.collectPdfAttachments().isEmpty())
    }

    // ── Test 7: Rechaza attachmentId inválido ────────────────────

    @Test
    fun `rejects null, empty and blank attachmentId`() {
        val nullAtt = pdfPart(filename = "f.pdf", attachmentId = "ok").copy(
            body = MessagePartBody(size = 100, attachmentId = null)
        )
        val emptyAtt = pdfPart(filename = "f.pdf", attachmentId = "ok").copy(
            body = MessagePartBody(size = 100, attachmentId = "")
        )
        val blankAtt = pdfPart(filename = "f.pdf", attachmentId = "ok").copy(
            body = MessagePartBody(size = 100, attachmentId = "  ")
        )
        // Also test the copy constructor for body — validAtt should be detected
        val validAtt = pdfPart(filename = "f.pdf", attachmentId = "valid")

        assertTrue(nullAtt.collectPdfAttachments().isEmpty())
        assertTrue(emptyAtt.collectPdfAttachments().isEmpty())
        assertTrue(blankAtt.collectPdfAttachments().isEmpty())
        assertEquals(1, validAtt.collectPdfAttachments().size)
    }

    // ── Test 8: Rechaza Content-Disposition: inline ──────────────

    @Test
    fun `rejects Content-Disposition inline`() {
        val inlineDisposition = pdfPart(
            filename = "inline.pdf",
            attachmentId = "att_inline",
            headers = listOf(header("Content-Disposition", "inline"))
        )
        val inlineWithParams = pdfPart(
            filename = "inline.pdf",
            attachmentId = "att_inline2",
            headers = listOf(header("Content-Disposition", "INLINE; filename=inline.pdf"))
        )
        val attachmentOk = pdfPart(
            filename = "att.pdf",
            attachmentId = "att_ok",
            headers = listOf(header("Content-Disposition", "attachment"))
        )

        assertTrue(inlineDisposition.collectPdfAttachments().isEmpty())
        assertTrue(inlineWithParams.collectPdfAttachments().isEmpty())
        assertEquals(1, attachmentOk.collectPdfAttachments().size)
    }

    // ── Test 9: Rechaza parte con Content-ID solo sin disposition ────

    @Test
    fun `rejects part with Content-ID only when not explicit attachment`() {
        // CID + NO disposition → treated as inline image → reject
        val inlineCid = Payload(
            mimeType = "application/pdf",
            body = MessagePartBody(size = 100, attachmentId = "att_inline"),
            parts = null,
            filename = "inline.pdf",
            headers = listOf(
                header("Content-Id", "<abc123@mail.gmail.com>")
            )
        )
        // CID + disposition=attachment → accept (Gmail includes CID for attachments)
        val attachmentWithCid = pdfPart(
            filename = "att.pdf",
            attachmentId = "att_cid_att",
            headers = listOf(
                header("Content-Id", "<cid@mail.gmail.com>"),
                header("Content-Disposition", "attachment")
            )
        )
        // Empty CID → accepted
        val emptyCid = pdfPart(
            filename = "ok.pdf",
            attachmentId = "att_ok",
            headers = listOf(header("Content-Id", ""))
        )
        // No CID → accepted
        val noCid = pdfPart(
            filename = "no_cid.pdf",
            attachmentId = "att_nocid"
        )

        assertTrue("inline part with CID must be rejected", inlineCid.collectPdfAttachments().isEmpty())
        assertEquals("attachment with CID must be accepted", 1, attachmentWithCid.collectPdfAttachments().size)
        assertEquals("empty CID is accepted", 1, emptyCid.collectPdfAttachments().size)
        assertEquals("no CID is accepted", 1, noCid.collectPdfAttachments().size)
    }

    // ── Test 10: Deduplica por attachmentId ──────────────────────

    @Test
    fun `deduplicates by attachmentId keeping first occurrence`() {
        val payload = Payload(
            headers = null,
            mimeType = "multipart/mixed",
            body = null,
            parts = listOf(
                pdfPart(filename = "first.pdf", attachmentId = "dup_att"),
                pdfPart(filename = "second.pdf", attachmentId = "dup_att")
            ),
            filename = null
        )

        val result = payload.collectPdfAttachments()

        assertEquals(1, result.size)
        assertEquals("first.pdf", result[0].fileName)
        assertEquals("dup_att", result[0].attachmentId)
    }

    // ── Test 11: sizeBytes es null cuando no hay tamaño ──────────

    @Test
    fun `preserves null sizeBytes when Gmail does not report size`() {
        val payload = Payload(
            headers = null,
            mimeType = "application/pdf",
            body = MessagePartBody(
                size = null,
                attachmentId = "att_nosize"
            ),
            parts = null,
            filename = "nosize.pdf"
        )

        val result = payload.collectPdfAttachments()

        assertEquals(1, result.size)
        assertEquals("nosize.pdf", result[0].fileName)
        assertEquals(null, result[0].sizeBytes)
    }

    // ── Test 12: Lista vacía para árbol sin PDFs válidos ─────────

    @Test
    fun `returns empty list for a tree with no valid PDFs`() {
        val payload = Payload(
            headers = null,
            mimeType = "multipart/mixed",
            body = null,
            parts = listOf(
                nonPdfPart(mimeType = "text/plain", filename = null),
                nonPdfPart(
                    mimeType = "application/pdf",
                    filename = "notes.txt",
                    attachmentId = "att_txt"
                ),
                nonPdfPart(
                    mimeType = "application/pdf",
                    filename = "inline.pdf",
                    attachmentId = "att_inline",
                    parts = null
                ).copy(
                    headers = listOf(
                        header("Content-Disposition", "inline")
                    )
                )
            ),
            filename = null
        )

        assertTrue(payload.collectPdfAttachments().isEmpty())
    }

    // ── Mixed-case header reuse ────────────────────────────────

    @Test
    fun `accepts PDF with mixed-case Content-Disposition attachment`() {
        val payload = pdfPart(
            filename = "doc.pdf",
            attachmentId = "att_mc",
            headers = listOf(
                header("content-disposition", "Attachment"),
                header("content-type", "Application/Pdf")
            )
        )
        val result = payload.collectPdfAttachments()
        assertEquals(1, result.size)
        assertEquals("doc.pdf", result[0].fileName)
        assertEquals("att_mc", result[0].attachmentId)
    }

    @Test
    fun `rejects inline PDF regardless of Content-ID capitalization`() {
        val payload = nonPdfPart(
            parts = listOf(
                pdfPart(
                    filename = "inline.pdf",
                    attachmentId = "att_ci",
                    headers = listOf(
                        header("content-disposition", "INLINE"),
                        header("CONTENT-ID", "<img001@mail.gmail.com>")
                    )
                )
            )
        )
        assertTrue(payload.collectPdfAttachments().isEmpty())
    }
}
