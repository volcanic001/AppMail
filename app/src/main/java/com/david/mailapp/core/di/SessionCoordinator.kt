package com.david.mailapp.core.di

import com.david.mailapp.core.auth.OAuthRevocationService
import com.david.mailapp.core.session.SessionWriteGuard
import com.david.mailapp.data.pdf.PdfCacheClearResult
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
class SessionCoordinator internal constructor(
    private val clearProvider: suspend () -> Unit,
    private val clearDatabase: suspend () -> Unit,
    private val clearPdfCache: () -> PdfCacheClearResult,
    private val clearSearchHistory: suspend () -> Unit,
    private val clearCredentials: suspend () -> Unit,
    private val isAuthenticated: suspend () -> Boolean,
    private val reactivateProvider: suspend () -> Unit,
    private val writeGuard: SessionWriteGuard,
    private val setPendingPdfCleanup: suspend (Boolean) -> Unit = {},
    private val readRefreshToken: suspend () -> String? = { null },
    private val revocationService: OAuthRevocationService? = null,
    private val revocationTimeoutMillis: Long = DEFAULT_REVOCATION_TIMEOUT_MS
) {
    init {
        require(revocationTimeoutMillis > 0L) { "Revocation timeout must be positive" }
    }

    internal val terminationMutex = Mutex()
    private val isManualSigningOut = AtomicBoolean(false)
    private var credentialsCommitted = false

    private companion object {
        private const val DEFAULT_REVOCATION_TIMEOUT_MS = 3_000L
    }

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
                if (credentialsCommitted) {
                    val isAuth = try {
                        isAuthenticated()
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        true
                    }
                    if (!isAuth) {
                        return@withLock SignOutResult.Success
                    }
                    credentialsCommitted = false
                }

                writeGuard.invalidate()

                try {
                    clearProvider()
                    withContext(Dispatchers.IO) { clearDatabase() }

                    val pdfResult = clearPdfCache()
                    if (pdfResult is PdfCacheClearResult.Failure) {
                        safeReactivateProvider()
                        return@withLock SignOutResult.Failed("No se pudieron eliminar los archivos temporales. Reinténtalo.")
                    }

                    clearSearchHistory()

                    val refreshToken = try {
                        readRefreshToken()
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        null
                    }

                    withContext(NonCancellable) {
                        clearCredentials()
                        credentialsCommitted = true

                        if (!refreshToken.isNullOrBlank() && revocationService != null) {
                            try {
                                kotlinx.coroutines.withTimeout(revocationTimeoutMillis) {
                                    revocationService.revoke(refreshToken)
                                }
                            } catch (e: Exception) {
                                // Ignore all exceptions, including timeouts
                            }
                        }
                    }
                    SignOutResult.Success
                } catch (e: kotlinx.coroutines.CancellationException) {
                    safeReactivateProvider()
                    throw e
                } catch (e: Exception) {
                    safeReactivateProvider()
                    SignOutResult.Failed("No se pudo cerrar sesión. Inténtalo nuevamente.")
                }
            }
        } finally {
            isManualSigningOut.set(false)
        }
    }

    private suspend fun safeReactivateProvider() {
        if (!credentialsCommitted) {
            withContext(NonCancellable) {
                try {
                    reactivateProvider()
                } catch (e: Exception) {
                    // Ignore
                }
            }
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
                credentialsCommitted = false

                writeGuard.invalidate()

                credentialsMustBeCleared = true

                withContext(NonCancellable) {
                    // Persist before any fallible/cancellable cleanup step. If
                    // automatic termination is interrupted, the next login
                    // must still retry deletion of the previous account's PDFs.
                    setPendingPdfCleanup(true)
                }

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

                var pdfCleanupSucceeded = false
                try {
                    val result = clearPdfCache()
                    pdfCleanupSucceeded = result is PdfCacheClearResult.Success
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (_: Exception) {}

                if (pdfCleanupSucceeded) {
                    withContext(NonCancellable) {
                        setPendingPdfCleanup(false)
                    }
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
                        credentialsCommitted = true
                    }
                }
            }
        }
    }
}
