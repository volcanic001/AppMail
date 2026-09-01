package com.david.mailapp.data.remote.provider.gmail

import com.david.mailapp.data.remote.provider.BodyFetchResult
import com.david.mailapp.domain.model.EmailBodyKind
import com.david.mailapp.domain.model.EmailContentState
import com.david.mailapp.domain.model.EmailInlineReference
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
import org.junit.Assert.fail
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
    fun `fetchBodyWithRefs parses text_html as HTML and READY`() = runTest {
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
        val result = provider.fetchBodyWithRefs("1")

        assertEquals(EmailContentState.READY, result?.contentState)
        assertEquals(EmailBodyKind.HTML, result?.bodyKind)
        assertEquals("<html>body</html>", result?.rawBody)
    }

    @Test
    fun `fetchBodyWithRefs parses text_plain fallback as PLAIN_TEXT and READY`() = runTest {
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
        val result = provider.fetchBodyWithRefs("2")

        assertEquals(EmailContentState.READY, result?.contentState)
        assertEquals(EmailBodyKind.PLAIN_TEXT, result?.bodyKind)
        assertEquals("<pre style=\"white-space: pre-wrap; font-family: inherit; margin: 0;\">hello &amp;boy</pre>", result?.rawBody)
    }

    @Test
    fun `fetchBodyWithRefs produces EMPTY and UNKNOWN for empty result`() = runTest {
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
        val result = provider.fetchBodyWithRefs("3")

        assertEquals(EmailContentState.EMPTY, result?.contentState)
        assertEquals(EmailBodyKind.UNKNOWN, result?.bodyKind)
        assertEquals(true, result?.rawBody.isNullOrBlank())
    }

    @Test
    fun `BodyFetchResult rejects inconsistent content contracts`() {
        assertInvalidResult {
            BodyFetchResult(
                rawBody = "<p>body</p>",
                contentState = EmailContentState.EMPTY,
                bodyKind = EmailBodyKind.UNKNOWN
            )
        }
        assertInvalidResult {
            BodyFetchResult(
                rawBody = "",
                contentState = EmailContentState.READY,
                bodyKind = EmailBodyKind.HTML
            )
        }
        assertInvalidResult {
            BodyFetchResult(
                rawBody = null,
                contentState = EmailContentState.EMPTY,
                bodyKind = EmailBodyKind.UNKNOWN,
                inlineRefs = listOf(EmailInlineReference("cid", "attachment", "image/png"))
            )
        }
        assertInvalidResult {
            BodyFetchResult(
                rawBody = null,
                contentState = EmailContentState.NOT_FETCHED,
                bodyKind = EmailBodyKind.UNKNOWN
            )
        }
    }

    private fun assertInvalidResult(block: () -> Unit) {
        try {
            block()
            fail("Expected an invalid content contract to be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }
}
