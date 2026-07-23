package com.david.mailapp.core.auth

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.david.mailapp.core.security.FailingSecretCipher
import com.david.mailapp.core.security.FakeSecretCipher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Tests for [AuthManager]'s pending OAuth session lifecycle.
 *
 * Uses a real DataStore backed by a temp file so the atomic read-and-delete
 * semantics of [AuthManager.consumePendingOAuthSession] are exercised against
 * a real store (not mocked). Encryption is provided by [FakeSecretCipher].
 */
class AuthManagerOAuthSessionTest {

    private lateinit var tempDir: File
    private lateinit var testStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>
    private lateinit var authManager: AuthManager

    @Before
    fun setUp() {
        tempDir = createTempDir("auth_test_")
        testStore = PreferenceDataStoreFactory.create {
            File(tempDir, "test_preferences.preferences_pb")
        }
        authManager = AuthManager(testStore, FakeSecretCipher())
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    // ── TTL boundary tests ─────────────────────────────────────

    @Test
    fun `session is valid before 10 minutes`() = runBlocking {
        val session = PendingOAuthSession(
            state = "valid_state",
            codeVerifier = "valid_verifier",
            createdAtEpochMillis = 0L
        )
        authManager.savePendingOAuthSession(session)

        // Age = 599_999 ms (< 600_000)
        val result = authManager.consumePendingOAuthSession("valid_state", 599_999L)
        assertTrue("Session must be valid before TTL", result is PendingOAuthSessionResult.Valid)
        val valid = result as PendingOAuthSessionResult.Valid
        assertEquals("valid_state", valid.session.state)
        assertEquals("valid_verifier", valid.session.codeVerifier)
    }

    @Test
    fun `session is valid exactly at 10 minutes`() = runBlocking {
        val session = PendingOAuthSession(
            state = "edge_state",
            codeVerifier = "edge_verifier",
            createdAtEpochMillis = 0L
        )
        authManager.savePendingOAuthSession(session)

        // Age = 600_000 ms (exactly TTL)
        val result = authManager.consumePendingOAuthSession("edge_state", 600_000L)
        assertTrue("Session must be valid at exactly TTL boundary", result is PendingOAuthSessionResult.Valid)
    }

    @Test
    fun `session is expired after 10 minutes`() = runBlocking {
        val session = PendingOAuthSession(
            state = "expired_state",
            codeVerifier = "expired_verifier",
            createdAtEpochMillis = 0L
        )
        authManager.savePendingOAuthSession(session)

        // Age = 600_001 ms (> TTL)
        val result = authManager.consumePendingOAuthSession("expired_state", 600_001L)
        assertTrue("Session must be expired after TTL", result is PendingOAuthSessionResult.Expired)
    }

    @Test
    fun `future timestamp is rejected as expired`() = runBlocking {
        val session = PendingOAuthSession(
            state = "future_state",
            codeVerifier = "future_verifier",
            createdAtEpochMillis = 1000L
        )
        authManager.savePendingOAuthSession(session)

        // nowEpochMillis < createdAtEpochMillis → age < 0 → Expired
        val result = authManager.consumePendingOAuthSession("future_state", 500L)
        assertTrue("Future timestamp must be treated as expired", result is PendingOAuthSessionResult.Expired)
    }

    // ── State validation ───────────────────────────────────────

    @Test
    fun `wrong state does not consume the session`() = runBlocking {
        authManager.savePendingOAuthSession(
            PendingOAuthSession("correct_state", "verifier", 0L)
        )

        // Try with wrong state
        val wrongResult = authManager.consumePendingOAuthSession("wrong_state", 1000L)
        assertTrue("Wrong state must return StateMismatch", wrongResult is PendingOAuthSessionResult.StateMismatch)

        // The legitimate session must still be available
        val retryResult = authManager.consumePendingOAuthSession("correct_state", 1000L)
        assertTrue("Correct state must succeed after rejection", retryResult is PendingOAuthSessionResult.Valid)
    }

    @Test
    fun `correct state consumes the session`() = runBlocking {
        authManager.savePendingOAuthSession(
            PendingOAuthSession("correct_state", "verifier", 0L)
        )

        val result = authManager.consumePendingOAuthSession("correct_state", 1000L)
        assertTrue("Correct state must return Valid", result is PendingOAuthSessionResult.Valid)

        // Second attempt must fail — session was consumed
        val secondResult = authManager.consumePendingOAuthSession("correct_state", 1000L)
        assertTrue("Consumed session must be gone", secondResult is PendingOAuthSessionResult.Missing)
    }

    // ── Edge cases ─────────────────────────────────────────────

    @Test
    fun `missing session returns Missing`() = runBlocking {
        val result = authManager.consumePendingOAuthSession("any_state", 1000L)
        assertTrue("No saved session must return Missing", result is PendingOAuthSessionResult.Missing)
    }

    @Test
    fun `savePendingOAuthSession overwrites previous session`() = runBlocking {
        authManager.savePendingOAuthSession(
            PendingOAuthSession("first", "verifier_1", 0L)
        )
        authManager.savePendingOAuthSession(
            PendingOAuthSession("second", "verifier_2", 0L)
        )

        val result = authManager.consumePendingOAuthSession("first", 1000L)
        assertTrue("Old session must be gone after overwrite", result is PendingOAuthSessionResult.StateMismatch)

        val result2 = authManager.consumePendingOAuthSession("second", 1000L)
        assertTrue("New session must be valid", result2 is PendingOAuthSessionResult.Valid)
    }

    @Test
    fun `clearPendingOAuthSession removes the session`() = runBlocking {
        authManager.savePendingOAuthSession(
            PendingOAuthSession("state", "verifier", 0L)
        )
        authManager.clearPendingOAuthSession()

        val result = authManager.consumePendingOAuthSession("state", 1000L)
        assertTrue("Session must be gone after clear", result is PendingOAuthSessionResult.Missing)
    }

    @Test
    fun `failed legacy session migration clears plaintext and returns Missing`() = runBlocking {
        val stateKey = stringPreferencesKey("pending_oauth_state")
        val verifierKey = stringPreferencesKey("pending_oauth_code_verifier")
        val createdAtKey = longPreferencesKey("pending_oauth_created_at")
        testStore.edit {
            it[stateKey] = "legacy_state"
            it[verifierKey] = "legacy_verifier"
            it[createdAtKey] = 0L
        }

        authManager = AuthManager(testStore, FailingSecretCipher())
        val result = authManager.consumePendingOAuthSession("legacy_state", 1000L)

        assertTrue("Failed migration must invalidate the session", result is PendingOAuthSessionResult.Missing)
        val prefs = testStore.data.first()
        assertNull("Legacy state must be removed", prefs[stateKey])
        assertNull("Legacy verifier must be removed", prefs[verifierKey])
        assertNull("Legacy timestamp must be removed", prefs[createdAtKey])
    }

    @Test
    fun `pending PDF cleanup marker survives token clearing`() = runBlocking {
        authManager.setPendingPdfCleanup(true)

        authManager.clearTokens()

        assertTrue(authManager.isPendingPdfCleanup())
    }

    @Test
    fun `completed PDF cleanup removes pending marker`() = runBlocking {
        authManager.setPendingPdfCleanup(true)

        authManager.setPendingPdfCleanup(false)

        assertFalse(authManager.isPendingPdfCleanup())
    }
}
