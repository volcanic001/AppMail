package com.david.mailapp.data.remote.provider.gmail

import com.david.mailapp.core.network.HttpClientFactory
import com.david.mailapp.core.perf.MailOpenPerformanceTrace
import com.david.mailapp.data.remote.provider.EmailLookupFailureReason
import com.david.mailapp.data.remote.provider.EmailLookupResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class GmailProviderNetworkRobustnessTest {

    private fun createClient(engine: MockEngine): HttpClient {
        return HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }
    }

    private fun validEmailResponse(id: String) = 
        """{"id":"$id","threadId":"$id","labelIds":[],"snippet":"","internalDate":"0","payload":{"mimeType":"text/plain","body":{"data":""}}}"""

    @Test
    fun `transient errors exhaust at exactly 3 attempts`() = runTest {
        val transients = listOf(
            HttpStatusCode.RequestTimeout, // 408
            HttpStatusCode.TooManyRequests, // 429
            HttpStatusCode.InternalServerError, // 500
            HttpStatusCode(599, "Unknown")
        )

        for (status in transients) {
            var attempts = 0
            val events = mutableListOf<NetworkDiagnosticEvent>()
            
            val engine = MockEngine { request ->
                attempts++
                respond("{}", status)
            }
            
            val provider = GmailProvider(
                client = createClient(engine),
                lookupBackoffMillis = listOf(10, 20),
                lookupDelay = { delay(it) },
                clock = { 100L },
                networkDiagnosticSink = { events.add(it) }
            )
            
            val result = provider.fetchEmailById("test1")
            
            assertEquals(3, attempts)
            assertTrue(result is EmailLookupResult.Failure)
            assertEquals(EmailLookupFailureReason.TEMPORARY_REMOTE, (result as EmailLookupResult.Failure).reason)
            
            // Check diagnostic events
            assertEquals(3, events.size)
            assertTrue(events.all { it.category == DiagnosticCategory.TRANSIENT_HTTP })
            assertEquals(1, events[0].attempt)
            assertEquals(2, events[1].attempt)
            assertEquals(3, events[2].attempt)
            
            val expectedHash = MailOpenPerformanceTrace.mailKey("test1")
            assertTrue(events.all { it.mailKey == expectedHash })
            assertTrue(events.all { it.mailKey != "test1" })
        }
    }

    @Test
    fun `io exceptions exhaust at exactly 3 attempts`() = runTest {
        var attempts = 0
        val events = mutableListOf<NetworkDiagnosticEvent>()
        val engine = MockEngine { request ->
            attempts++
            throw IOException("Network down")
        }
        
        val provider = GmailProvider(
            client = createClient(engine),
            lookupBackoffMillis = listOf(10, 20),
            lookupDelay = { delay(it) },
            clock = { 100L },
            networkDiagnosticSink = { events.add(it) }
        )
        
        val result = provider.fetchEmailById("test2")
        
        assertEquals(3, attempts)
        assertTrue(result is EmailLookupResult.Failure)
        assertEquals(EmailLookupFailureReason.NO_CONNECTION, (result as EmailLookupResult.Failure).reason)
        assertEquals(3, events.size)
        assertTrue(events.all { it.category == DiagnosticCategory.IO })
    }

    @Test
    fun `permanent errors execute single logical request`() = runTest {
        val permanents = listOf(
            HttpStatusCode.BadRequest to EmailLookupFailureReason.REMOTE_REJECTED,
            HttpStatusCode.Forbidden to EmailLookupFailureReason.REMOTE_REJECTED,
            HttpStatusCode.NotFound to null, // Special case
            HttpStatusCode.Unauthorized to EmailLookupFailureReason.SESSION_EXPIRED
        )

        for ((status, expectedReason) in permanents) {
            var attempts = 0
            val events = mutableListOf<NetworkDiagnosticEvent>()
            val engine = MockEngine { request ->
                attempts++
                respond("{}", status)
            }
            
            val provider = GmailProvider(
                client = createClient(engine),
                lookupBackoffMillis = listOf(10, 20),
                lookupDelay = { delay(it) },
                clock = { 100L },
                networkDiagnosticSink = { events.add(it) }
            )
            
            val result = provider.fetchEmailById("test3")
            
            assertEquals(1, attempts) // No retry
            assertEquals(1, events.size) // 1 event
            
            if (status == HttpStatusCode.NotFound) {
                assertTrue(result is EmailLookupResult.NotFound)
                assertEquals(DiagnosticCategory.NOT_FOUND, events[0].category)
            } else {
                assertTrue(result is EmailLookupResult.Failure)
                assertEquals(expectedReason, (result as EmailLookupResult.Failure).reason)
                val expectedCat = if (status == HttpStatusCode.Unauthorized) DiagnosticCategory.SESSION_EXPIRED else DiagnosticCategory.PERMANENT_HTTP
                assertEquals(expectedCat, events[0].category)
            }
        }
    }

    @Test
    fun `invalid json executes single request and maps correctly`() = runTest {
        var attempts = 0
        val events = mutableListOf<NetworkDiagnosticEvent>()
        val engine = MockEngine { request ->
            attempts++
            respond("{ invalid json }", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        
        val provider = GmailProvider(
            client = createClient(engine),
            lookupBackoffMillis = listOf(10, 20),
            lookupDelay = { delay(it) },
            clock = { 100L },
            networkDiagnosticSink = { events.add(it) }
        )
        
        val result = provider.fetchEmailById("test_json")
        assertEquals(1, attempts)
        assertTrue(result is EmailLookupResult.Failure)
        assertEquals(EmailLookupFailureReason.INVALID_RESPONSE, (result as EmailLookupResult.Failure).reason)
        assertEquals(1, events.size)
        assertEquals(DiagnosticCategory.INVALID_RESPONSE, events[0].category)
    }

    @Test
    fun `concurrent lookups with 401 trigger one refresh and two physical requests per email`() = runTest {
        val physicalRequests = AtomicInteger(0)
        val refreshCount = AtomicInteger(0)
        
        val engine = MockEngine { request ->
            val count = physicalRequests.incrementAndGet()
            val authHeader = request.headers[HttpHeaders.Authorization]
            if (authHeader == "Bearer token") {
                respond("{}", HttpStatusCode.Unauthorized)
            } else if (authHeader == "Bearer new_token") {
                val id = request.url.encodedPath.substringAfterLast("/")
                respond(validEmailResponse(id), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                respondError(HttpStatusCode.BadRequest)
            }
        }

        val client = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
            install(Auth) {
                bearer {
                    loadTokens { BearerTokens("token", "refresh") }
                    refreshTokens {
                        refreshCount.incrementAndGet()
                        delay(50)
                        BearerTokens("new_token", "new_refresh")
                    }
                }
            }
        }
        
        val events = mutableListOf<NetworkDiagnosticEvent>()
        val provider = GmailProvider(
            client = client,
            lookupBackoffMillis = listOf(10, 20),
            lookupDelay = { delay(it) },
            clock = { 100L },
            networkDiagnosticSink = { synchronized(events) { events.add(it) } }
        )

        val j1 = async { provider.fetchEmailById("msg1") }
        val j2 = async { provider.fetchEmailById("msg2") }

        val r1 = j1.await()
        val r2 = j2.await()

        assertTrue(r1 is EmailLookupResult.Found)
        assertTrue(r2 is EmailLookupResult.Found)
        
        assertEquals(4, physicalRequests.get())
        // Our outer loop should only see ONE attempt per message because the Auth plugin hides the 401.
        assertEquals(2, events.size)
        assertTrue(events.all { it.attempt == 1 })
        assertTrue(events.all { it.category == DiagnosticCategory.SUCCESS })
    }

    @Test
    fun `failed refresh results in single SESSION_EXPIRED and no outer retries`() = runTest {
        val physicalRequests = AtomicInteger(0)
        val engine = MockEngine { request ->
            physicalRequests.incrementAndGet()
            respond("{}", HttpStatusCode.Unauthorized) // Keep returning 401
        }
        
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; isLenient = true }) }
            install(Auth) {
                bearer {
                    loadTokens { BearerTokens("token", "refresh") }
                    refreshTokens {
                        null // Refresh failed (requires re-auth)
                    }
                }
            }
        }
        
        val events = mutableListOf<NetworkDiagnosticEvent>()
        val provider = GmailProvider(
            client = client,
            networkDiagnosticSink = { events.add(it) }
        )
        
        val result = provider.fetchEmailById("fail_refresh")
        
        // 1 initial 401, then refresh fails (returns null). Ktor will not replay.
        // Outer loop gets 401 and maps to SESSION_EXPIRED. No retries.
        assertEquals(1, physicalRequests.get())
        assertTrue(result is EmailLookupResult.Failure)
        assertEquals(EmailLookupFailureReason.SESSION_EXPIRED, (result as EmailLookupResult.Failure).reason)
        
        assertEquals(1, events.size)
        assertEquals(DiagnosticCategory.SESSION_EXPIRED, events[0].category)
    }
}
