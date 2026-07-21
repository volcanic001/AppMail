package com.david.mailapp.core.auth

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Result of [OAuthTokenManager.ensureFreshToken] or [OAuthTokenManager.forceRefresh].
 */
sealed interface OAuthTokenResult {
    /**
     * A usable token is available.
     *
     * @param tokens     The current token bundle (may have been refreshed).
     * @param refreshed  Whether a refresh was performed in this call.
     */
    data class Available(
        val tokens: OAuthTokens,
        val refreshed: Boolean
    ) : OAuthTokenResult

    /** A transient error occurred. The caller may retry later. */
    data object TemporarilyUnavailable : OAuthTokenResult

    /** The refresh token is invalid — user must re-authenticate. */
    data object ReauthenticationRequired : OAuthTokenResult

    /** No token session exists. */
    data object NoSession : OAuthTokenResult
}

/**
 * Manages OAuth2 access token freshness with single-flight concurrency.
 *
 * Wraps [AuthManager] and [OAuthRefreshService] so that:
 * - Stale tokens are refreshed automatically under a [Mutex].
 * - Concurrent callers share one refresh request.
 * - Transient errors degrade gracefully (old token returned while valid).
 * - The refresh token is **never** deleted — reauthentication signals are
 *   propagated but the session is preserved for the caller to decide.
 */
