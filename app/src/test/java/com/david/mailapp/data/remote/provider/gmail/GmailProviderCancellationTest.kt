package com.david.mailapp.data.remote.provider.gmail

import com.david.mailapp.data.remote.provider.InlineImageRef
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Pruebas de cancelación para GmailProvider — Tarea 3.1-A.
 *
 * Verifica que los métodos reales en GmailProvider propagan
 * CancellationException en lugar de convertirla a null.
 * Instancia un GmailProvider productivo usando un HttpClient(MockEngine)
 * que inyecta cancelaciones en el nivel HTTP.
 */
class GmailProviderCancellationTest {

    private val h = headersOf(HttpHeaders.ContentType, "application/json")

    private fun client(handler: (String) -> Unit): HttpClient {
        val engine = MockEngine { req ->
            handler(req.url.encodedPath)
            // If handler doesn't throw, return generic error (shouldn't happen in these tests)
            respond("{}", HttpStatusCode.InternalServerError, h)
        }
        return HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }

    // ── fetchBodyWithRefs ─────────────────────────────────────────

    @Test
    fun `fetchBodyWithRefs propagates CancellationException and does not return null`() = runTest {
        val sentinel = CancellationException("sentinel-body")
        val c = client { path ->
            if (path.contains("/messages/")) throw sentinel
        }
        val provider = GmailProvider(c)

        try {
            provider.fetchBodyWithRefs("msg_1")
            fail("Expected CancellationException to propagate")
        } catch (e: CancellationException) {
            assertEquals("sentinel-body", e.message?.substringAfterLast(": ")?.trim())
        }
    }

    @Test
    fun `fetchBodyWithRefs returns null for ordinary HTTP errors`() = runTest {
        val c = client {
            throw RuntimeException("ordinary-error")
        }
        val provider = GmailProvider(c)
        val result = provider.fetchBodyWithRefs("msg_1")
        assertNull("Ordinary errors should still produce null", result)
    }

    // ── downloadInlineImages ───────────────────────────────────────

    @Test
    fun `downloadInlineImages propagates CancellationException`() = runTest {
        val sentinel = CancellationException("sentinel-inline")
        val c = client { path ->
            if (path.contains("/attachments/")) throw sentinel
        }
        val provider = GmailProvider(c)

        val ref = InlineImageRef(contentId = "cid", attachmentId = "att1", mimeType = "image/png")
        try {
            provider.downloadInlineImages("msg_1", listOf(ref))
            fail("Expected CancellationException to propagate")
        } catch (e: CancellationException) {
            assertEquals("sentinel-inline", e.message?.substringAfterLast(": ")?.trim())
        }
    }

    @Test
    fun `downloadInlineImages returns partial map on ordinary error`() = runTest {
        val c = client {
            throw RuntimeException("ordinary-error")
        }
        val provider = GmailProvider(c)
        val ref = InlineImageRef(contentId = "cid", attachmentId = "att1", mimeType = "image/png")
        val result = provider.downloadInlineImages("msg_1", listOf(ref))
        assertTrue("Ordinary errors should skip the image (empty map)", result.isEmpty())
    }

    // ── getUserEmail ──────────────────────────────────────────────

    @Test
    fun `getUserEmail propagates CancellationException and does not return null`() = runTest {
        val sentinel = CancellationException("sentinel-profile")
        val c = client { path ->
            if (path.contains("/users/me/profile")) throw sentinel
        }
        val provider = GmailProvider(c)

        try {
            provider.getUserEmail()
            fail("Expected CancellationException to propagate")
        } catch (e: CancellationException) {
            assertEquals("sentinel-profile", e.message?.substringAfterLast(": ")?.trim())
        }
    }

    @Test
    fun `getUserEmail returns null for ordinary HTTP errors`() = runTest {
        val c = client {
            throw RuntimeException("ordinary-error")
        }
        val provider = GmailProvider(c)
        val result = provider.getUserEmail()
        assertNull("Ordinary errors should still produce null", result)
    }
}
