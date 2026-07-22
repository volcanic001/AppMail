package com.david.mailapp.core.network

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.david.mailapp.core.auth.AuthManager
import com.david.mailapp.core.auth.FakeOAuthRefreshService
import com.david.mailapp.core.auth.OAuthRefreshResult
import com.david.mailapp.core.auth.OAuthTokenManager
import com.david.mailapp.core.auth.OAuthTokenResult
import com.david.mailapp.core.auth.OAuthTokens
import com.david.mailapp.core.security.FakeSecretCipher
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class HttpClientIntegrationTest {

    private lateinit var tempDir: File
    private lateinit var authManager: AuthManager
    private var currentTime = 1_000_000_000_000L
    private fun now() = currentTime

    @Before
    fun setUp() {
        tempDir = createTempDir("http_client_test_")
        val store = PreferenceDataStoreFactory.create {
            File(tempDir, "prefs.preferences_pb")
        }
        authManager = AuthManager(store, FakeSecretCipher())
        currentTime = 1_000_000_000_000L
    }

    @After
    fun tearDown() { tempDir.deleteRecursively() }

    private fun gmailUrl() = "https://gmail.googleapis.com/gmail/v1/users/me/profile"

    private fun io.ktor.client.engine.mock.MockRequestHandleScope.mockOk() = respond(
        """{"emailAddress":"test@example.com"}""",
        HttpStatusCode.OK,
        headersOf(HttpHeaders.ContentType, "application/json")
    )
    private fun io.ktor.client.engine.mock.MockRequestHandleScope.mock401() = respond(
        """{"error":"unauthorized"}""",
        HttpStatusCode.Unauthorized,
        headersOf(HttpHeaders.ContentType, "application/json")
    )

    // Test 1: Token vigente -> cero renovaciones
    @Test
    fun `fresh token - no refresh and correct Bearer`() = runBlocking {
        authManager.saveTokens(OAuthTokens("access_fresh", "refresh", now() + 600_000L))
        val oauthCalls = AtomicInteger(0)
        val tokenManager = OAuthTokenManager(authManager, object : com.david.mailapp.core.auth.OAuthRefreshService {
            override suspend fun refresh(rt: String): OAuthRefreshResult {
                oauthCalls.incrementAndGet(); return OAuthRefreshResult.Success("new", 3600)
            }
        }, ::now)
        val requestCount = AtomicInteger(0)
        val engine = MockEngine { req ->
            requestCount.incrementAndGet()
            val auth = req.headers[HttpHeaders.Authorization] ?: ""
            assertEquals("Bearer debe ser exactamente Bearer access_fresh", "Bearer access_fresh", auth)
            mockOk()
        }
        HttpClientFactory.createGmailClient(tokenManager, engine).use { client ->
            client.get(gmailUrl())
        }
        assertEquals(0, oauthCalls.get())
        assertEquals(1, requestCount.get())
    }

    // Test 2: (C) Proactive concurrent refresh
    @Test
    fun `ten concurrent requests with stale token - single proactive refresh`() = runBlocking {
        withTimeout(5000L) {
            authManager.saveTokens(OAuthTokens("old_access", "refresh", now() + 60_000L)) // inside 5-min window
            val oauthCalls = AtomicInteger(0)
            val blockRefresh = CompletableDeferred<Unit>()
            val reachedRefresh = CompletableDeferred<Unit>()
            val requestCount = AtomicInteger(0)
            
            val arrivedRequests = AtomicInteger(0)
            val blockRequests = CompletableDeferred<Unit>()

            val tokenManager = OAuthTokenManager(authManager, object : com.david.mailapp.core.auth.OAuthRefreshService {
                override suspend fun refresh(rt: String): OAuthRefreshResult {
                    oauthCalls.incrementAndGet()
                    reachedRefresh.complete(Unit)
                    blockRefresh.await()
                    return OAuthRefreshResult.Success("new_access", 3600)
                }
            }, ::now)
            
            val engine = MockEngine { req ->
                val n = arrivedRequests.incrementAndGet()
                if (n == 10) blockRequests.complete(Unit)
                requestCount.incrementAndGet()
                val auth = req.headers[HttpHeaders.Authorization] ?: ""
                assertEquals("Debe llevar el token nuevo", "Bearer new_access", auth)
                mockOk()
            }
            
            HttpClientFactory.createGmailClient(tokenManager, engine).use { client ->
                coroutineScope {
                    val startBarrier = CompletableDeferred<Unit>()
                    val allReady = CompletableDeferred<Unit>()
                    val readyCount = AtomicInteger(0)
                    val jobs = List(10) { 
                        async { 
                            if (readyCount.incrementAndGet() == 10) {
                                allReady.complete(Unit)
                            }
                            startBarrier.await() 
                            client.get(gmailUrl()) 
                        } 
                    }
                    
                    allReady.await()
                    startBarrier.complete(Unit)
                    reachedRefresh.await()
                    blockRefresh.complete(Unit)
                    
                    blockRequests.await() // Wait for all 10 to arrive at the engine
                    jobs.awaitAll()
                }
            }
            
            assertEquals("Una sola renovación OAuth", 1, oauthCalls.get())
            assertEquals("10 requests Gmail", 10, requestCount.get())
        }
    }

    // Test 3: (B) 401 Response exact validation
    @Test
    fun `401 response triggers exactly one force refresh and two exact requests`() = runBlocking {
        withTimeout(5000L) {
            authManager.saveTokens(OAuthTokens("expired_access", "refresh", now() + 600_000L)) // fresh locally but rejected
            val oauthCalls = AtomicInteger(0)
            val tokenManager = OAuthTokenManager(authManager, object : com.david.mailapp.core.auth.OAuthRefreshService {
                override suspend fun refresh(rt: String): OAuthRefreshResult {
                    oauthCalls.incrementAndGet(); return OAuthRefreshResult.Success("new_access", 3600)
                }
            }, ::now)
            val requestCount = AtomicInteger(0)
            val engine = MockEngine { req ->
                val n = requestCount.incrementAndGet()
                val auth = req.headers[HttpHeaders.Authorization] ?: ""
                if (n == 1) {
                    assertEquals("Primer envio lleva token expirado", "Bearer expired_access", auth)
                    mock401()
                } else {
                    assertEquals("Segundo envio lleva token nuevo", "Bearer new_access", auth)
                    mockOk()
                }
            }
            HttpClientFactory.createGmailClient(tokenManager, engine).use { client ->
                val resp = client.get(gmailUrl())
                assertEquals(HttpStatusCode.OK, resp.status)
            }
            assertEquals("Exactamente 2 envíos totales", 2, requestCount.get())
            assertEquals("Exactamente 1 renovación OAuth", 1, oauthCalls.get())
        }
    }

    // Test 4: (E) Concurrent 401 / forceRefresh
    @Test
    fun `concurrent 401 rejections - no duplicate oauth refresh`() = runBlocking {
        withTimeout(5000L) {
            authManager.saveTokens(OAuthTokens("expired_access", "refresh", now() + 600_000L))
            val oauthCalls = AtomicInteger(0)
            val blockRefresh = CompletableDeferred<Unit>()
            val reachedRefresh = CompletableDeferred<Unit>()

            val tokenManager = OAuthTokenManager(authManager, object : com.david.mailapp.core.auth.OAuthRefreshService {
                override suspend fun refresh(rt: String): OAuthRefreshResult {
                    oauthCalls.incrementAndGet()
                    reachedRefresh.complete(Unit)
                    blockRefresh.await()
                    return OAuthRefreshResult.Success("new_access", 3600)
                }
            }, ::now)
            
            val requestCount = AtomicInteger(0)
            val failedRequests = AtomicInteger(0)
            val allFailedRequestsArrived = CompletableDeferred<Unit>()
            
            val engine = MockEngine { req ->
                requestCount.incrementAndGet()
                val auth = req.headers[HttpHeaders.Authorization] ?: ""
                if (auth == "Bearer expired_access") {
                    val count = failedRequests.incrementAndGet()
                    if (count == 10) allFailedRequestsArrived.complete(Unit)
                    mock401()
                } else {
                    assertEquals("Debe ser el token nuevo", "Bearer new_access", auth)
                    mockOk()
                }
            }
            
            HttpClientFactory.createGmailClient(tokenManager, engine).use { client ->
                coroutineScope {
                    val startBarrier = CompletableDeferred<Unit>()
                    val allReady = CompletableDeferred<Unit>()
                    val readyCount = AtomicInteger(0)
                    val jobs = List(10) { 
                        async { 
                            if (readyCount.incrementAndGet() == 10) {
                                allReady.complete(Unit)
                            }
                            startBarrier.await() 
                            client.get(gmailUrl()) 
                        } 
                    }
                    
                    allReady.await()
                    startBarrier.complete(Unit)
                    
                    // Wait until all 10 have attempted and failed with 401
                    allFailedRequestsArrived.await()
                    
                    // Wait until the refresh service is reached by the first one
                    reachedRefresh.await()
                    
                    // Allow the refresh to proceed
                    blockRefresh.complete(Unit)
                    
                    jobs.awaitAll()
                }
            }
            
            assertEquals("Una sola llamada OAuth", 1, oauthCalls.get())
            // Total requests: 10 failed with 401 + 10 retries = 20
            assertEquals("Total requests", 20, requestCount.get())
        }
    }

    // Test 5: (D) Concurrent invalid_grant
    @Test
    fun `concurrent invalid_grant - single oauth call, single event, latch active, credentials preserved`() = runBlocking {
        withTimeout(5000L) {
            authManager.saveTokens(OAuthTokens("stale", "refresh", now() + 60_000L))
            val oauthCalls = AtomicInteger(0)
            val blockRefresh = CompletableDeferred<Unit>()
            val reachedRefresh = CompletableDeferred<Unit>()

            val tokenManager = OAuthTokenManager(authManager, object : com.david.mailapp.core.auth.OAuthRefreshService {
                override suspend fun refresh(rt: String): OAuthRefreshResult {
                    oauthCalls.incrementAndGet()
                    reachedRefresh.complete(Unit)
                    blockRefresh.await()
                    return OAuthRefreshResult.ReauthenticationRequired
                }
            }, ::now)
            
            val eventReceived = CompletableDeferred<Unit>()
            val events = mutableListOf<Unit>()
            val collectJob = launch { 
                tokenManager.reauthenticationEvents.collect { 
                    events.add(it)
                    eventReceived.complete(Unit)
                } 
            }
            
            val requestCount = AtomicInteger(0)
            val engine = MockEngine { requestCount.incrementAndGet(); mockOk() }
            
            HttpClientFactory.createGmailClient(tokenManager, engine).use { client ->
                coroutineScope {
                    val startBarrier = CompletableDeferred<Unit>()
                    val allReady = CompletableDeferred<Unit>()
                    val readyCount = AtomicInteger(0)
                    val jobs = List(10) { 
                        async { 
                            if (readyCount.incrementAndGet() == 10) {
                                allReady.complete(Unit)
                            }
                            startBarrier.await() 
                            runCatching { client.get(gmailUrl()) }
                        } 
                    }
                    
                    allReady.await()
                    startBarrier.complete(Unit)
                    reachedRefresh.await()
                    blockRefresh.complete(Unit)
                    
                    val results = jobs.awaitAll()
                    assertTrue(results.all { it.isFailure })
                }
            }
            
            // Explicitly wait for the event
            eventReceived.await()
            collectJob.cancel()
            
            assertEquals("Cero requests Gmail llegaron a red", 0, requestCount.get())
            assertEquals("Solo una llamada al servicio OAuth", 1, oauthCalls.get())
            assertEquals("Un solo evento de reautenticación", 1, events.size)
            assertTrue("Latch activo", tokenManager.isReauthenticationPending)
            assertNotNull("Credenciales siguen almacenadas", authManager.getTokens())
        }
    }

    // Test 6: (A) External host protections
    @Test
    fun `untrusted external host receives exactly one request without Bearer and triggers no refresh`() = runBlocking {
        withTimeout(5000L) {
            authManager.saveTokens(OAuthTokens("stale", "refresh", now() + 60_000L))
            val oauthCalls = AtomicInteger(0)
            val tokenManager = OAuthTokenManager(authManager, object : com.david.mailapp.core.auth.OAuthRefreshService {
                override suspend fun refresh(rt: String): OAuthRefreshResult {
                    oauthCalls.incrementAndGet(); return OAuthRefreshResult.Success("new", 3600)
                }
            }, ::now)
            
            val requestCount = AtomicInteger(0)
            val engine = MockEngine { req ->
                requestCount.incrementAndGet()
                val auth = req.headers[HttpHeaders.Authorization]
                assertNull("No debe haber header Authorization", auth)
                mockOk()
            }
            
            HttpClientFactory.createGmailClient(tokenManager, engine).use { client ->
                // Case 1: Example.com
                client.get("https://example.com/api/data")
                // Case 2: Spoofed Gmail hostname
                client.get("https://gmail.googleapis.com.evil.example/api/data")
                // Case 3: HTTP instead of HTTPS
                client.get("http://gmail.googleapis.com/api/data")
            }
            
            assertEquals("Exactamente 3 peticiones", 3, requestCount.get())
            assertEquals("Cero renovaciones", 0, oauthCalls.get())
        }
    }

    @Test
    fun `ktor header logging redacts authorization token`() = runBlocking {
        val accessToken = "sensitive_authorization_marker"
        authManager.saveTokens(OAuthTokens(accessToken, "refresh", now() + 600_000L))
        val tokenManager = OAuthTokenManager(
            authManager,
            FakeOAuthRefreshService(emptyList()),
            ::now
        )
        val logMessages = mutableListOf<String>()
        val logger = object : Logger {
            override fun log(message: String) {
                logMessages.add(message)
            }
        }
        val engine = MockEngine { mockOk() }

        HttpClientFactory.createGmailClient(tokenManager, engine, logger).use { client ->
            client.get(gmailUrl())
        }

        val output = logMessages.joinToString("\n")
        assertTrue("El logger debe recibir la traza HTTP en debug", output.isNotEmpty())
        assertTrue(
            "La traza debe conservar el nombre del header para diagnóstico",
            output.contains("Authorization", ignoreCase = true)
        )
        assertFalse("El access token nunca debe aparecer en logs", output.contains(accessToken))
    }
}
