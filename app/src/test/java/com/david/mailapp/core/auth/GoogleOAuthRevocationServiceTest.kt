package com.david.mailapp.core.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.request.forms.FormDataContent
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GoogleOAuthRevocationServiceTest {

    @Test
    fun `revoke sends exact request`() = runBlocking {
        var requestCount = 0
        val mockEngine = MockEngine { request ->
            requestCount++
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("oauth2.googleapis.com", request.url.host)
            assertEquals("/revoke", request.url.encodedPath)
            assertEquals("https", request.url.protocol.name)
            assertTrue(request.url.parameters.isEmpty())
            assertNull(request.headers["Authorization"])

            val body = request.body as FormDataContent
            assertTrue(body.contentType.match(ContentType.Application.FormUrlEncoded))
            assertEquals(setOf("token"), body.formData.names())
            val tokenValues = body.formData.getAll("token")
            assertEquals(1, tokenValues?.size)
            assertEquals("test_refresh_token_value", tokenValues?.first())

            respondOk()
        }

        val service = GoogleOAuthRevocationService(HttpClient(mockEngine))
        service.revoke("test_refresh_token_value")

        assertEquals(1, requestCount)
    }

    @Test
    fun `revoke handles various http status codes normally`() = runBlocking {
        val statuses = listOf(
            HttpStatusCode.OK,
            HttpStatusCode.BadRequest,
            HttpStatusCode.Unauthorized,
            HttpStatusCode.TooManyRequests,
            HttpStatusCode.InternalServerError
        )

        for (status in statuses) {
            val mockEngine = MockEngine {
                respond(
                    content = if (status == HttpStatusCode.BadRequest) "invalid json" else "",
                    status = status,
                    headers = headersOf("Content-Type", "application/json")
                )
            }
            val service = GoogleOAuthRevocationService(HttpClient(mockEngine))

            service.revoke("token_$status")
        }
    }

    @Test
    fun `revoke rejects empty and blank tokens without making request`() = runBlocking {
        var requestCount = 0
        val mockEngine = MockEngine {
            requestCount++
            respondOk()
        }
        val service = GoogleOAuthRevocationService(HttpClient(mockEngine))

        try {
            service.revoke("")
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // Success
        }

        try {
            service.revoke("   ")
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // Success
        }

        assertEquals(0, requestCount)
    }

    @Test
    fun `revoke propagates network exceptions`() = runBlocking {
        val expectedException = object : RuntimeException("Network error") {}
        val mockEngine = MockEngine {
            throw expectedException
        }
        val service = GoogleOAuthRevocationService(HttpClient(mockEngine))

        try {
            service.revoke("test_token")
            fail("Expected RuntimeException")
        } catch (e: RuntimeException) {
            assertSame(expectedException, e)
        }
    }

    @Test
    fun `revoke propagates cancellation exception`() = runBlocking {
        val mockEngine = MockEngine {
            throw CancellationException("Cancelled")
        }
        val service = GoogleOAuthRevocationService(HttpClient(mockEngine))

        try {
            service.revoke("test_token")
            fail("Expected CancellationException")
        } catch (e: CancellationException) {
            assertEquals("Cancelled", e.message)
        }
    }
}
