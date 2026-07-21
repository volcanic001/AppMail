package com.david.mailapp.core.di

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Coordinates session termination between manual logout and automatic
 * invalidation triggered by invalid_grant.
 *
 * Manual logout and automatic invalidation are serialized by [terminationMutex].
 * If the manual logout already cleared credentials, automatic invalidation is a no-op.
 * Credentials are always cleared in a non-cancellable block.
 */
class SessionCoordinator(
    private val clearProvider: suspend () -> Unit,
    private val clearDatabase: suspend () -> Unit,
    private val clearPdfCache: () -> Unit,
    private val clearSearchHistory: suspend () -> Unit,
    private val clearCredentials: suspend () -> Unit,
    private val isAuthenticated: suspend () -> Boolean,
    private val reactivateProvider: suspend () -> Unit
) {
    internal val terminationMutex = Mutex()
    private val isManualSigningOut = AtomicBoolean(false)

    sealed interface SignOutResult {
        data object Success : SignOutResult
        data class Failed(val message: String) : SignOutResult
    }

    /**
     * Result of [invalidateExpiredSession].
     *
     * - [Completed]: the automatic cleanup ran (session was authenticated).
     * - [AlreadySignedOut]: the session was already gone (manual logout had
     *   already cleared credentials) — the UI must NOT show the expiry message.
     */
    sealed interface InvalidationResult {
        data object Completed : InvalidationResult
        data object AlreadySignedOut : InvalidationResult
    }

    suspend fun signOut(): SignOutResult {
        if (!isManualSigningOut.compareAndSet(false, true)) {
            return SignOutResult.Failed("Ya hay un cierre de sesión en curso.")
        }
        return try {
            terminationMutex.withLock {
                try {
                    clearProvider()
                    withContext(Dispatchers.IO) { clearDatabase() }
                    clearPdfCache()
                    clearSearchHistory()
                    clearCredentials()
                    SignOutResult.Success
                } catch (_: Exception) {
                    reactivateProvider()
                    SignOutResult.Failed("No se pudo cerrar sesión. Inténtalo nuevamente.")
                }
            }
        } finally {
            isManualSigningOut.set(false)
        }
    }

    suspend fun invalidateExpiredSession(): InvalidationResult {
        return terminationMutex.withLock {
            var credentialsMustBeCleared = false
            try {
                val isAuth = try {
                    isAuthenticated()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    credentialsMustBeCleared = true
                    throw e
                } catch (e: Exception) {
                    // Si la lectura de estado falla, asumimos que está autenticado (fail-closed)
                    true
                }

                if (!isAuth) return@withLock InvalidationResult.AlreadySignedOut

                credentialsMustBeCleared = true

                try {
                    clearProvider()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Fallo al limpiar provider. Continuamos con el resto.
                }

                withContext(Dispatchers.IO) {
                    try {
                        clearDatabase()
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Fallo al limpiar base de datos. Continuamos.
                    }
                }

                try {
                    clearPdfCache()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Fallo al limpiar caché PDF. Continuamos.
                }

                try {
                    clearSearchHistory()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Fallo al limpiar historial. Continuamos.
                }

                InvalidationResult.Completed
            } finally {
                if (credentialsMustBeCleared) {
                    withContext(NonCancellable) {
                        clearCredentials()
                    }
                }
            }
        }
    }
}
