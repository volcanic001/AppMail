package com.david.mailapp.data.remote.provider.gmail

import com.david.mailapp.data.remote.provider.EmailLookupResult
import com.david.mailapp.domain.model.EmailBodyKind
import com.david.mailapp.domain.model.EmailContentState
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class GmailProviderContentTest {

    private fun createProvider(mockResponse: String): GmailProvider {
        val mockEngine = MockEngine { _ ->
            respond(
                content = mockResponse,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        return GmailProvider(client)
    }

    @Test
    fun `fetchEmailById returns HTML content in Found`() = runTest {
        val htmlBase64 = "PGh0bWw-Ym9keTwvaHRtbD4=" // "<html>body</html>" (URL safe)
        val response = """
        {
            "id": "1",
            "threadId": "t1",
            "payload": {
                "mimeType": "multipart/alternative",
                "parts": [
                    {
                        "mimeType": "text/html",
                        "body": { "data": "$htmlBase64" }
                    }
                ]
            }
        }
        """.trimIndent()

        val provider = createProvider(response)
        val result = provider.fetchEmailById("1") as EmailLookupResult.Found

        assertEquals(EmailContentState.READY, result.email.contentState)
        assertEquals(EmailBodyKind.HTML, result.email.bodyKind)
        assertEquals("<html>body</html>", result.email.body)
    }

    @Test
    fun `fetchEmailById returns plain text fallback in Found`() = runTest {
        val plainBase64 = "aGVsbG8gJmJveQ==" // "hello &boy"
        val response = """
        {
            "id": "2",
            "threadId": "t2",
            "payload": {
                "mimeType": "multipart/alternative",
                "parts": [
                    {
                        "mimeType": "text/plain",
                        "body": { "data": "$plainBase64" }
                    }
                ]
            }
        }
        """.trimIndent()

        val provider = createProvider(response)
        val result = provider.fetchEmailById("2") as EmailLookupResult.Found

        assertEquals(EmailContentState.READY, result.email.contentState)
        assertEquals(EmailBodyKind.PLAIN_TEXT, result.email.bodyKind)
        assertEquals("hello &boy", result.email.body)
    }

    @Test
    fun `fetchEmailById returns EMPTY content in Found`() = runTest {
        val response = """
        {
            "id": "3",
            "threadId": "t3",
            "payload": {
                "mimeType": "text/plain",
                "body": { "data": "" }
            }
        }
        """.trimIndent()

        val provider = createProvider(response)
        val result = provider.fetchEmailById("3") as EmailLookupResult.Found

        assertEquals(EmailContentState.EMPTY, result.email.contentState)
        assertEquals(EmailBodyKind.UNKNOWN, result.email.bodyKind)
        assertEquals(true, result.email.body.isBlank())
    }
}