class OAuthTokenManager(
    private val authManager: AuthManager,
    private val refreshService: OAuthRefreshService,
    private val nowEpochMillis: () -> Long = { System.currentTimeMillis() }
) {
    /** 5-minute margin: within this window the token is considered "about to expire". */
    private val refreshMutex = Mutex()

    private val isReauthPending = AtomicBoolean(false)
    private val reauthChannel = Channel<Unit>(capacity = Channel.CONFLATED)

    /** Expose the reauthentication events as a Flow. */
    val reauthenticationEvents: Flow<Unit> = reauthChannel.receiveAsFlow()

    /** Expose the current status of the reauthentication latch. */
    val isReauthenticationPending: Boolean
        get() = isReauthPending.get()

    /** Reset the latch and discard any pending events. Called after successful login. */
    suspend fun resetReauthenticationLatch() = refreshMutex.withLock {
        isReauthPending.set(false)
        // Drain any old events that were left in the conflated channel
        reauthChannel.tryReceive()
    }

    suspend fun loadTokens(): OAuthTokens? = authManager.getTokens()

    companion object {
        private const val REFRESH_MARGIN_MS = 300_000L
    }

    // ── Public API ──────────────────────────────────────────────

    /**
     * Returns a fresh access token if available, refreshing if the current
     * token is within the 5-minute expiration window.
     *
     * Rules:
     * 1. No stored tokens → [NoSession].
     * 2. Refresh token is empty → [ReauthenticationRequired].
     * 3. `nowEpochMillis() + REFRESH_MARGIN_MS < expiresAtEpochMillis` → [Available](refreshed=false).
     * 4. Otherwise → acquire [refreshMutex], re-read tokens, re-evaluate.
     * 5. Still stale → call [OAuthRefreshService.refresh].
     * 6. Success → persist new tokens, return [Available](refreshed=true).
     * 7. Transient + old token still valid → return old token as [Available](refreshed=false).
     * 8. Transient + old token expired → [TemporarilyUnavailable].
     * 9. [ReauthenticationRequired] → propagated as-is without deleting tokens.
     */
    suspend fun ensureFreshToken(): OAuthTokenResult {
        if (isReauthPending.get()) {
            return OAuthTokenResult.ReauthenticationRequired
        }
        val initialTokens = authManager.getTokens() ?: return OAuthTokenResult.NoSession
        if (initialTokens.refreshToken.isEmpty()) {
            return OAuthTokenResult.ReauthenticationRequired
        }
        if (isFresh(initialTokens)) {
            return OAuthTokenResult.Available(initialTokens, refreshed = false)
        }

        // Need refresh — acquire mutex for single-flight
        return refreshMutex.withLock {
            if (isReauthPending.get()) {
                return@withLock OAuthTokenResult.ReauthenticationRequired
            }
            // Re-read tokens — another coroutine may have refreshed while we waited
            val currentTokens = authManager.getTokens()
            if (currentTokens == null) return@withLock OAuthTokenResult.NoSession

            // Re-evaluate — now fresh enough?
            if (isFresh(currentTokens)) {
                return@withLock OAuthTokenResult.Available(currentTokens, refreshed = false)
            }

            performRefresh(currentTokens, forceTransientFailure = false)
        }
    }

    /**
     * Force a token refresh, regardless of the current token's validity.
     *
     * Rules:
     * 1. If [rejectedAccessToken] differs from the stored access token, another
     *    coroutine already refreshed → return [Available](refreshed=false).
     * 2. Acquire [refreshMutex], re-read tokens, re-check.
     * 3. Call [OAuthRefreshService.refresh].
     * 4. Success → persist, [Available](refreshed=true).
     * 5. Transient → [TemporarilyUnavailable] (even if old token is still valid).
     * 6. [ReauthenticationRequired] → propagated.
     */
    suspend fun forceRefresh(rejectedAccessToken: String? = null): OAuthTokenResult {
        if (isReauthPending.get()) {
            return OAuthTokenResult.ReauthenticationRequired
        }
        val initialTokens = authManager.getTokens() ?: return OAuthTokenResult.NoSession

        // Another coroutine already refreshed with a different token
        if (rejectedAccessToken != null && rejectedAccessToken != initialTokens.accessToken) {
            return OAuthTokenResult.Available(initialTokens, refreshed = false)
        }

        return refreshMutex.withLock {
            if (isReauthPending.get()) {
                return@withLock OAuthTokenResult.ReauthenticationRequired
            }
            // Re-read and re-check after acquiring mutex
            val currentTokens = authManager.getTokens()
            if (currentTokens == null) return@withLock OAuthTokenResult.NoSession

            if (rejectedAccessToken != null && rejectedAccessToken != currentTokens.accessToken) {
                return@withLock OAuthTokenResult.Available(currentTokens, refreshed = false)
            }

            performRefresh(currentTokens, forceTransientFailure = true)
        }
    }

    // ── Internal ────────────────────────────────────────────────

    /**
     * True if the token has more than [REFRESH_MARGIN_MS] of life remaining.
     */
    private fun isFresh(tokens: OAuthTokens): Boolean {
        return nowEpochMillis() + REFRESH_MARGIN_MS < tokens.expiresAtEpochMillis
    }

    /**
     * Execute a single refresh request and classify the result.
     * Caller must hold [refreshMutex].
     *
     * @param forceTransientFailure When true, transient errors always return
     *   [TemporarilyUnavailable] even if the old token is still valid.
     *   When false, the old token is returned if still valid.
     */
    private suspend fun performRefresh(
        tokens: OAuthTokens,
        forceTransientFailure: Boolean
    ): OAuthTokenResult {
        val refreshResult = try {
            refreshService.refresh(tokens.refreshToken)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            OAuthRefreshResult.TransientFailure
        }

        return when (refreshResult) {
            is OAuthRefreshResult.Success -> {
                val now = nowEpochMillis()
                val updatedTokens = tokens.copy(
                    accessToken = refreshResult.accessToken,
                    expiresAtEpochMillis = now + refreshResult.expiresInSeconds * 1000L
                )
                // Persist the updated tokens (keeps the existing refresh token)
                authManager.saveTokens(updatedTokens)
                OAuthTokenResult.Available(updatedTokens, refreshed = true)
            }

            is OAuthRefreshResult.TransientFailure -> {
                if (forceTransientFailure || nowEpochMillis() >= tokens.expiresAtEpochMillis) {
                    OAuthTokenResult.TemporarilyUnavailable
                } else {
                    OAuthTokenResult.Available(tokens, refreshed = false)
                }
            }

            is OAuthRefreshResult.ReauthenticationRequired -> {
                // Activate latch and publish event
                if (isReauthPending.compareAndSet(false, true)) {
                    reauthChannel.trySend(Unit)
                }
                // Propagate without deleting tokens
                OAuthTokenResult.ReauthenticationRequired
            }
        }
    }
}
