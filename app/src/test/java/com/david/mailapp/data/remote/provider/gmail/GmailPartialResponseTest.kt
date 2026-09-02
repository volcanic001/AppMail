package com.david.mailapp.data.remote.provider.gmail

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

class GmailPartialResponseTest {

    private fun createClient(engine: MockEngine): HttpClient {
        return HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true; prettyPrint = false })
            }
        }
    }

    @Test
    fun `messages_list requests exact fields projection`() = runTest {
        var requestedFields: String? = null
        val engine = MockEngine { request ->
            requestedFields = request.url.parameters["fields"]
            respond("""{"messages":[]}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }

        fetchGmailPage(createClient(engine), labelId = "INBOX", maxResults = 10)
        
        assertEquals(GmailProjections.LIST_FIELDS, requestedFields)
    }

    @Test
    fun `messages_get full requests exact fields projection`() = runTest {
        var requestedFields: String? = null
        var requestedFormat: String? = null
        val engine = MockEngine { request ->
            requestedFields = request.url.parameters["fields"]
            requestedFormat = request.url.parameters["format"]
            
            respond("""{"id":"m1","threadId":"t1"}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }

        fetchWithRetry(createClient(engine), messageId = "m1", maxAttempts = 1)
        
        assertEquals(GmailProjections.FULL_MESSAGE_FIELDS, requestedFields)
        assertEquals("full", requestedFormat)
    }
}
