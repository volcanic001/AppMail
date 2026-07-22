package com.david.mailapp.core.auth

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.david.mailapp.core.security.FakeSecretCipher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tests for [OAuthTokenManager] (Fase 1C.1).
 *
 * Uses [FakeSecretCipher] for encryption and a real DataStore backed by a temp
 * file so token persistence semantics are exercised against a real store.
 */
class OAuthTokenManagerTest {

    private lateinit var tempDir: File
    private lateinit var testStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>
    private lateinit var authManager: AuthManager
    private val sharedCipher = FakeSecretCipher()

    private var currentTimeMillis = 1_000_000_000_000L // fixed "now" for deterministic tests
    private fun advanceTime(ms: Long) { currentTimeMillis += ms }
    private fun now() = currentTimeMillis

    @Before
    fun setUp() {
        tempDir = createTempDir("token_manager_test_")
        testStore = PreferenceDataStoreFactory.create {
            File(tempDir, "test_preferences.preferences_pb")
        }
        authManager = AuthManager(testStore, sharedCipher)
        currentTimeMillis = 1_000_000_000_000L
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    // ── ensureFreshToken ─────────────────────────────────────

    @Test
    fun `no session returns NoSession`() = runBlocking {
        val refreshService = FakeOAuthRefreshService(emptyList())
        val manager = OAuthTokenManager(authManager, refreshService, ::now)

        assertEquals(OAuthTokenResult.NoSession, manager.ensureFreshToken())
        assertEquals(0, refreshService.invocationCount)
    }

    @Test
    fun `valid token more than 5min from expiry returns Available with no refresh`() = runBlocking {
        authManager.saveTokens(OAuthTokens("access", "refresh", now() + 600_000L))
        val refreshService = FakeOAuthRefreshService(emptyList())
        val manager = OAuthTokenManager(authManager, refreshService, ::now)

        val result = manager.ensureFreshToken()
        assertTrue("Must be Available", result is OAuthTokenResult.Available)
        val available = result as OAuthTokenResult.Available
        assertFalse("Must not be refreshed", available.refreshed)
        assertEquals(0, refreshService.invocationCount)
    }

    @Test
    fun `token within 5min window triggers one refresh`() = runBlocking {
        authManager.saveTokens(OAuthTokens("access", "refresh", now() + 60_000L))
        val refreshService = FakeOAuthRefreshService(listOf(
            OAuthRefreshResult.Success("new_access", 3600)
        ))
        val manager = OAuthTokenManager(authManager, refreshService, ::now)

        val result = manager.ensureFreshToken()
        assertTrue("Must be Available after refresh", result is OAuthTokenResult.Available)
        val available = result as OAuthTokenResult.Available
        assertTrue("Must be refreshed", available.refreshed)
        assertEquals("new_access", available.tokens.accessToken)
        assertEquals(1, refreshService.invocationCount)
    }

    @Test
    fun `proactive refresh emits safe lifecycle events without token values`() = runBlocking {
        val accessToken = "sensitive_access_marker"
        val refreshToken = "sensitive_refresh_marker"
        val renewedToken = "sensitive_renewed_marker"
        authManager.saveTokens(OAuthTokens(accessToken, refreshToken, now() + 60_000L))
        val events = mutableListOf<String>()
        val manager = OAuthTokenManager(
            authManager = authManager,
            refreshService = FakeOAuthRefreshService(
                listOf(OAuthRefreshResult.Success(renewedToken, 3600))
            ),
            nowEpochMillis = ::now,
            lifecycleLogger = events::add
        )

        val result = manager.ensureFreshToken()

        assertTrue(result is OAuthTokenResult.Available)
        assertEquals(
            listOf(
                "refresh_started trigger=proactive",
                "refresh_succeeded trigger=proactive"
            ),
            events
        )
        val output = events.joinToString("\n")
        assertFalse(output.contains(accessToken))
        assertFalse(output.contains(refreshToken))
        assertFalse(output.contains(renewedToken))
    }

    @Test
    fun `expired token triggers refresh`() = runBlocking {
        authManager.saveTokens(OAuthTokens("old_access", "refresh", now() - 60_000L))
        val refreshService = FakeOAuthRefreshService(listOf(
            OAuthRefreshResult.Success("fresh_access", 3600)
        ))
        val manager = OAuthTokenManager(authManager, refreshService, ::now)

        val result = manager.ensureFreshToken()
        assertTrue("Must be Available after refresh", result is OAuthTokenResult.Available)
        val available = result as OAuthTokenResult.Available
        assertTrue("Must be refreshed", available.refreshed)
        assertEquals("fresh_access", available.tokens.accessToken)
        assertEquals(1, refreshService.invocationCount)
    }

    @Test
    fun `ten concurrent calls to ensureFreshToken produce one OAuth request`() = runBlocking {
        withTimeout(5_000L) {
            authManager.saveTokens(OAuthTokens("access", "refresh", now() + 60_000L))
            val refreshStarted = CompletableDeferred<Unit>()
            val allCallersReachedManager = CompletableDeferred<Unit>()
            val allowRefreshToFinish = CompletableDeferred<Unit>()
            val invocationCount = AtomicInteger(0)
            val clockCalls = AtomicInteger(0)
            val refreshService = object : OAuthRefreshService {
                override suspend fun refresh(refreshToken: String): OAuthRefreshResult {
                    invocationCount.incrementAndGet()
                    refreshStarted.complete(Unit)
                    allowRefreshToFinish.await()
                    return OAuthRefreshResult.Success("new_access", 3600)
                }
            }
            val manager = OAuthTokenManager(authManager, refreshService) {
                if (clockCalls.incrementAndGet() == 11) {
                    allCallersReachedManager.complete(Unit)
                }
                now()
            }

            coroutineScope {
                val startTogether = CompletableDeferred<Unit>()
                val allReady = CompletableDeferred<Unit>()
                val readyCount = AtomicInteger(0)
                val deferred = List(10) {
                    async {
                        if (readyCount.incrementAndGet() == 10) {
                            allReady.complete(Unit)
                        }
                        startTogether.await()
                        manager.ensureFreshToken()
                    }
                }

                allReady.await()
                startTogether.complete(Unit)
                refreshStarted.await()
                allCallersReachedManager.await()
                allowRefreshToFinish.complete(Unit)

                val results = deferred.awaitAll()
                val available = results.filterIsInstance<OAuthTokenResult.Available>()
                assertEquals("All 10 calls must return Available", 10, available.size)
                assertTrue("All calls must receive the refreshed token", available.all {
                    it.tokens.accessToken == "new_access"
                })
                val refreshedCount = available.count { it.refreshed }
                assertEquals("Exactly 1 call should report refreshed", 1, refreshedCount)
            }

            assertEquals("Exactly 1 OAuth request", 1, invocationCount.get())
        }
    }

    @Test
    fun `GmailAuthClient calculates initial expiry with injected clock`() {
        val manager = OAuthTokenManager(
            authManager,
            FakeOAuthRefreshService(emptyList()),
            ::now
        )
        val client = GmailAuthClient(
            authManager = authManager,
            tokenManager = manager,
            nowEpochMillis = { 42_000L }
        )

        assertEquals(3_642_000L, client.calculateExpiresAtEpochMillis(3600))
    }

    @Test
    fun `transient error before expiry returns current token`() = runBlocking {
        authManager.saveTokens(OAuthTokens("access", "refresh", now() + 120_000L))
        val refreshService = FakeOAuthRefreshService(listOf(
            OAuthRefreshResult.TransientFailure
        ))
        val manager = OAuthTokenManager(authManager, refreshService, ::now)

        val result = manager.ensureFreshToken()
        assertTrue("Must be Available with old token", result is OAuthTokenResult.Available)
        val available = result as OAuthTokenResult.Available
        assertFalse("Must NOT be marked as refreshed", available.refreshed)
        assertEquals("Must return old access token", "access", available.tokens.accessToken)
        assertEquals(1, refreshService.invocationCount)
    }

    @Test
    fun `transient error after expiry returns TemporarilyUnavailable`() = runBlocking {
        authManager.saveTokens(OAuthTokens("expired_access", "refresh", now() - 60_000L))
        val refreshService = FakeOAuthRefreshService(listOf(
            OAuthRefreshResult.TransientFailure
        ))
        val manager = OAuthTokenManager(authManager, refreshService, ::now)

        val result = manager.ensureFreshToken()
        assertEquals(
            "Expired token + transient must be TemporarilyUnavailable",
            OAuthTokenResult.TemporarilyUnavailable,
            result
        )
        assertEquals(1, refreshService.invocationCount)
    }

    @Test
    fun `invalid_grant returns ReauthenticationRequired without deleting tokens`() = runBlocking {
        authManager.saveTokens(OAuthTokens("access", "refresh", now() + 60_000L))
        val refreshService = FakeOAuthRefreshService(listOf(
            OAuthRefreshResult.ReauthenticationRequired
        ))
        val manager = OAuthTokenManager(authManager, refreshService, ::now)

        val result = manager.ensureFreshToken()
        assertEquals(
            "invalid_grant must be ReauthenticationRequired",
            OAuthTokenResult.ReauthenticationRequired,
            result
        )
        // Tokens must still exist
        assertNotNull("Tokens must not be deleted", authManager.getTokens())
        assertEquals(1, refreshService.invocationCount)
    }

    @Test
    fun `empty refresh token returns NoSession`() = runBlocking {
        // Write encrypted v2 payload with empty refreshToken directly (bypasses saveTokens validation)
        val encrypted = sharedCipher.encrypt(
            """{"schemaVersion":2,"accessToken":"access","refreshToken":"","expiresAtEpochMillis":${now() + 600_000L}}""".toByteArray(),
            "mailapp.oauth.tokens.v1".toByteArray()
        )
        testStore.edit { prefs ->
            prefs[stringPreferencesKey("encrypted_oauth_tokens_v1")] = encrypted
        }
        // getTokens() will reject the empty refreshToken and return null
        val refreshService = FakeOAuthRefreshService(emptyList())
        val manager = OAuthTokenManager(authManager, refreshService, ::now)

        val result = manager.ensureFreshToken()
        assertEquals(
            "Empty refresh token payload is rejected → NoSession",
            OAuthTokenResult.NoSession,
            result
        )
        assertEquals(0, refreshService.invocationCount)
    }

    @Test
    fun `refresh preserves existing refresh token`() = runBlocking {
        authManager.saveTokens(OAuthTokens("old_access", "my_refresh_token", now() + 60_000L))
        val refreshService = FakeOAuthRefreshService(listOf(
            OAuthRefreshResult.Success("new_access", 3600)
        ))
        val manager = OAuthTokenManager(authManager, refreshService, ::now)

        manager.ensureFreshToken()

        val tokens = authManager.getTokens()
        assertNotNull("Tokens must exist after refresh", tokens)
        assertEquals("new_access", tokens!!.accessToken)
        assertEquals("Refresh token must be preserved", "my_refresh_token", tokens.refreshToken)
    }

    // ── forceRefresh ─────────────────────────────────────────

    @Test
    fun `forceRefresh reuses token refreshed by another coroutine`() = runBlocking {
        // Token is valid, but forceRefresh should still refresh
        authManager.saveTokens(OAuthTokens("old_access", "refresh", now() + 600_000L))
        val refreshService = FakeOAuthRefreshService(listOf(
            OAuthRefreshResult.Success("fresh_access", 3600)
        ))
        val manager = OAuthTokenManager(authManager, refreshService, ::now)

        // First call refreshes
        val result1 = manager.forceRefresh()
        assertTrue(result1 is OAuthTokenResult.Available)
        assertEquals("fresh_access", (result1 as OAuthTokenResult.Available).tokens.accessToken)

        // Second call should detect that another coroutine already refreshed
        // by noticing the access token differs from rejectedAccessToken
        val result2 = manager.forceRefresh(rejectedAccessToken = "old_access")
        assertTrue("Must be Available without refresh", result2 is OAuthTokenResult.Available)
        val available2 = result2 as OAuthTokenResult.Available
        assertFalse("Must NOT be marked as refreshed", available2.refreshed)

        // Verify only one OAuth request was made
        assertEquals(1, refreshService.invocationCount)
    }

    @Test
    fun `forceRefresh with no rejection triggers fresh request`() = runBlocking {
        authManager.saveTokens(OAuthTokens("access", "refresh", now() + 600_000L))
        val refreshService = FakeOAuthRefreshService(listOf(
            OAuthRefreshResult.Success("fresh_access", 3600)
        ))
        val manager = OAuthTokenManager(authManager, refreshService, ::now)

        val result = manager.forceRefresh()
        assertTrue("Must refresh", result is OAuthTokenResult.Available)
        assertEquals("fresh_access", (result as OAuthTokenResult.Available).tokens.accessToken)
        assertEquals(1, refreshService.invocationCount)
    }

    @Test
    fun `forceRefresh transient failure returns TemporarilyUnavailable even if token is valid`() = runBlocking {
        authManager.saveTokens(OAuthTokens("access", "refresh", now() + 600_000L))
        val refreshService = FakeOAuthRefreshService(listOf(
            OAuthRefreshResult.TransientFailure
        ))
        val manager = OAuthTokenManager(authManager, refreshService, ::now)

        val result = manager.forceRefresh()
        assertEquals(
            "forceRefresh transient must be TemporarilyUnavailable even with valid token",
            OAuthTokenResult.TemporarilyUnavailable,
            result
        )
        assertEquals(1, refreshService.invocationCount)
    }

    @Test
    fun `forceRefresh invalid_grant returns ReauthenticationRequired`() = runBlocking {
        authManager.saveTokens(OAuthTokens("access", "refresh", now() + 600_000L))
        val refreshService = FakeOAuthRefreshService(listOf(
            OAuthRefreshResult.ReauthenticationRequired
        ))
        val manager = OAuthTokenManager(authManager, refreshService, ::now)

        val result = manager.forceRefresh()
        assertEquals(
            "forceRefresh invalid_grant",
            OAuthTokenResult.ReauthenticationRequired,
            result
        )
        assertNotNull("Tokens must not be deleted", authManager.getTokens())
    }

    // ── Latch & Reauthentication Events (Fase 1C.2) ────────────────────

    @Test
    fun `ten concurrent invalid_grant produce one OAuth call and one event`() = runBlocking {
        withTimeout(5_000L) {
            authManager.saveTokens(OAuthTokens("access", "refresh", now() + 60_000L))
            val callCount = AtomicInteger(0)
            val clockCalls = AtomicInteger(0)
            val refreshStarted = CompletableDeferred<Unit>()
            val allCallersReachedManager = CompletableDeferred<Unit>()
            val allowFinish = CompletableDeferred<Unit>()
            val refreshService = object : OAuthRefreshService {
                override suspend fun refresh(refreshToken: String): OAuthRefreshResult {
                    callCount.incrementAndGet()
                    refreshStarted.complete(Unit)
                    allowFinish.await()
                    return OAuthRefreshResult.ReauthenticationRequired
                }
            }
            val manager = OAuthTokenManager(authManager, refreshService) {
                if (clockCalls.incrementAndGet() == 11) {
                    allCallersReachedManager.complete(Unit)
                }
                now()
            }
            val eventCount = AtomicInteger(0)
            val eventReceived = CompletableDeferred<Unit>()
            val collectJob = launch(start = CoroutineStart.UNDISPATCHED) {
                manager.reauthenticationEvents.collect {
                    eventCount.incrementAndGet()
                    eventReceived.complete(Unit)
                }
            }

            coroutineScope {
                val start = CompletableDeferred<Unit>()
                val allReady = CompletableDeferred<Unit>()
                val readyCount = AtomicInteger(0)
                val jobs = List(10) {
                    async {
                        if (readyCount.incrementAndGet() == 10) {
                            allReady.complete(Unit)
                        }
                        start.await()
                        manager.ensureFreshToken()
                    }
                }
                allReady.await()
                start.complete(Unit)
                refreshStarted.await()
                allCallersReachedManager.await()
                allowFinish.complete(Unit)
                val results = jobs.awaitAll()
                assertTrue(results.all { it is OAuthTokenResult.ReauthenticationRequired })
            }
            eventReceived.await()
            collectJob.cancel()
            collectJob.join()

            assertEquals("Solo una llamada OAuth", 1, callCount.get())
            assertEquals("Solo un evento de reautenticación", 1, eventCount.get())
            assertTrue(manager.isReauthenticationPending)
            assertNotNull("Las credenciales se conservan hasta la invalidación", authManager.getTokens())
        }
    }

    @Test
    fun `reset is serialized - late ReauthRequired does not override reset`() = runBlocking {
        authManager.saveTokens(OAuthTokens("access", "refresh", now() + 60_000L))
        val blockRefresh = CompletableDeferred<Unit>()
        val refreshStarted = CompletableDeferred<Unit>()
        val callCount = AtomicInteger(0)
        val refreshService = object : OAuthRefreshService {
            override suspend fun refresh(refreshToken: String): OAuthRefreshResult {
                val c = callCount.incrementAndGet()
                if (c == 1) {
                    refreshStarted.complete(Unit)
                    blockRefresh.await()
                }
                return OAuthRefreshResult.ReauthenticationRequired
            }
        }
        val manager = OAuthTokenManager(authManager, refreshService, ::now)
        // Start a refresh that will block inside the mutex
        val refreshJob = async { manager.ensureFreshToken() }
        refreshStarted.await()
        // Reset while refresh is blocked — must wait for the mutex
        val resetJob = async(start = CoroutineStart.UNDISPATCHED) {
            manager.resetReauthenticationLatch()
        }
        // Unblock the refresh
        blockRefresh.complete(Unit)
        refreshJob.await()
        resetJob.await()
        // Reset acquired mutex after refresh finished, so latch is now false
        assertFalse("Latch debe estar limpio tras el reset", manager.isReauthenticationPending)
    }

    @Test
    fun `transient failures do not emit reauthentication events`() = runBlocking {
        authManager.saveTokens(OAuthTokens("access", "refresh", now() + 60_000L))
        val refreshService = FakeOAuthRefreshService(List(5) { OAuthRefreshResult.TransientFailure })
        val manager = OAuthTokenManager(authManager, refreshService, ::now)
        val events = mutableListOf<Unit>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            manager.reauthenticationEvents.collect { events.add(it) }
        }
        repeat(3) { manager.ensureFreshToken() }
        job.cancel()
        job.join()
        assertEquals("Transitorios no emiten eventos", 0, events.size)
        assertFalse(manager.isReauthenticationPending)
    }
}
