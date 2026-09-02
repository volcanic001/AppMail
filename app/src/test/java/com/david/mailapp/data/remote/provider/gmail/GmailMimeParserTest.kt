package com.david.mailapp.data.remote.provider.gmail

import com.david.mailapp.domain.model.EmailBodyKind
import com.david.mailapp.domain.model.EmailContentState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class GmailMimeParserTest {

    private fun b64(text: String) = Base64.getUrlEncoder().encodeToString(text.toByteArray())

    private fun payload(
        mimeType: String = "multipart/alternative",
        filename: String? = null,
        body: MessagePartBody? = null,
        parts: List<Payload>? = null,
        headers: List<Header>? = null,
        contentId: String? = null
    ): Payload {
        val h = headers?.toMutableList() ?: mutableListOf()
        if (contentId != null) h.add(Header("Content-Id", contentId))
        return Payload(
            partId = "1",
            mimeType = mimeType,
            filename = filename,
            headers = h.ifEmpty { null },
            body = body,
            parts = parts
        )
    }

    private fun part(
        mimeType: String,
        data: String? = null,
        attachmentId: String? = null,
        filename: String? = null,
        contentId: String? = null,
        headers: List<Header>? = null
    ): Payload {
        val h = headers?.toMutableList() ?: mutableListOf()
        if (contentId != null) h.add(Header("Content-Id", contentId))
        return Payload(
            partId = "sub",
            mimeType = mimeType,
            filename = filename,
            headers = h.ifEmpty { null },
            body = MessagePartBody(attachmentId = attachmentId, data = data, size = data?.length ?: 0),
            parts = null
        )
    }
    
    private fun msg(payload: Payload?) = MessageResponse(id = "1", threadId = "1", payload = payload)

    @Test
    fun `multipart alternative selects HTML over PLAIN`() {
        val root = payload(
            parts = listOf(
                part("text/plain", data = b64("plain text")),
                part("text/html", data = b64("<html>html text</html>"))
            )
        )
        val result = GmailMimeParser.parse(msg(root))
        assertEquals(EmailBodyKind.HTML, result.bodyKind)
        assertEquals("<html>html text</html>", result.body)
        assertEquals(EmailContentState.READY, result.contentState)
    }

    @Test
    fun `nested MIME extracts body, CID and PDF together`() {
        val root = payload(
            parts = listOf(
                part("text/html", data = b64("body")),
                part("image/png", attachmentId = "img1", contentId = "<cid1>"),
                part("application/pdf", attachmentId = "pdf1", filename = "doc.pdf")
            )
        )
        val result = GmailMimeParser.parse(msg(root))
        assertEquals("body", result.body)
        assertEquals(1, result.inlineReferences.size)
        assertEquals("cid1", result.inlineReferences[0].contentId)
        assertEquals(1, result.pdfAttachments.size)
        assertEquals("doc.pdf", result.pdfAttachments[0].fileName)
    }

    @Test
    fun `plain text literal and no pre tag`() {
        val root = part("text/plain", data = b64("hello & <world>\nline2"))
        val result = GmailMimeParser.parse(msg(root))
        assertEquals(EmailBodyKind.PLAIN_TEXT, result.bodyKind)
        assertEquals("hello & <world>\nline2", result.body)
    }

    @Test
    fun `invalid base64 HTML falls back to plain text`() {
        val root = payload(
            parts = listOf(
                part("text/plain", data = b64("valid plain")),
                part("text/html", data = "invalid!base64!%")
            )
        )
        val result = GmailMimeParser.parse(msg(root))
        assertEquals(EmailBodyKind.PLAIN_TEXT, result.bodyKind)
        assertEquals("valid plain", result.body)
    }

    @Test
    fun `first invalid HTML is skipped for second valid HTML`() {
        val root = payload(
            parts = listOf(
                part("text/html", data = "invalid!base64!%"),
                part("text/html", data = b64("valid html"))
            )
        )
        val result = GmailMimeParser.parse(msg(root))
        assertEquals(EmailBodyKind.HTML, result.bodyKind)
        assertEquals("valid html", result.body)
    }

    @Test
    fun `attached text parts are ignored for body`() {
        val root = payload(
            parts = listOf(
                part("text/plain", data = b64("ignored"), filename = "file.txt"),
                part("text/plain", data = b64("ignored"), attachmentId = "att1"),
                part("text/plain", data = b64("valid body"))
            )
        )
        val result = GmailMimeParser.parse(msg(root))
        assertEquals("valid body", result.body)
    }

    @Test
    fun `empty message yields EMPTY and UNKNOWN`() {
        val result = GmailMimeParser.parse(msg(null))
        assertEquals(EmailContentState.EMPTY, result.contentState)
        assertEquals(EmailBodyKind.UNKNOWN, result.bodyKind)
        assertEquals(null, result.body)
    }

    @Test
    fun `CID with angle brackets normalized`() {
        val root = part("image/jpeg", attachmentId = "a1", contentId = "<my-cid>")
        val result = GmailMimeParser.parse(msg(root))
        assertEquals("my-cid", result.inlineReferences[0].contentId)
    }

    @Test
    fun `PDF deduplicated and respects disposition`() {
        val root = payload(
            parts = listOf(
                part("application/pdf", attachmentId = "pdf1", filename = "doc.pdf"),
                part("application/pdf", attachmentId = "pdf1", filename = "doc2.pdf"),
                part("application/pdf", attachmentId = "pdf2", filename = "doc3.pdf", headers = listOf(Header("Content-Disposition", "inline")))
            )
        )
        val result = GmailMimeParser.parse(msg(root))
        assertEquals(1, result.pdfAttachments.size)
        assertEquals("doc.pdf", result.pdfAttachments[0].fileName)
    }

    @Test
    fun `PDF preserved even without body`() {
        val root = part("application/pdf", attachmentId = "pdf1", filename = "doc.pdf")
        val result = GmailMimeParser.parse(msg(root))
        assertEquals(EmailContentState.EMPTY, result.contentState)
        assertEquals(1, result.pdfAttachments.size)
    }
}
