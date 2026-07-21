package com.david.mailapp.core.di

import io.ktor.client.HttpClient
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Coordinates the lifecycle of the email provider and its backing HTTP client.
 *
 * All transitions (activate / deactivate) are serialized by [mutex] so that
 * concurrent calls never produce a leaked client or a stale provider reference.
 *
 * Invariants:
 *  - At most one [HttpClient] and one provider exist at any time.
 *  - [deactivateProvider] always leaves the state fully cleared.
 *  - A late activation after invalidation (latch pending) is rejected.
 *  - [activateProvider] is idempotent: if a provider already exists, it is a no-op.
 *  - The retired [HttpClient] is closed exactly once.
 */
internal class ProviderLifecycleCoordinator<P>(
    private val isReauthPending: () -> Boolean,
    private val createClient: () -> HttpClient,
    private val createProvider: (HttpClient) -> P,
    private val closeClient: (HttpClient) -> Unit = { it.close() }
) {
    private val mutex = Mutex()
    
    @Volatile
    private var _provider: P? = null
    private var _httpClient: HttpClient? = null

    /** The currently active provider, or null. */
    val provider: P?
        get() = _provider

    /**
     * Creates and publishes a provider if none exists and reauthentication
     * is not pending.
     *
     * If a provider already exists, this is a no-op (idempotent).
     * If the reauth latch is active, no provider is created.
     */
    suspend fun activateProvider() {
        mutex.withLock {
            if (_provider != null) return@withLock           // already active
            if (isReauthPending()) return@withLock           // latch blocks creation
            val client = createClient()
            val provider = try {
                createProvider(client)
            } catch (e: Exception) {
                closeClient(client)
                throw e
            }
            _httpClient = client
            _provider = provider
        }
    }

    /**
     * Removes the current provider and closes the HTTP client exactly once.
     *
     * Safe to call multiple times — subsequent calls are no-ops.
     */
    suspend fun deactivateProvider() {
        mutex.withLock {
            val client = _httpClient
            _provider = null
            _httpClient = null
            if (client != null) {
                closeClient(client)
            }
        }
    }
}
