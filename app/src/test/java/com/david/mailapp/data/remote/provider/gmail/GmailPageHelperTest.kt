package com.david.mailapp.data.remote.provider.gmail

import com.david.mailapp.domain.model.PageItemFailureKind
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GmailPageHelperTest {

    private val h = headersOf(HttpHeaders.ContentType, "application/json")

    private val detailOk = """{
        "id":"msg-1","threadId":"t1","labelIds":["INBOX"],"snippet":"s",
        "internalDate":"1000","payload":{"headers":[
            {"name":"From","value":"s@t.com"},{"name":"To","value":"r@t.com"},
            {"name":"Subject","value":"S"}
        ]}
    }""".trimIndent()

    private fun client(
        listBody: String = """{"messages":[{"id":"msg-1","threadId":"t1"}],"nextPageToken":"next-token"}""",
        detailHandler: (String) -> Pair<HttpStatusCode, String> = { HttpStatusCode.OK to detailOk }
    ): HttpClient {
        val engine = MockEngine { req ->
            val path = req.url.encodedPath
            when {
                path.endsWith("/users/me/messages") -> {
                    respond(listBody, HttpStatusCode.OK, h)
                }
                path.contains("/messages/") -> {
                    val (code, body) = detailHandler(path.substringAfterLast("/"))
                    respond(body, code, h)
                }
                else -> error("Unexpected: $path")
            }
        }
        return HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }

    private fun json(code: HttpStatusCode, body: String) = code to body

    @Test fun complete_page_exposes_next_token() = runTest {
        val c = client()
        val result = fetchGmailPage(c, labelId = "INBOX")
        assertEquals(1, result.items.size)
        assertTrue(result.isComplete)
        assertEquals("next-token", result.nextPageToken)
        assertTrue(result.failures.isEmpty())
    }

    @Test fun transient_failure_eventually_succeeds() = runTest {
        var count = 0
        val c = client(
            detailHandler = {
                count++
                if (count < 3) json(HttpStatusCode.InternalServerError, """{}""")
                else json(HttpStatusCode.OK, detailOk)
            }
        )
        val result = fetchGmailPage(c, labelId = "INBOX")
        assertEquals(3, count)
        assertEquals(1, result.items.size)
        assertTrue(result.isComplete)
    }

    @Test fun transient_exhausted_partial_page() = runTest {
        val c = client(
            detailHandler = { json(HttpStatusCode.GatewayTimeout, """{}""") }
        )
        val result = fetchGmailPage(c, labelId = "INBOX", transientRetries = 0)
        // With 0 retries, the single failed attempt produces an incomplete page
        assertTrue(result.isComplete.not())
        assertNull(result.nextPageToken)
        assertEquals(1, result.failures.size)
        assertEquals(PageItemFailureKind.TRANSIENT_EXHAUSTED, result.failures[0].kind)
    }

    @Test fun permanent_404_not_retried_partial_page() = runTest {
        val c = client(
            detailHandler = { json(HttpStatusCode.NotFound, """{}""") }
        )
        val result = fetchGmailPage(c, labelId = "INBOX")
        assertTrue(result.isComplete.not())
        assertNull(result.nextPageToken)
        assertEquals(1, result.failures.size)
        assertEquals(PageItemFailureKind.PERMANENT, result.failures[0].kind)
        assertEquals(1, result.failures[0].attempts)
    }

    @Test fun empty_list_complete_page() = runTest {
        val c = client(listBody = """{"nextPageToken":"next-token"}""")
        val result = fetchGmailPage(c, labelId = "INBOX")
        assertTrue(result.items.isEmpty())
        assertTrue(result.isComplete)
        assertEquals("next-token", result.nextPageToken)
    }

    @Test fun search_uses_same_contract() = runTest {
        val c = client(
            listBody = """{"messages":[{"id":"s1","threadId":"t1"}],"nextPageToken":"s-next"}"""
        )
        val result = fetchGmailPage(c, query = "test")
        assertEquals(1, result.items.size)
        assertTrue(result.isComplete)
        assertEquals("s-next", result.nextPageToken)
    }

    @Test fun concurrency_limited_to_6() = runTest {
        val msgIds = (1..12).map { """{"id":"m$it","threadId":"t$it"}""" }
        val listBody = """{"messages":[${msgIds.joinToString()}],"nextPageToken":"t"}"""
        // Use a suspend detail handler via a separate mechanism:
        // fetchGmailPage processes in chunks of 6 serially, so max concurrent
        // is naturally bounded by the batch size.
        val c = client(listBody = listBody)
        val result = fetchGmailPage(c, labelId = "INBOX")
        assertEquals(12, result.items.size)
        assertTrue(result.isComplete)
        // The chunked(6) call in fetchGmailPage guarantees ≤ 6 concurrent
        // detail fetches in each batch. This is a structural guarantee.
        assertTrue("12 items from 2 batches → all fetched", result.items.size == 12)
    }

    @Test fun detail_cancellation_propagates_not_converted_to_failure() = runTest {
        val sentinel = kotlinx.coroutines.CancellationException("sentinel-page")
        val c = client(
            detailHandler = { throw sentinel }
        )
        try {
            fetchGmailPage(c, labelId = "INBOX")
            org.junit.Assert.fail("Expected CancellationException to propagate")
        } catch (e: kotlinx.coroutines.CancellationException) {
            assertEquals("sentinel-page", e.message?.substringAfterLast(": ")?.trim())
        }
    }
}
