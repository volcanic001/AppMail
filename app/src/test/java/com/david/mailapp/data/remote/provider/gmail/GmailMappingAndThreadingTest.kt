package com.david.mailapp.data.remote.provider.gmail

import com.david.mailapp.data.remote.provider.ReplyContext
import com.david.mailapp.core.localization.StringProvider
import com.david.mailapp.domain.model.Email
import com.david.mailapp.domain.model.EmailFolder
import com.david.mailapp.feature.compose.ComposeArgs
import com.david.mailapp.feature.compose.ComposeEmailSource
import com.david.mailapp.feature.compose.ComposeViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GmailMappingAndThreadingTest {

    private val mainDispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(mainDispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    // 1–7 preserved from original plan
    @Test fun headers_mixed_case_map_and_preserve_rfc_fields() {
        val headersList = listOf(
            Header("From", "sender@test.com"), Header("to", "recipient@test.com"),
            Header("SUBJECT", "Hello"), Header("Message-ID", "<a@b.com>"), Header("references", "<r1@b.com>"))
        val r = MessageResponse("m1", "t1", listOf("INBOX"), null, Payload(headers = headersList), "1719619200000")
        val e = r.toDomainEmail()
        assertEquals("sender@test.com", e.from)
        assertEquals("recipient@test.com", e.to)
        assertEquals("Hello", e.subject)
        assertEquals("<a@b.com>", e.rfcMessageId)
        assertEquals("<r1@b.com>", e.rfcReferences)
    }

    @Test fun blank_headers_produce_defaults() {
        val blankHeaders = listOf(
            Header("From", "   "),
            Header("to", "\t"),
            Header("SUBJECT", "")
        )
        val r = MessageResponse("m1", "t1", listOf("INBOX"), null,
            Payload(headers = blankHeaders), null)
        val e = r.toDomainEmail()
        assertEquals("Unknown", e.from)
        assertEquals("", e.to)
        assertEquals("(no subject)", e.subject)
        assertNull(e.rfcMessageId)
        assertNull(e.rfcReferences)
        assertEquals(0L, e.timestamp)
    }

    @Test fun internalDate_valid_preserved_malformed_or_absent_produces_zero() {
        val r = MessageResponse("m1", "t1", emptyList(), null, null, "1719619200000")
        assertEquals(1719619200000L, r.toDomainEmail().timestamp)
        assertEquals(0L, r.copy(internalDate = "abc").toDomainEmail().timestamp)
        assertEquals(0L, r.copy(internalDate = null).toDomainEmail().timestamp)
    }

    @Test fun labels_preserve_exact_order_case_and_derive_flags() {
        // System labels (uppercase, as Gmail API returns) + custom labels
        val labels = listOf("INBOX", "IMPORTANT", "UNREAD", "STARRED", "TRASH", "Custom_Label")
        val r = MessageResponse("m1", "t1", labels, null, null, "1000")
        val e = r.toDomainEmail()

        // Exact order, case, and values preserved
        assertEquals("preserved exactly", labels, e.labels)
        assertTrue("UNREAD → isRead=false", e.isRead.not())
        assertTrue("STARRED → isStarred=true", e.isStarred)
        assertEquals("TRASH → Trash folder", EmailFolder.Trash, e.folder)

        // null labelIds → defaults
        val rNull = MessageResponse("m1", "t1", null, null, null, "1000")
        val eNull = rNull.toDomainEmail()
        assertEquals("null labels → empty", emptyList<String>(), eNull.labels)
        assertTrue("null labels → isRead", eNull.isRead)
        assertFalse("null labels → not starred", eNull.isStarred)
        assertEquals("null labels → Inbox", EmailFolder.Inbox, eNull.folder)
    }

    @Test fun real_mime_tree_preserves_pdf_metadata_and_flags() {
        // Multipart with nested PDF + non-PDF part
        val pdfPart = Payload(
            headers = listOf(
                Header("Content-Type", "application/pdf"),
                Header("Content-Disposition", "attachment")
            ),
            mimeType = "application/pdf",
            body = MessagePartBody(size = 2048, attachmentId = "att-pdf-1", data = null),
            filename = "report.pdf",
            partId = "part1"
        )
        val nonPdf = Payload(
            mimeType = "text/html",
            body = MessagePartBody(size = 512, data = "PGh0bWw+"),
            filename = null
        )
        val multipart = Payload(
            mimeType = "multipart/mixed",
            parts = listOf(nonPdf, pdfPart)
        )

        val r = MessageResponse("m1", "t1", listOf("INBOX"), null, multipart, "1000")
        val e = r.toDomainEmail()

        assertTrue("pdfMetadataScanned", e.pdfMetadataScanned)
        assertTrue("hasAttachments", e.hasAttachments)
        assertEquals("one PDF", 1, e.pdfAttachments.size)
        assertEquals("report.pdf", e.pdfAttachments[0].fileName)
        assertEquals("att-pdf-1", e.pdfAttachments[0].attachmentId)
        assertEquals(2048L, e.pdfAttachments[0].sizeBytes)

        // Absent payload → unscanned, no attachments
        val rNo = MessageResponse("m1", "t1", null, null, null, "1000")
        val eNo = rNo.toDomainEmail()
        assertFalse("no payload → not scanned", eNo.pdfMetadataScanned)
        assertFalse("no payload → no attachments", eNo.hasAttachments)
        assertTrue("no payload → empty list", eNo.pdfAttachments.isEmpty())
    }

    @Test fun reply_with_previous_refs_appends_parent_msg_id() {
        val e = replyEmail("<m@x.com>", "<p1@x.com> <p2@x.com>")
        val ctx = ReplyContext.from(e)
        assertEquals("t1", ctx.threadId)
        assertEquals("<m@x.com>", ctx.inReplyTo)
        assertEquals("<p1@x.com> <p2@x.com> <m@x.com>", ctx.references)
    }

    @Test fun reply_without_previous_refs_uses_msg_id_as_references() {
        val e = replyEmail("<m@x.com>", null)
        val ctx = ReplyContext.from(e)
        assertEquals("<m@x.com>", ctx.inReplyTo)
        assertEquals("<m@x.com>", ctx.references)
    }

    // ═══════════════════════════════════════════════════════════════
    // 8. Sin Message-ID → thread conservado, RFC headers null
    // ═══════════════════════════════════════════════════════════════

    @Test fun without_message_id_thread_preserved_rfc_headers_null() {
        val e = replyEmail(null, null)
        val ctx = ReplyContext.from(e)
        assertEquals("t1", ctx.threadId)
        assertNull(ctx.inReplyTo)
        assertNull(ctx.references)
    }

    // ═══════════════════════════════════════════════════════════════
    // 9. Real GmailProvider reply carries thread/RFC data without an extra GET
    // ═══════════════════════════════════════════════════════════════

    @Test fun gmailProvider_reply_posts_thread_and_rfc_headers_without_extra_get() = runTest {
        val ctx = ReplyContext.from(replyEmail("<m@x.com>", "<anc@x.com>"))
        val captured = executeSend(replyContext = ctx)

        assertEquals(
            listOf("/users/me/profile", "/users/me/messages/send"),
            captured.paths
        )
        assertEquals("t1", captured.request.threadId)
        assertTrue(captured.mime.contains("In-Reply-To: <m@x.com>\r\n"))
        assertTrue(captured.mime.contains("References: <anc@x.com> <m@x.com>\r\n"))
        assertFalse(captured.mime.contains("gmail-internal"))
    }

    // ═══════════════════════════════════════════════════════════════
    // 10. New email → ReplyContext absent → threadId null in request
    // ═══════════════════════════════════════════════════════════════

    @Test fun gmailProvider_new_email_omits_thread_and_rfc_headers() = runTest {
        val captured = executeSend(replyContext = null)

        assertEquals(
            listOf("/users/me/profile", "/users/me/messages/send"),
            captured.paths
        )
        assertNull(captured.request.threadId)
        assertFalse(captured.mime.contains("In-Reply-To:"))
        assertFalse(captured.mime.contains("References:"))
    }

    // ═══════════════════════════════════════════════════════════════
    // 11. Reply with refs builds correct chain, never uses internal ID
    // ═══════════════════════════════════════════════════════════════

    @Test fun composeViewModel_reply_sends_replyContext_not_internal_id() = runTest(mainDispatcher) {
        val reply = Email("gmail-123", "th1", "from@test.com", "F", "me@t.com",
            "Orig", "", 1000L, false, false, false, emptyList(), EmailFolder.Inbox,
            rfcMessageId = "<o@m.com>", rfcReferences = "<anc@m.com>")
        val source = RecordingComposeEmailSource()
        val viewModel = ComposeViewModel(
            args = ComposeArgs.Reply(reply),
            emailSource = source,
            stringProvider = ComposeTestStringProvider
        )

        testScheduler.advanceUntilIdle()
        viewModel.onBodyChanged("Respuesta")
        viewModel.onSend()
        testScheduler.advanceUntilIdle()

        assertEquals(1, source.sendCallCount)
        val ctx = source.lastReplyContext

        assertEquals("thread from email", "th1", ctx?.threadId)
        assertEquals("inReplyTo = Message-ID", "<o@m.com>", ctx?.inReplyTo)
        assertEquals("references = prev + msgId", "<anc@m.com> <o@m.com>", ctx?.references)
        assertFalse("never uses internal Gmail ID", ctx.toString().contains("gmail-123"))
    }

    // ── helpers ─────────────────────────────────────────────────

    private data class CapturedSend(
        val paths: List<String>,
        val request: SendRequest,
        val mime: String
    )

    private suspend fun executeSend(replyContext: ReplyContext?): CapturedSend {
        val paths = mutableListOf<String>()
        var capturedRequest: SendRequest? = null
        val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        val engine = MockEngine { request ->
            paths += request.url.encodedPath
            when {
                request.url.encodedPath.endsWith("/users/me/profile") -> respond(
                    content = """{"emailAddress":"me@test.com"}""",
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders
                )
                request.url.encodedPath.endsWith("/users/me/messages/send") -> {
                    capturedRequest = Json.decodeFromString(outgoingBodyText(request.body))
                    respond(
                        content = """{"id":"sent-1","threadId":"${replyContext?.threadId ?: "sent-thread"}"}""",
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders
                    )
                }
                else -> error("Unexpected request: ${request.url}")
            }
        }
        val client = HttpClient(engine) {
            expectSuccess = true
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            defaultRequest {
                contentType(ContentType.Application.Json)
            }
        }

        try {
            GmailProvider(client).sendEmail(
                to = "recipient@test.com",
                cc = null,
                bcc = null,
                subject = "Subject",
                body = "Body",
                replyContext = replyContext
            )
        } finally {
            client.close()
        }

        val request = checkNotNull(capturedRequest) { "messages.send request was not captured" }
        val mime = String(java.util.Base64.getUrlDecoder().decode(request.raw), Charsets.UTF_8)
        return CapturedSend(paths.toList(), request, mime)
    }

    private fun outgoingBodyText(body: OutgoingContent): String = when (body) {
        is TextContent -> body.text
        is ByteArrayContent -> body.bytes().decodeToString()
        else -> error("Unsupported outgoing body: ${body::class.qualifiedName}")
    }

    private class RecordingComposeEmailSource : ComposeEmailSource {
        var sendCallCount: Int = 0
            private set
        var lastReplyContext: ReplyContext? = null
            private set

        override suspend fun getUserEmail(): String = "me@test.com"

        override suspend fun sendEmail(
            to: String,
            cc: String?,
            bcc: String?,
            subject: String,
            body: String,
            replyContext: ReplyContext?
        ) {
            sendCallCount++
            lastReplyContext = replyContext
        }
    }

    private object ComposeTestStringProvider : StringProvider {
        override fun getString(resId: Int, vararg formatArgs: Any): String =
            if (formatArgs.isEmpty()) "yyyy-MM-dd" else formatArgs.joinToString(" ")
    }

    private fun replyEmail(msgId: String?, refs: String?) = Email(
        "gmail-internal", "t1", "a@b.com", "A", "c@d.com", "S", "", 1000L,
        false, false, false, emptyList(), EmailFolder.Inbox,
        rfcMessageId = msgId, rfcReferences = refs
    )
}
