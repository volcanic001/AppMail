package com.david.mailapp.data.remote.provider.gmail

import com.david.mailapp.core.network.OAuthSessionExpiredException
import com.david.mailapp.data.remote.provider.EmailLookupFailureReason
import com.david.mailapp.data.remote.provider.EmailLookupResult
import com.david.mailapp.domain.model.EmailFolder
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Subfase 1 — contrato de recuperación individual.
 *
 * Cubre el contrato fetchEmailById de GmailProvider: petición correcta,
 * clasificación de resultados, política de reintentos, respuesta inválida
 * y propagación de cancelación.
 */
class GmailFetchEmailByIdTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    // ── helpers ────────────────────────────────────────────────

    private fun jsonStringArray(values: List<String>): String =
        values.joinToString(",", prefix = "[", postfix = "]") { "\"$it\"" }

    private val pdfPart = """
        {"mimeType":"application/pdf","filename":"report.pdf","partId":"part1",
         "headers":[{"name":"Content-Type","value":"application/pdf"},
                    {"name":"Content-Disposition","value":"attachment"}],
         "body":{"size":2048,"attachmentId":"att-pdf-1"}}
    """.trimIndent()

    private fun detailOk(labelIds: List<String> = listOf("INBOX")) = """
        {
          "id": "m1",
          "threadId": "t1",
          "labelIds": ${jsonStringArray(labelIds)},
          "snippet": "snippet-m1",
          "internalDate": "1719619200000",
          "payload": {
            "mimeType": "multipart/mixed",
            "headers": [
              {"name":"From","value":"sender@test.com"},
              {"name":"To","value":"recipient@test.com"},
              {"name":"Subject","value":"Subject m1"},
              {"name":"Message-ID","value":"<mid@test.com>"},
              {"name":"References","value":"<ref@test.com>"}
            ],
            "parts": [$pdfPart]
          }
        }
    """.trimIndent()

    /**
     * Scripted mock engine: each fetch attempt consumes the next queued
     * response; IOException failures and throwables can be injected too.
     */
    private inner class ScriptedEngine {
        val requests = mutableListOf<Pair<String, Parameters>>()
        private val responses = ArrayDeque<Pair<HttpStatusCode, String>>()
        var ioFailuresRemaining = 0
        var throwOnRequest: (() -> Throwable)? = null

        fun enqueue(status: HttpStatusCode, body: String) {
            responses.addLast(status to body)
        }

        fun client(): HttpClient {
            val engine = MockEngine { req ->
                requests += req.url.encodedPath to req.url.parameters
                throwOnRequest?.let { throw it() }
                if (ioFailuresRemaining > 0) {
                    ioFailuresRemaining--
                    throw IOException("connection reset")
                }
                val (status, body) = responses.removeFirst()
                respond(body, status, jsonHeaders)
            }
            return HttpClient(engine) {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true })
                }
            }
        }
    }

    private fun provider(client: HttpClient): GmailProvider =
        GmailProvider(client, lookupBackoffMillis = emptyList())

    private fun EmailLookupResult.failureReason(): EmailLookupFailureReason =
        (this as EmailLookupResult.Failure).reason

    // ── petición correcta ──────────────────────────────────────

    @Test
    fun request_uses_get_with_id_and_format_full() = runTest {
        val engine = ScriptedEngine()
        engine.enqueue(HttpStatusCode.OK, detailOk())
        val result = provider(engine.client()).fetchEmailById("m1")

        assertTrue(result is EmailLookupResult.Found)
        val (path, params) = engine.requests.single()
        assertEquals("/users/me/messages/m1", path)
        assertEquals("full", params["format"])
    }

    @Test
    fun found_preserves_identity_metadata_and_read_state() = runTest {
        val engine = ScriptedEngine()
        engine.enqueue(
            HttpStatusCode.OK,
            detailOk(labelIds = listOf("INBOX", "UNREAD", "STARRED", "Custom_Label"))
        )
        val result = provider(engine.client()).fetchEmailById("m1")
        val email = (result as EmailLookupResult.Found).email

        assertEquals("m1", email.id)
        assertEquals("t1", email.threadId)
        assertEquals("sender@test.com", email.from)
        assertEquals("recipient@test.com", email.to)
        assertEquals("Subject m1", email.subject)
        assertEquals("snippet-m1", email.snippet)
        assertEquals(1719619200000L, email.timestamp)
        assertEquals(listOf("INBOX", "UNREAD", "STARRED", "Custom_Label"), email.labels)
        assertEquals("UNREAD present → unread", false, email.isRead)
        assertTrue(email.isStarred)
        assertEquals(EmailFolder.Inbox, email.folder)
        assertEquals("<mid@test.com>", email.rfcMessageId)
        assertEquals("<ref@test.com>", email.rfcReferences)
        assertTrue(email.pdfMetadataScanned)
        assertTrue(email.hasAttachments)
        assertEquals(1, email.pdfAttachments.size)
        assertEquals("report.pdf", email.pdfAttachments[0].fileName)
        assertEquals("att-pdf-1", email.pdfAttachments[0].attachmentId)
        assertEquals(2048L, email.pdfAttachments[0].sizeBytes)
    }

    // ── clasificación independiente ────────────────────────────

    @Test
    fun received_classified_as_inbox() = runTest {
        val engine = ScriptedEngine()
        engine.enqueue(HttpStatusCode.OK, detailOk(labelIds = listOf("INBOX", "IMPORTANT")))
        val email = (provider(engine.client()).fetchEmailById("m1") as EmailLookupResult.Found).email
        assertEquals(EmailFolder.Inbox, email.folder)
    }

    @Test
    fun trashed_classified_as_trash() = runTest {
        val engine = ScriptedEngine()
        engine.enqueue(HttpStatusCode.OK, detailOk(labelIds = listOf("TRASH")))
        val email = (provider(engine.client()).fetchEmailById("m1") as EmailLookupResult.Found).email
        assertEquals(EmailFolder.Trash, email.folder)
    }

    @Test
    fun sent_classified_as_other() = runTest {
        val engine = ScriptedEngine()
        engine.enqueue(HttpStatusCode.OK, detailOk(labelIds = listOf("SENT", "UNREAD")))
        val email = (provider(engine.client()).fetchEmailById("m1") as EmailLookupResult.Found).email
        assertEquals(EmailFolder.Other, email.folder)
    }

    @Test
    fun archived_without_inbox_classified_as_other() = runTest {
        val engine = ScriptedEngine()
        engine.enqueue(HttpStatusCode.OK, detailOk(labelIds = listOf("ARCHIVE")))
        val email = (provider(engine.client()).fetchEmailById("m1") as EmailLookupResult.Found).email
        assertEquals(EmailFolder.Other, email.folder)
    }

    @Test
    fun trash_and_inbox_classified_as_trash() = runTest {
        val engine = ScriptedEngine()
        engine.enqueue(HttpStatusCode.OK, detailOk(labelIds = listOf("INBOX", "TRASH")))
        val email = (provider(engine.client()).fetchEmailById("m1") as EmailLookupResult.Found).email
        assertEquals(EmailFolder.Trash, email.folder)
    }

    @Test
    fun trash_and_sent_classified_as_trash() = runTest {
        val engine = ScriptedEngine()
        engine.enqueue(HttpStatusCode.OK, detailOk(labelIds = listOf("TRASH", "SENT")))
        val email = (provider(engine.client()).fetchEmailById("m1") as EmailLookupResult.Found).email
        assertEquals(EmailFolder.Trash, email.folder)
        assertEquals(listOf("TRASH", "SENT"), email.labels)
    }

    // ── resultados de error sin reintento ──────────────────────

    @Test
    fun notFound_is_returned_and_not_retried() = runTest {
        val engine = ScriptedEngine()
        engine.enqueue(HttpStatusCode.NotFound, "{}")
        val result = provider(engine.client()).fetchEmailById("m1")

        assertEquals(EmailLookupResult.NotFound, result)
        assertEquals(1, engine.requests.size)
    }

    @Test
    fun unauthorized_is_sessionExpired_and_not_retried() = runTest {
        val engine = ScriptedEngine()
        engine.enqueue(HttpStatusCode.Unauthorized, "{}")
        val result = provider(engine.client()).fetchEmailById("m1")

        assertEquals(EmailLookupFailureReason.SESSION_EXPIRED, result.failureReason())
        assertEquals(1, engine.requests.size)
    }

    @Test
    fun other4xx_are_remoteRejected_and_not_retried() = runTest {
        for (status in listOf(
            HttpStatusCode.BadRequest,
            HttpStatusCode.Forbidden,
            HttpStatusCode.MethodNotAllowed
        )) {
            val engine = ScriptedEngine()
            engine.enqueue(status, "{}")
            val result = provider(engine.client()).fetchEmailById("m1")

            assertEquals("$status → REMOTE_REJECTED", EmailLookupFailureReason.REMOTE_REJECTED, result.failureReason())
            assertEquals("$status must not be retried", 1, engine.requests.size)
        }
    }

    @Test
    fun oauthSessionExpired_maps_to_sessionExpired_without_retry() = runTest {
        val engine = ScriptedEngine()
        engine.throwOnRequest = { OAuthSessionExpiredException("token expired") }
        val result = provider(engine.client()).fetchEmailById("m1")

        assertEquals(EmailLookupFailureReason.SESSION_EXPIRED, result.failureReason())
        assertEquals(1, engine.requests.size)
    }

    // ── reintentos transitorios ────────────────────────────────

    @Test
    fun transientStatuses_recover_when_later_attempt_succeeds() = runTest {
        for (status in listOf(
            HttpStatusCode.RequestTimeout,
            HttpStatusCode.TooManyRequests,
            HttpStatusCode.InternalServerError,
            HttpStatusCode.BadGateway,
            HttpStatusCode.ServiceUnavailable
        )) {
            val engine = ScriptedEngine()
            engine.enqueue(status, "{}")
            engine.enqueue(HttpStatusCode.OK, detailOk())
            val result = provider(engine.client()).fetchEmailById("m1")

            assertTrue("$status recovers on second attempt", result is EmailLookupResult.Found)
            assertEquals("$status → 2 attempts", 2, engine.requests.size)
        }
    }

    @Test
    fun transientStatuses_exhausted_produce_temporaryRemote() = runTest {
        val engine = ScriptedEngine()
        repeat(3) { engine.enqueue(HttpStatusCode.InternalServerError, "{}") }
        val result = provider(engine.client()).fetchEmailById("m1")

        assertEquals(EmailLookupFailureReason.TEMPORARY_REMOTE, result.failureReason())
        assertEquals("initial + 2 retries", 3, engine.requests.size)
    }

    @Test
    fun ioException_recovers_when_later_attempt_succeeds() = runTest {
        val engine = ScriptedEngine()
        engine.ioFailuresRemaining = 1
        engine.enqueue(HttpStatusCode.OK, detailOk())
        val result = provider(engine.client()).fetchEmailById("m1")

        assertTrue(result is EmailLookupResult.Found)
        assertEquals(2, engine.requests.size)
    }

    @Test
    fun ioException_exhausted_produces_noConnection() = runTest {
        val engine = ScriptedEngine()
        engine.ioFailuresRemaining = 3
        val result = provider(engine.client()).fetchEmailById("m1")

        assertEquals(EmailLookupFailureReason.NO_CONNECTION, result.failureReason())
        assertEquals("initial + 2 retries", 3, engine.requests.size)
    }

    @Test
    fun retries_use_250ms_and_750ms_backoff_by_default() = runTest {
        val delays = mutableListOf<Long>()
        val engine = ScriptedEngine()
        engine.enqueue(HttpStatusCode.InternalServerError, "{}")
        engine.enqueue(HttpStatusCode.InternalServerError, "{}")
        engine.enqueue(HttpStatusCode.OK, detailOk())
        val provider = GmailProvider(engine.client(), lookupDelay = { delays += it })
        val result = provider.fetchEmailById("m1")

        assertTrue(result is EmailLookupResult.Found)
        assertEquals(listOf(250L, 750L), delays)
    }

    // ── respuesta inválida ─────────────────────────────────────

    @Test
    fun invalidJson_on200_produces_invalidResponse_without_retry() = runTest {
        val engine = ScriptedEngine()
        engine.enqueue(HttpStatusCode.OK, "not-json{")
        val result = provider(engine.client()).fetchEmailById("m1")

        assertEquals(EmailLookupFailureReason.INVALID_RESPONSE, result.failureReason())
        assertEquals("parse errors are never retried", 1, engine.requests.size)
    }

    // ── id vacío ───────────────────────────────────────────────

    @Test
    fun blankId_produces_invalidResponse_without_any_request() = runTest {
        for (blank in listOf("", "   ")) {
            val engine = ScriptedEngine()
            engine.throwOnRequest = { error("No request must be made for a blank id") }
            val result = provider(engine.client()).fetchEmailById(blank)

            assertEquals("'$blank' → INVALID_RESPONSE", EmailLookupFailureReason.INVALID_RESPONSE, result.failureReason())
            assertEquals("'$blank' → no network access", 0, engine.requests.size)
        }
    }

    // ── cancelación ────────────────────────────────────────────

    @Test
    fun cancellation_propagates_and_is_not_converted_to_failure() = runTest {
        val sentinel = CancellationException("sentinel-lookup")
        val engine = ScriptedEngine()
        engine.throwOnRequest = { sentinel }

        try {
            provider(engine.client()).fetchEmailById("m1")
            fail("Expected CancellationException to propagate")
        } catch (e: CancellationException) {
            assertEquals("sentinel-lookup", e.message?.substringAfterLast(": ")?.trim())
        }
    }
}
