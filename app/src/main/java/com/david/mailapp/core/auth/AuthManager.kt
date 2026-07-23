package com.david.mailapp.core.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.david.mailapp.core.security.AndroidKeystoreSecretCipher
import com.david.mailapp.core.security.SecretCipher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "auth_tokens")

/**
 * OAuth2 token bundle stored in DataStore (encrypted at rest via [SecretCipher]).
 * Persisted across app restarts; excluded from backup via data_extraction_rules.xml.
 */
data class OAuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochMillis: Long = 0L
)

/**
 * Manages OAuth2 tokens and pending PKCE sessions via DataStore,
 * with AES-256-GCM encryption at rest through [SecretCipher].
 *
 * On first access after upgrade, legacy plaintext values are migrated to
 * encrypted payloads (see Rules 1–10 in the Fase 1B specification).
 */
class AuthManager(
    private val store: DataStore<Preferences>,
    private val cipher: SecretCipher
) {

    constructor(context: Context) : this(context.dataStore, AndroidKeystoreSecretCipher())

    companion object {
        // ── Legacy plaintext keys (migration only) ────────────────
        private val KEY_ACCESS = stringPreferencesKey("access_token")
        private val KEY_REFRESH = stringPreferencesKey("refresh_token")
        private val KEY_EXPIRES = intPreferencesKey("expires_in")
        private val KEY_PENDING_STATE = stringPreferencesKey("pending_oauth_state")
        private val KEY_PENDING_CODE_VERIFIER = stringPreferencesKey("pending_oauth_code_verifier")
        private val KEY_PENDING_CREATED_AT = longPreferencesKey("pending_oauth_created_at")

        // ── Encrypted keys ────────────────────────────────────────
        private val KEY_ENCRYPTED_TOKENS = stringPreferencesKey("encrypted_oauth_tokens_v1")
        private val KEY_ENCRYPTED_SESSION = stringPreferencesKey("encrypted_pending_oauth_session_v1")
        private val KEY_PENDING_PDF_CLEANUP = booleanPreferencesKey("pending_pdf_cleanup")

        // ── AAD constants (two distinct domains) ──────────────────
        private val AAD_TOKENS = "mailapp.oauth.tokens.v1".toByteArray()
        private val AAD_SESSION = "mailapp.oauth.pending-session.v1".toByteArray()
        private val TOKEN_JSON = Json { encodeDefaults = true }

        /** TTL for a pending OAuth session: 10 minutes in milliseconds. */
        internal const val PENDING_SESSION_TTL_MS = 600_000L
    }

    // ── Token management ──────────────────────────────────────

    /**
     * Save OAuth2 tokens as encrypted payload.
     *
     * Validates before encrypting:
     * - accessToken must not be empty.
     * - refreshToken must not be empty.
     * - expiresAtEpochMillis must be >= 0.
     *
     * Rule 7: If encryption fails, no plaintext is persisted and the error
     * propagates to the caller.
     */
    suspend fun saveTokens(tokens: OAuthTokens) {
        require(tokens.accessToken.isNotEmpty()) { "accessToken must not be empty" }
        require(tokens.refreshToken.isNotEmpty()) { "refreshToken must not be empty" }
        require(tokens.expiresAtEpochMillis >= 0) { "expiresAtEpochMillis must be >= 0" }
        val encrypted = encryptTokensPayload(tokens)
        store.edit { prefs ->
            prefs[KEY_ENCRYPTED_TOKENS] = encrypted
            removeLegacyTokenKeys(prefs)
        }
    }

    /**
     * Read OAuth2 tokens, attempting encrypted payload first, then legacy.
     *
     * Rule 1: Encrypted payload has priority. Legacy plaintext is cleaned up.
     * Rule 2/3: Legacy migration happens on read if encrypted is absent.
     * Rule 4/5: Corrupted encrypted payload → deleted, returns null.
     */
    suspend fun getTokens(): OAuthTokens? {
        val prefs = store.data.first()

        // Encrypted path (Rule 1)
        val encrypted = prefs[KEY_ENCRYPTED_TOKENS]
        if (encrypted != null) {
            val decryptResult = decryptTokenPayload(encrypted)
            val hasLegacy = prefs[KEY_ACCESS] != null ||
                prefs[KEY_REFRESH] != null ||
                prefs[KEY_EXPIRES] != null

            if (decryptResult != null) {
                if (decryptResult is DecryptResult.NeedsRewrite) {
                    store.edit { editPrefs ->
                        val currentEncrypted = editPrefs[KEY_ENCRYPTED_TOKENS]
                        // Only overwrite if no other coroutine replaced the value
                        if (currentEncrypted == encrypted) {
                            editPrefs[KEY_ENCRYPTED_TOKENS] = encryptTokensPayload(decryptResult.tokens)
                        }
                    }
                }
                if (hasLegacy) {
                    store.edit { removeLegacyTokenKeys(it) }
                }
            } else {
                store.edit {
                    it.remove(KEY_ENCRYPTED_TOKENS)
                    if (hasLegacy) {
                        removeLegacyTokenKeys(it)
                    }
                }
            }
            return decryptResult?.tokens
        }

        // Legacy migration path (Rule 2/3)
        val access = prefs[KEY_ACCESS]
        val refresh = prefs[KEY_REFRESH]
        if (access != null && refresh != null) {
            val tokens = OAuthTokens(access, refresh, expiresAtEpochMillis = 0L)
            return try {
                var migrated = false
                store.edit { prefsEdit ->
                    if (prefsEdit[KEY_ENCRYPTED_TOKENS] == null) {
                        prefsEdit[KEY_ENCRYPTED_TOKENS] = encryptTokensPayload(tokens)
                        migrated = true
                    }
                    removeLegacyTokenKeys(prefsEdit)
                }
                if (migrated) tokens else getTokens()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Fail closed: plaintext must not survive an unsuccessful migration.
                store.edit { removeLegacyTokenKeys(it) }
                null
            }
        }

        // Incomplete legacy (Rule 3): clean up
        if (access != null || refresh != null) {
            store.edit { removeLegacyTokenKeys(it) }
        }
        return null
    }

    suspend fun getAccessToken(): String? = getTokens()?.accessToken

    suspend fun getRefreshToken(): String? = getTokens()?.refreshToken

    suspend fun isAuthenticated(): Boolean {
        val tokens = getTokens()
        return tokens != null && tokens.refreshToken.isNotEmpty()
    }

    /**
     * Clear all tokens — both encrypted and legacy.
     *
     * Rule 9: Removes both encrypted and legacy token keys.
     * Rule 10: Also removes any pending OAuth session.
     */
    suspend fun clearTokens() {
        store.edit { prefs ->
            val pendingPdf = prefs[KEY_PENDING_PDF_CLEANUP]
            prefs.clear()
            if (pendingPdf == true) {
                prefs[KEY_PENDING_PDF_CLEANUP] = true
            }
        }
    }

    suspend fun setPendingPdfCleanup(pending: Boolean) {
        store.edit { prefs ->
            if (pending) {
                prefs[KEY_PENDING_PDF_CLEANUP] = true
            } else {
                prefs.remove(KEY_PENDING_PDF_CLEANUP)
            }
        }
    }

    suspend fun isPendingPdfCleanup(): Boolean {
        val prefs = store.data.first()
        return prefs[KEY_PENDING_PDF_CLEANUP] ?: false
    }

    // ── Pending OAuth session management ──────────────────────

    /**
     * Persist a [PendingOAuthSession] as encrypted payload.
     *
     * Rule 8: If encryption fails, the exception propagates so the caller
     * can abort before opening Chrome Custom Tabs.
     */
    internal suspend fun savePendingOAuthSession(session: PendingOAuthSession) {
        val encrypted = encryptSessionPayload(session)
        store.edit { prefs ->
            prefs[KEY_ENCRYPTED_SESSION] = encrypted
            removeLegacyPendingKeys(prefs)
        }
    }

    /**
     * Atomically read and conditionally consume the stored [PendingOAuthSession].
     *
     * Validation rules:
     * - If no session exists → [PendingOAuthSessionResult.Missing].
     * - If [receivedState] does not match → [PendingOAuthSessionResult.StateMismatch]
     *   (session is NOT consumed so a retry with the correct state can succeed).
     * - If age > [PENDING_SESSION_TTL_MS] OR timestamp is in the future →
     *   [PendingOAuthSessionResult.Expired] (session IS consumed).
     * - Otherwise → [PendingOAuthSessionResult.Valid] with the consumed session.
     *
     * The entire read-and-delete is atomic inside a single [store.edit] transaction.
     * Never log [receivedState] or the stored codeVerifier.
     *
     * Rule 6: If decryption fails, the session is deleted and [Missing] returned.
     */
    internal suspend fun consumePendingOAuthSession(
        receivedState: String,
        nowEpochMillis: Long
    ): PendingOAuthSessionResult {
        var result: PendingOAuthSessionResult = PendingOAuthSessionResult.Missing
        store.edit { prefs ->
            // ── Encrypted path ──
            val encrypted = prefs[KEY_ENCRYPTED_SESSION]
            if (encrypted != null) {
                val payload = try {
                    Json.decodeFromString<PendingOAuthSessionPayload>(
                        cipher.decrypt(encrypted, AAD_SESSION).decodeToString()
                    ).also { require(it.schemaVersion == 1) }
                } catch (_: Exception) {
                    // Rule 6: corrupted → delete and return Missing
                    prefs.remove(KEY_ENCRYPTED_SESSION)
                    removeLegacyPendingKeys(prefs)
                    result = PendingOAuthSessionResult.Missing
                    return@edit
                }
                result = consumeSessionPayload(payload, receivedState, nowEpochMillis, prefs)
                return@edit
            }

            // ── Legacy migration path ──
            val storedState = prefs[KEY_PENDING_STATE] ?: run {
                result = PendingOAuthSessionResult.Missing
                return@edit
            }
            val codeVerifier = prefs[KEY_PENDING_CODE_VERIFIER]
            val createdAt = prefs[KEY_PENDING_CREATED_AT]
            if (codeVerifier == null || createdAt == null) {
                // Rule 3: incomplete → clean
                removeLegacyPendingKeys(prefs)
                result = PendingOAuthSessionResult.Missing
                return@edit
            }

            // Build payload and attempt migration to encrypted
            val payload = PendingOAuthSessionPayload(
                state = storedState,
                codeVerifier = codeVerifier,
                createdAtEpochMillis = createdAt
            )
            try {
                val enc = cipher.encrypt(
                    Json.encodeToString(PendingOAuthSessionPayload.serializer(), payload).toByteArray(),
                    AAD_SESSION
                )
                prefs[KEY_ENCRYPTED_SESSION] = enc
                removeLegacyPendingKeys(prefs)
            } catch (_: Exception) {
                // Fail closed: never continue OAuth with a session that could not migrate.
                removeLegacyPendingKeys(prefs)
                result = PendingOAuthSessionResult.Missing
                return@edit
            }

            result = consumeSessionPayload(payload, receivedState, nowEpochMillis, prefs)
        }
        return result
    }

    /** Clear any existing pending session without validation. */
    internal suspend fun clearPendingOAuthSession() {
        store.edit { prefs ->
            prefs.remove(KEY_ENCRYPTED_SESSION)
            removeLegacyPendingKeys(prefs)
        }
    }

    // ── Private helpers ───────────────────────────────────────

    /** Encrypt [OAuthTokens] into a v2 payload string. Propagates on failure (Rule 7). */
    private fun encryptTokensPayload(tokens: OAuthTokens): String {
        val payload = OAuthTokensPayloadV2(
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken,
            expiresAtEpochMillis = tokens.expiresAtEpochMillis
        )
        return cipher.encrypt(
            TOKEN_JSON.encodeToString(OAuthTokensPayloadV2.serializer(), payload).toByteArray(),
            AAD_TOKENS
        )
    }

    /** Result of [decryptTokenPayload]. */
    private sealed interface DecryptResult {
        val tokens: OAuthTokens
        data class Current(override val tokens: OAuthTokens) : DecryptResult
        data class NeedsRewrite(override val tokens: OAuthTokens) : DecryptResult
    }

    /**
     * Decrypt and parse an encrypted token payload. Returns null on failure.
     *
     * Validates schema version and field requirements.
     */
    private fun decryptTokenPayload(encrypted: String): DecryptResult? {
        val jsonStr = try {
            cipher.decrypt(encrypted, AAD_TOKENS).decodeToString()
        } catch (_: Exception) {
            return null
        }

        return try {
            val jsonObject = TOKEN_JSON.parseToJsonElement(jsonStr).jsonObject
            val schemaVersion = jsonObject["schemaVersion"]?.jsonPrimitive?.intOrNull

            if (schemaVersion == null) {
                return decodeUnversionedTokenPayload(jsonStr, jsonObject.keys)
            }

            when (schemaVersion) {
                1 -> {
                    val v1 = TOKEN_JSON.decodeFromString<OAuthTokensPayloadV1>(jsonStr)
                    require(v1.accessToken.isNotEmpty())
                    require(v1.refreshToken.isNotEmpty())
                    require(v1.expiresIn > 0)
                    DecryptResult.NeedsRewrite(
                        OAuthTokens(v1.accessToken, v1.refreshToken, expiresAtEpochMillis = 0L)
                    )
                }

                2 -> {
                    val v2 = TOKEN_JSON.decodeFromString<OAuthTokensPayloadV2>(jsonStr)
                    require(v2.accessToken.isNotEmpty())
                    require(v2.refreshToken.isNotEmpty())
                    require(v2.expiresAtEpochMillis >= 0)
                    DecryptResult.Current(
                        OAuthTokens(v2.accessToken, v2.refreshToken, v2.expiresAtEpochMillis)
                    )
                }

                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Reads payloads written before schemaVersion was encoded explicitly. */
    private fun decodeUnversionedTokenPayload(
        jsonStr: String,
        fields: Set<String>
    ): DecryptResult? {
        return when {
            "expiresIn" in fields && "expiresAtEpochMillis" !in fields -> {
                val v1 = TOKEN_JSON.decodeFromString<OAuthTokensPayloadV1>(jsonStr)
                require(v1.accessToken.isNotEmpty())
                require(v1.refreshToken.isNotEmpty())
                require(v1.expiresIn > 0)
                DecryptResult.NeedsRewrite(
                    OAuthTokens(v1.accessToken, v1.refreshToken, expiresAtEpochMillis = 0L)
                )
            }

            "expiresAtEpochMillis" in fields && "expiresIn" !in fields -> {
                val v2 = TOKEN_JSON.decodeFromString<OAuthTokensPayloadV2>(jsonStr)
                require(v2.accessToken.isNotEmpty())
                require(v2.refreshToken.isNotEmpty())
                require(v2.expiresAtEpochMillis >= 0)
                DecryptResult.NeedsRewrite(
                    OAuthTokens(v2.accessToken, v2.refreshToken, v2.expiresAtEpochMillis)
                )
            }

            else -> null
        }
    }

    /** Encrypt a [PendingOAuthSession] into a payload string. Propagates on failure (Rule 8). */
    private fun encryptSessionPayload(session: PendingOAuthSession): String {
        val payload = PendingOAuthSessionPayload(
            state = session.state,
            codeVerifier = session.codeVerifier,
            createdAtEpochMillis = session.createdAtEpochMillis
        )
        return cipher.encrypt(Json.encodeToString(PendingOAuthSessionPayload.serializer(), payload).toByteArray(), AAD_SESSION)
    }

    /**
     * Validate and consume a [PendingOAuthSessionPayload] atomically.
     * Mutates [prefs] for the delete-on-consume case.
     *
     * State mismatch does NOT consume the session, allowing retry.
     */
    private fun consumeSessionPayload(
        payload: PendingOAuthSessionPayload,
        receivedState: String,
        nowEpochMillis: Long,
        prefs: MutablePreferences
    ): PendingOAuthSessionResult {
        if (!OAuthSecurity.constantTimeEquals(payload.state, receivedState)) {
            return PendingOAuthSessionResult.StateMismatch
        }

        val age = nowEpochMillis - payload.createdAtEpochMillis
        if (age < 0 || age > PENDING_SESSION_TTL_MS) {
            prefs.remove(KEY_ENCRYPTED_SESSION)
            removeLegacyPendingKeys(prefs)
            return PendingOAuthSessionResult.Expired
        }

        prefs.remove(KEY_ENCRYPTED_SESSION)
        removeLegacyPendingKeys(prefs)
        return PendingOAuthSessionResult.Valid(
            PendingOAuthSession(
                state = payload.state,
                codeVerifier = payload.codeVerifier,
                createdAtEpochMillis = payload.createdAtEpochMillis
            )
        )
    }

    private fun removeLegacyTokenKeys(prefs: MutablePreferences) {
        prefs.remove(KEY_ACCESS)
        prefs.remove(KEY_REFRESH)
        prefs.remove(KEY_EXPIRES)
    }

    private fun removeLegacyPendingKeys(prefs: MutablePreferences) {
        prefs.remove(KEY_PENDING_STATE)
        prefs.remove(KEY_PENDING_CODE_VERIFIER)
        prefs.remove(KEY_PENDING_CREATED_AT)
    }
}
