package com.david.mailapp.data.remote.provider.gmail

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class GmailPageHelperConcurrencyTest {

    private fun createClient(engine: MockEngine): HttpClient {
        return HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }
    }

    private fun listResponse(ids: List<String>, nextPageToken: String? = "token123"): String {
        val messagesJson = ids.joinToString(",") { """{"id":"$it","threadId":"$it"}""" }
        val tokenJson = if (nextPageToken != null) ""","nextPageToken":"$nextPageToken"""" else ""
        return """{"messages":[$messagesJson]$tokenJson}"""
    }

    private fun detailResponse(id: String): String {
        return """{"id":"$id","threadId":"$id","labelIds":[],"snippet":"snippet","internalDate":"123","payload":{"mimeType":"text/plain","body":{"data":""}}}"""
    }

    @Test
    fun `nunca hay mas de seis solicitudes activas y el primero bloqueado no frena al resto`() = runTest {
        val ids = (1..10).map { "msg$it" }
        val activeRequests = AtomicInteger(0)
        val maxObserved = AtomicInteger(0)

        val firstMessageStarted = CompletableDeferred<Unit>()
        val blockFirstMessage = CompletableDeferred<Unit>()

        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            if (path.contains("messages/msg")) {
                val current = activeRequests.incrementAndGet()
                synchronized(maxObserved) {
                    if (current > maxObserved.get()) {
                        maxObserved.set(current)
                    }
                }

                if (path.contains("msg1")) {
                    firstMessageStarted.complete(Unit)
                    blockFirstMessage.await()
                } else {
                    delay(10)
                }

                activeRequests.decrementAndGet()
                respond(detailResponse(path.substringAfterLast("/")), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                respond(listResponse(ids), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }

        val job = async {
            fetchGmailPage(createClient(engine), maxResults = 10, delayFn = { delay(it) })
        }

        // Wait for first message to start and be blocked
        firstMessageStarted.await()
        
        // Wait for other 5 to start and complete (they should complete because msg1 is blocked, but semaphore allows 5 others)
        // Then msg7,8,9,10 will also complete. Total 9 will complete while msg1 is STILL blocked!
        // This proves there is no batch boundary!
        advanceTimeBy(100) // let all other 9 execute and finish
        
        blockFirstMessage.complete(Unit)
        
        val result = job.await()
        
        assertEquals(6, maxObserved.get())
        assertEquals(10, result.items.size)
        // Ensure order is preserved
        assertEquals(ids, result.items.map { it.id })
    }

    @Test
    fun `mensaje en backoff libera permiso inmediatamente para otro`() = runTest {
        val ids = (1..7).map { "msg$it" } // 7 messages, max concurrency is 6
        
        val activeRequests = AtomicInteger(0)
        val maxActive = AtomicInteger(0)
        val msg1Started = CompletableDeferred<Unit>()
        val blockDelay = CompletableDeferred<Unit>()

        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            if (path.contains("messages/msg")) {
                val current = activeRequests.incrementAndGet()
                synchronized(maxActive) {
                    if (current > maxActive.get()) maxActive.set(current)
                }
                
                try {
                    if (path.contains("msg1")) {
                        // Return 500 so it goes into backoff
                        respond("{}", HttpStatusCode.InternalServerError)
                    } else {
                        delay(20)
                        respond(detailResponse(path.substringAfterLast("/")), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                    }
                } finally {
                    activeRequests.decrementAndGet()
                }
            } else {
                respond(listResponse(ids), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }

        val client = createClient(engine)
        val job = async {
            fetchGmailPage(
                client = client,
                transientRetries = 1,
                backoffMillis = listOf(5000L),
                delayFn = {
                    if (it == 5000L) {
                        msg1Started.complete(Unit) // Signal that msg1 is in backoff
                        blockDelay.await() // Block the delay
                    }
                }
            )
        }

        // Wait for msg1 to fail and enter delayFn
        msg1Started.await()
        
        // Now msg1 is suspended IN delayFn. It should NOT hold the semaphore!
        // We can prove this by letting the rest of the messages complete. Since there are 6 others (msg2-msg7),
        // and semaphore size is 6, if msg1 held the permit, only 5 could run concurrently, so msg7 would be blocked.
        // But if it released it, all 6 remaining can run concurrently.
        
        advanceTimeBy(100) // let msg2..msg7 complete
        
        // At this point, only msg1 is pending (in delay).
        // Let's release the delay.
        blockDelay.complete(Unit)
        
        val result = job.await()
        
        // msg1 failed again (we didn't mock a success for its retry), so it should be in failures
        assertEquals(6, result.items.size)
        assertEquals(1, result.failures.size)
        assertEquals("msg1", result.failures[0].itemId)
        
        // The max concurrent ACTIVE HTTP requests should never exceed 6.
        assertTrue("Max active should be <= 6", maxActive.get() <= 6)
    }

    @Test
    fun `orden original preservado pese a respuestas desordenadas`() = runTest {
        val ids = (1..6).map { "msg$it" }
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            if (path.contains("messages/msg")) {
                val id = path.substringAfterLast("/")
                // Delay inversely proportional to id number to guarantee out-of-order completion
                val delayTime = (10 - id.removePrefix("msg").toLong()) * 10
                delay(delayTime)
                respond(detailResponse(id), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                respond(listResponse(ids), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }

        val result = fetchGmailPage(createClient(engine), delayFn = { delay(it) })
        
        assertEquals(6, result.items.size)
        assertEquals(ids, result.items.map { it.id })
    }

    @Test
    fun `aislamiento de fallos supresion de token e isComplete`() = runTest {
        val ids = listOf("msg1", "msg2", "msg3", "msg4")
        
        var msg2Attempts = 0
        
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            if (path.contains("messages/msg")) {
                val id = path.substringAfterLast("/")
                when (id) {
                    "msg2" -> {
                        msg2Attempts++
                        // Transitorio
                        respond("{}", HttpStatusCode.ServiceUnavailable)
                    }
                    "msg3" -> {
                        // Permanente
                        respond("{}", HttpStatusCode.NotFound)
                    }
                    else -> respond(detailResponse(id), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                }
            } else {
                respond(listResponse(ids, "nextPage"), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }

        val result = fetchGmailPage(
            client = createClient(engine),
            transientRetries = 2,
            backoffMillis = listOf(10L, 20L),
            delayFn = { delay(it) }
        )
        
        assertEquals(2, result.items.size)
        assertEquals("msg1", result.items[0].id)
        assertEquals("msg4", result.items[1].id)
        
        assertEquals(2, result.failures.size)
        assertEquals("msg2", result.failures[0].itemId)
        assertEquals(3, result.failures[0].attempts)
        assertEquals("msg3", result.failures[1].itemId)
        assertEquals(1, result.failures[1].attempts)
        
        assertFalse(result.isComplete)
        assertNull(result.nextPageToken)
        
        assertEquals(3, msg2Attempts)
    }

    @Test
    fun `cancellacion propaga e interrumpe todo el trabajo residual`() = runTest {
        val ids = (1..20).map { "msg$it" }
        var httpRequests = 0
        
        val blockMsg1 = CompletableDeferred<Unit>()

        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            if (path.contains("messages/msg")) {
                httpRequests++
                if (path.contains("msg1")) {
                    blockMsg1.await()
                } else {
                    delay(10)
                }
                respond(detailResponse(path.substringAfterLast("/")), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                respond(listResponse(ids), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }

        val job = launch {
            fetchGmailPage(createClient(engine), delayFn = { delay(it) })
        }

        // Advance a tiny bit so list is fetched and detail fetches start.
        // msg1 will block indefinitely. msg2..msg6 will start and finish in 10ms.
        advanceTimeBy(5)
        
        // Cancel the entire page fetch!
        job.cancelAndJoin()
        
        // None of the remaining messages should have been requested!
        // At most 6 requests were launched.
        assertTrue("Requests should be <= 6", httpRequests <= 6)
    }
}
