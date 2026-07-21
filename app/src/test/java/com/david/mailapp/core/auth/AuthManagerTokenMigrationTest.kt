package com.david.mailapp.core.auth

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.david.mailapp.core.security.FailingSecretCipher
import com.david.mailapp.core.security.FakeSecretCipher
import com.david.mailapp.core.security.SecretCipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Tests for [AuthManager] token encryption and legacy migration (Fase 1B).
 *
 * Uses [FakeSecretCipher] for encryption and a real DataStore backed by a temp
 * file so migration semantics are exercised against a real store.
 */
class AuthManagerTokenMigrationTest {

    private lateinit var tempDir: File
    private lateinit var testStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>
    private lateinit var authManager: AuthManager

    /** Shared cipher so encrypted data survives between "restart" instances. */
    private val sharedCipher: SecretCipher = FakeSecretCipher()

    /** Legacy keys matching AuthManager's companion. */
    private val KEY_ACCESS = stringPreferencesKey("access_token")
    private val KEY_REFRESH = stringPreferencesKey("refresh_token")
    private val KEY_EXPIRES = intPreferencesKey("expires_in")
    private val KEY_ENCRYPTED_TOKENS = stringPreferencesKey("encrypted_oauth_tokens_v1")

    @Before
    fun setUp() {
        tempDir = createTempDir("migration_test_")
        testStore = PreferenceDataStoreFactory.create {
            File(tempDir, "test_preferences.preferences_pb")
        }
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    // ── Token migration (Rule 2) ──────────────────────────────

    @Test
    fun `legacy tokens are migrated to encrypted on first read`() = runBlocking {
        // Write legacy plaintext tokens
        testStore.edit { prefs ->
            prefs[KEY_ACCESS] = "legacy_access_token"
            prefs[KEY_REFRESH] = "legacy_refresh_token"
            prefs[KEY_EXPIRES] = 7200
        }

        authManager = AuthManager(testStore, sharedCipher)

        // Read should trigger migration
        val tokens = authManager.getTokens()
        assertNotNull("Tokens must be readable after migration", tokens)
        assertEquals("legacy_access_token", tokens!!.accessToken)
        assertEquals("legacy_refresh_token", tokens.refreshToken)

        // Verify plaintext keys are gone
        val prefs = testStore.data.first()
        assertNull("Access token must not remain in plaintext", prefs[KEY_ACCESS])
        assertNull("Refresh token must not remain in plaintext", prefs[KEY_REFRESH])

        // Verify encrypted key exists
        assertNotNull("Encrypted token key must exist after migration", prefs[KEY_ENCRYPTED_TOKENS])
    }

    @Test
    fun `migrated tokens survive restart`() = runBlocking {
        // Write legacy tokens
        testStore.edit { prefs ->
            prefs[KEY_ACCESS] = "survive_token"
            prefs[KEY_REFRESH] = "survive_refresh"
            prefs[KEY_EXPIRES] = 3600
        }

        // First AuthManager instance — reads and migrates
        val am1 = AuthManager(testStore, sharedCipher)
        val tokens1 = am1.getTokens()
        assertNotNull("First read must succeed", tokens1)

        // "Restart" with a new AuthManager instance
        val am2 = AuthManager(testStore, sharedCipher)
        val tokens2 = am2.getTokens()
        assertNotNull("Tokens must survive restart", tokens2)
        assertEquals("survive_token", tokens2!!.accessToken)
        assertEquals("survive_refresh", tokens2.refreshToken)
    }

    // ── Partial legacy (Rule 3) ───────────────────────────────

    @Test
    fun `partial legacy tokens are cleaned and return null`() = runBlocking {
        testStore.edit { prefs ->
            prefs[KEY_ACCESS] = "orphan_access"
            // No refresh token — incomplete
        }

        authManager = AuthManager(testStore, sharedCipher)
        val tokens = authManager.getTokens()

        assertNull("Partial legacy must return null", tokens)

        // Verify orphan keys are cleaned
        val prefs = testStore.data.first()
        assertNull("Orphan access must be cleaned", prefs[KEY_ACCESS])
    }

    @Test
    fun `only refresh without access is also cleaned`() = runBlocking {
        testStore.edit { prefs ->
            prefs[KEY_REFRESH] = "orphan_refresh"
        }

        authManager = AuthManager(testStore, sharedCipher)
        assertNull("Partial legacy must return null", authManager.getTokens())

        val prefs = testStore.data.first()
        assertNull("Orphan refresh must be cleaned", prefs[KEY_REFRESH])
    }

    @Test
    fun `empty store returns null`() = runBlocking {
        authManager = AuthManager(testStore, sharedCipher)
        assertNull("Empty store must return null", authManager.getTokens())
    }

    // ── Corrupted encrypted data (Rule 4/5) ───────────────────

    @Test
    fun `corrupted encrypted tokens are deleted and return null`() = runBlocking {
        testStore.edit { prefs ->
            prefs[KEY_ENCRYPTED_TOKENS] = "v1:invalid-base64-data"
        }

        authManager = AuthManager(testStore, sharedCipher)
        assertNull("Corrupted data must return null", authManager.getTokens())

        val prefs = testStore.data.first()
        assertNull("Corrupted encrypted key must be removed", prefs[KEY_ENCRYPTED_TOKENS])
    }

    @Test
    fun `garbage encrypted value is deleted and returns null`() = runBlocking {
        testStore.edit { prefs ->
            prefs[KEY_ENCRYPTED_TOKENS] = "this is not valid ciphertext at all"
        }

        authManager = AuthManager(testStore, sharedCipher)
        assertNull("Garbage data must return null", authManager.getTokens())

        val prefs = testStore.data.first()
        assertNull("Garbage encrypted key must be removed", prefs[KEY_ENCRYPTED_TOKENS])
    }

    @Test
    fun `corrupted encrypted tokens cannot fall back to legacy plaintext`() = runBlocking {
        testStore.edit { prefs ->
            prefs[KEY_ENCRYPTED_TOKENS] = "corrupted"
            prefs[KEY_ACCESS] = "legacy_access"
            prefs[KEY_REFRESH] = "legacy_refresh"
        }

        authManager = AuthManager(testStore, sharedCipher)
        assertNull("Corrupted encrypted data must fail closed", authManager.getTokens())

        val prefs = testStore.data.first()
        assertNull("Corrupted encrypted value must be removed", prefs[KEY_ENCRYPTED_TOKENS])
        assertNull("Legacy access fallback must be removed", prefs[KEY_ACCESS])
        assertNull("Legacy refresh fallback must be removed", prefs[KEY_REFRESH])
    }

    @Test
    fun `encrypted with wrong AAD is treated as corrupted`() = runBlocking {
        // Encrypt tokens with a different cipher that uses wrong/random AAD
        val wrongCipher = FakeSecretCipher()
        testStore.edit { prefs ->
            prefs[KEY_ENCRYPTED_TOKENS] = wrongCipher.encrypt(
                """{"schemaVersion":1,"accessToken":"a","refreshToken":"b","expiresIn":3600}""".toByteArray(),
                "wrong-aad-constant".toByteArray()
            )
        }

        authManager = AuthManager(testStore, sharedCipher)
        assertNull("Wrong AAD must return null", authManager.getTokens())
    }

    // ── saveTokens (Rule 7) ───────────────────────────────────

    @Test
    fun `saveTokens stores encrypted and cleans legacy`() = runBlocking {
        // Write legacy tokens first
        testStore.edit { prefs ->
            prefs[KEY_ACCESS] = "legacy"
            prefs[KEY_REFRESH] = "legacy_refresh"
        }

        authManager = AuthManager(testStore, sharedCipher)
        authManager.saveTokens(OAuthTokens("new_access", "new_refresh", 1800))

        val tokens = authManager.getTokens()
        assertNotNull("Tokens must be readable after save", tokens)
        assertEquals("new_access", tokens!!.accessToken)
        assertEquals("new_refresh", tokens.refreshToken)

        // Legacy must be gone
        val prefs = testStore.data.first()
        assertNull("Legacy access must be removed after save", prefs[KEY_ACCESS])
        assertNull("Legacy refresh must be removed after save", prefs[KEY_REFRESH])
    }

    @Test
    fun `failed legacy migration clears plaintext and returns null`() = runBlocking {
        testStore.edit { prefs ->
            prefs[KEY_ACCESS] = "legacy_access"
            prefs[KEY_REFRESH] = "legacy_refresh"
        }

        authManager = AuthManager(testStore, FailingSecretCipher())
        assertNull("Encryption failure must invalidate legacy tokens", authManager.getTokens())

        val prefs = testStore.data.first()
        assertNull("Legacy access must be removed after migration failure", prefs[KEY_ACCESS])
        assertNull("Legacy refresh must be removed after migration failure", prefs[KEY_REFRESH])
        assertNull("No encrypted payload may be written on failure", prefs[KEY_ENCRYPTED_TOKENS])
    }

    // ── clearTokens (Rule 9/10) ───────────────────────────────

    @Test
    fun `clearTokens removes both encrypted and pending session`() = runBlocking {
        authManager = AuthManager(testStore, sharedCipher)

        // Save tokens and a pending session
        authManager.saveTokens(OAuthTokens("access", "refresh", 3600))
        authManager.savePendingOAuthSession(
            PendingOAuthSession("state", "verifier", 1000L)
        )

        authManager.clearTokens()

        // Verify nothing remains
        assertNull("Tokens must be null after clear", authManager.getTokens())

        val sessionResult = authManager.consumePendingOAuthSession("state", 2000L)
        assertTrue("Session must be gone after clear", sessionResult is PendingOAuthSessionResult.Missing)

        // Verify DataStore is empty
        val prefs = testStore.data.first()
        assertTrue("DataStore should be empty after clear", prefs.asMap().isEmpty())
    }

    // ── isAuthenticated after migration ───────────────────────

    @Test
    fun `isAuthenticated returns true after migration`() = runBlocking {
        testStore.edit { prefs ->
            prefs[KEY_ACCESS] = "valid_access"
            prefs[KEY_REFRESH] = "valid_refresh"
        }

        authManager = AuthManager(testStore, sharedCipher)
        assertTrue("User must be authenticated after migration", authManager.isAuthenticated())
    }

    // ── Absence of plaintext ──────────────────────────────────

    @Test
    fun `no plaintext values in store after encrypted save`() = runBlocking {
        authManager = AuthManager(testStore, sharedCipher)
        authManager.saveTokens(OAuthTokens("access", "refresh", 3600))

        val prefs = testStore.data.first()
        // The only keys should be encrypted ones
        val allKeys = prefs.asMap().keys.map { it.name }

        // No legacy keys should exist
        assertTrue("No legacy access token", allKeys.none { it == "access_token" })
        assertTrue("No legacy refresh token", allKeys.none { it == "refresh_token" })
        assertTrue("No legacy expires in", allKeys.none { it == "expires_in" })
        assertTrue("Key must be encrypted_oauth_tokens_v1", allKeys.any { it == "encrypted_oauth_tokens_v1" })
    }

    // ── V2 payload (Fase 1C.1) ────────────────────────────────

    @Test
    fun `saveTokens writes v2 payload`() = runBlocking {
        authManager = AuthManager(testStore, sharedCipher)
        authManager.saveTokens(OAuthTokens("v2_access", "v2_refresh", 99999L))

        val tokens = authManager.getTokens()
        assertNotNull("V2 tokens must be readable", tokens)
        assertEquals("v2_access", tokens!!.accessToken)
        assertEquals("v2_refresh", tokens.refreshToken)
        assertEquals(99999L, tokens.expiresAtEpochMillis)

        val encrypted = testStore.data.first()[KEY_ENCRYPTED_TOKENS]!!
        val payload = sharedCipher.decrypt(
            encrypted,
            "mailapp.oauth.tokens.v1".toByteArray()
        ).decodeToString()
        assertTrue("V2 payload must persist schemaVersion", payload.contains("\"schemaVersion\":2"))
    }

    @Test
    fun `v2 tokens survive restart`() = runBlocking {
        val am1 = AuthManager(testStore, sharedCipher)
        am1.saveTokens(OAuthTokens("survive_access", "survive_refresh", 88888L))

        // "Restart" with a new instance
        val am2 = AuthManager(testStore, sharedCipher)
        val tokens = am2.getTokens()
        assertNotNull("V2 tokens must survive restart", tokens)
        assertEquals("survive_access", tokens!!.accessToken)
        assertEquals(88888L, tokens.expiresAtEpochMillis)
    }

    @Test
    fun `v1 encrypted payload is migrated to v2 with expiresAtEpochMillis=0`() = runBlocking {
        // Write a v1 encrypted payload using the same cipher the AuthManager will use
        val v1Encrypted = sharedCipher.encrypt(
            """{"schemaVersion":1,"accessToken":"v1_access","refreshToken":"v1_refresh","expiresIn":3600}""".toByteArray(),
            "mailapp.oauth.tokens.v1".toByteArray()
        )
        testStore.edit { prefs ->
            prefs[stringPreferencesKey("encrypted_oauth_tokens_v1")] = v1Encrypted
        }

        authManager = AuthManager(testStore, sharedCipher)
        val tokens = authManager.getTokens()

        assertNotNull("V1 must migrate to tokens", tokens)
        assertEquals("v1_access", tokens!!.accessToken)
        assertEquals("v1_refresh", tokens.refreshToken)
        assertEquals("Migrated v1 must have expiresAtEpochMillis=0", 0L, tokens.expiresAtEpochMillis)
    }

    @Test
    fun `v1 migration re-encrypts as v2`() = runBlocking {
        // Write v1 encrypted using sharedCipher
        val v1Encrypted = sharedCipher.encrypt(
            """{"schemaVersion":1,"accessToken":"a","refreshToken":"b","expiresIn":3600}""".toByteArray(),
            "mailapp.oauth.tokens.v1".toByteArray()
        )
        testStore.edit { prefs ->
            prefs[stringPreferencesKey("encrypted_oauth_tokens_v1")] = v1Encrypted
        }

        authManager = AuthManager(testStore, sharedCipher)
        authManager.getTokens() // triggers migration

        // After migration, the stored payload must be v2. "Restart" to verify.
        val am2 = AuthManager(testStore, sharedCipher)
        val tokens = am2.getTokens()
        assertNotNull("V2 must survive restart", tokens)
        assertEquals("a", tokens!!.accessToken)
        assertEquals(0L, tokens.expiresAtEpochMillis)
    }

    @Test
    fun `unversioned v1 payload is migrated to explicit v2`() = runBlocking {
        val encrypted = sharedCipher.encrypt(
            """{"accessToken":"old_access","refreshToken":"old_refresh","expiresIn":3600}""".toByteArray(),
            "mailapp.oauth.tokens.v1".toByteArray()
        )
        testStore.edit { it[KEY_ENCRYPTED_TOKENS] = encrypted }

        authManager = AuthManager(testStore, sharedCipher)
        val tokens = authManager.getTokens()

        assertEquals("old_access", tokens?.accessToken)
        assertEquals(0L, tokens?.expiresAtEpochMillis)
        val rewritten = testStore.data.first()[KEY_ENCRYPTED_TOKENS]!!
        val payload = sharedCipher.decrypt(
            rewritten,
            "mailapp.oauth.tokens.v1".toByteArray()
        ).decodeToString()
        assertTrue(payload.contains("\"schemaVersion\":2"))
    }

    @Test
    fun `unversioned transitional v2 payload is rewritten with explicit version`() = runBlocking {
        val encrypted = sharedCipher.encrypt(
            """{"accessToken":"access","refreshToken":"refresh","expiresAtEpochMillis":99999}""".toByteArray(),
            "mailapp.oauth.tokens.v1".toByteArray()
        )
        testStore.edit { it[KEY_ENCRYPTED_TOKENS] = encrypted }

        authManager = AuthManager(testStore, sharedCipher)
        val tokens = authManager.getTokens()

        assertEquals("access", tokens?.accessToken)
        assertEquals(99999L, tokens?.expiresAtEpochMillis)
        val rewritten = testStore.data.first()[KEY_ENCRYPTED_TOKENS]!!
        val payload = sharedCipher.decrypt(
            rewritten,
            "mailapp.oauth.tokens.v1".toByteArray()
        ).decodeToString()
        assertTrue(payload.contains("\"schemaVersion\":2"))
    }

    @Test
    fun `v1 migration does not overwrite concurrent v2 write`() = runBlocking {
        val v1Encrypted = sharedCipher.encrypt(
            """{"schemaVersion":1,"accessToken":"old","refreshToken":"old","expiresIn":3600}""".toByteArray(),
            "mailapp.oauth.tokens.v1".toByteArray()
        )
        testStore.edit { prefs ->
            prefs[stringPreferencesKey("encrypted_oauth_tokens_v1")] = v1Encrypted
        }

        val decryptStarted = CountDownLatch(1)
        val allowDecryptToReturn = CountDownLatch(1)
        val blockingCipher = object : SecretCipher {
            override fun encrypt(plaintext: ByteArray, aad: ByteArray): String =
                sharedCipher.encrypt(plaintext, aad)

            override fun decrypt(encrypted: String, aad: ByteArray): ByteArray {
                val plaintext = sharedCipher.decrypt(encrypted, aad)
                decryptStarted.countDown()
                check(allowDecryptToReturn.await(5, TimeUnit.SECONDS)) {
                    "Timed out waiting to resume V1 migration"
                }
                return plaintext
            }
        }
        authManager = AuthManager(testStore, blockingCipher)

        val migratingRead = async(Dispatchers.Default) { authManager.getTokens() }
        val readWasPaused = withContext(Dispatchers.IO) {
            decryptStarted.await(5, TimeUnit.SECONDS)
        }
        if (!readWasPaused) allowDecryptToReturn.countDown()
        assertTrue("V1 read must pause before migration write", readWasPaused)

        authManager.saveTokens(OAuthTokens("fresh_access", "fresh_refresh", 99999L))
        allowDecryptToReturn.countDown()
        migratingRead.await()

        val finalTokens = authManager.getTokens()
        assertNotNull("Fresh tokens must survive", finalTokens)
        assertEquals("Concurrent v2 write must not be overwritten", "fresh_access", finalTokens!!.accessToken)
    }

    // ── Empty token payloads (written directly, bypassing saveTokens) ──

    @Test
    fun `payload with empty refresh token returns null`() = runBlocking {
        val encrypted = sharedCipher.encrypt(
            """{"schemaVersion":2,"accessToken":"access","refreshToken":"","expiresAtEpochMillis":99999}""".toByteArray(),
            "mailapp.oauth.tokens.v1".toByteArray()
        )
        testStore.edit { prefs ->
            prefs[stringPreferencesKey("encrypted_oauth_tokens_v1")] = encrypted
        }

        authManager = AuthManager(testStore, sharedCipher)
        assertNull("Empty refresh token payload must return null", authManager.getTokens())
    }

    @Test
    fun `payload with empty access token returns null`() = runBlocking {
        val encrypted = sharedCipher.encrypt(
            """{"schemaVersion":2,"accessToken":"","refreshToken":"refresh","expiresAtEpochMillis":99999}""".toByteArray(),
            "mailapp.oauth.tokens.v1".toByteArray()
        )
        testStore.edit { prefs ->
            prefs[stringPreferencesKey("encrypted_oauth_tokens_v1")] = encrypted
        }

        authManager = AuthManager(testStore, sharedCipher)
        assertNull("Empty access token payload must return null", authManager.getTokens())
    }

    // ── Negative expiresAtEpochMillis ─────────────────────────

    @Test
    fun `negative expiresAtEpochMillis is treated as corrupted`() = runBlocking {
        authManager = AuthManager(testStore, sharedCipher)
        val v2WithNegative = sharedCipher.encrypt(
            """{"schemaVersion":2,"accessToken":"a","refreshToken":"b","expiresAtEpochMillis":-1}""".toByteArray(),
            "mailapp.oauth.tokens.v1".toByteArray()
        )
        testStore.edit { prefs ->
            prefs[stringPreferencesKey("encrypted_oauth_tokens_v1")] = v2WithNegative
        }

        assertNull("Negative expiresAtEpochMillis must return null", authManager.getTokens())
    }

    // ── Unknown schemaVersion ────────────────────────────────

    @Test
    fun `schemaVersion=99 with all v1 fields is treated as corrupted`() = runBlocking {
        val encrypted = sharedCipher.encrypt(
            """{"schemaVersion":99,"accessToken":"a","refreshToken":"b","expiresIn":3600}""".toByteArray(),
            "mailapp.oauth.tokens.v1".toByteArray()
        )
        testStore.edit { prefs ->
            prefs[stringPreferencesKey("encrypted_oauth_tokens_v1")] = encrypted
        }

        authManager = AuthManager(testStore, sharedCipher)
        assertNull("schemaVersion=99 must return null", authManager.getTokens())

        val prefs = testStore.data.first()
        assertNull("Corrupted payload must be deleted", prefs[stringPreferencesKey("encrypted_oauth_tokens_v1")])
    }

    @Test
    fun `schemaVersion=99 with all v2 fields is treated as corrupted`() = runBlocking {
        val encrypted = sharedCipher.encrypt(
            """{"schemaVersion":99,"accessToken":"a","refreshToken":"b","expiresAtEpochMillis":99999}""".toByteArray(),
            "mailapp.oauth.tokens.v1".toByteArray()
        )
        testStore.edit { prefs ->
            prefs[stringPreferencesKey("encrypted_oauth_tokens_v1")] = encrypted
        }

        authManager = AuthManager(testStore, sharedCipher)
        assertNull("schemaVersion=99 must return null", authManager.getTokens())

        val prefs = testStore.data.first()
        assertNull("Corrupted payload must be deleted", prefs[stringPreferencesKey("encrypted_oauth_tokens_v1")])
    }

    // ── saveTokens validation ─────────────────────────────────

    @Test
    fun `saveTokens rejects empty access token`() = runBlocking {
        authManager = AuthManager(testStore, sharedCipher)
        try {
            authManager.saveTokens(OAuthTokens("", "refresh", 99999L))
            fail("saveTokens should throw for empty access token")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `saveTokens rejects empty refresh token`() = runBlocking {
        authManager = AuthManager(testStore, sharedCipher)
        try {
            authManager.saveTokens(OAuthTokens("access", "", 99999L))
            fail("saveTokens should throw for empty refresh token")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `saveTokens rejects negative expiresAtEpochMillis`() = runBlocking {
        authManager = AuthManager(testStore, sharedCipher)
        try {
            authManager.saveTokens(OAuthTokens("access", "refresh", -1L))
            fail("saveTokens should throw for negative expiresAtEpochMillis")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `saveTokens accepts expiresAtEpochMillis=0`() = runBlocking {
        authManager = AuthManager(testStore, sharedCipher)
        authManager.saveTokens(OAuthTokens("access", "refresh", 0L))

        val tokens = authManager.getTokens()
        assertNotNull("expiresAtEpochMillis=0 must be valid", tokens)
        assertEquals(0L, tokens!!.expiresAtEpochMillis)
    }

    @Test
    fun `unknown schema version is treated as corrupted`() = runBlocking {
        val badEncrypted = sharedCipher.encrypt(
            """{"schemaVersion":99,"accessToken":"a","refreshToken":"b"}""".toByteArray(),
            "mailapp.oauth.tokens.v1".toByteArray()
        )
        testStore.edit { prefs ->
            prefs[stringPreferencesKey("encrypted_oauth_tokens_v1")] = badEncrypted
        }

        authManager = AuthManager(testStore, sharedCipher)
        assertNull("Unknown schema version must return null", authManager.getTokens())
    }
}
