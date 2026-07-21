package com.david.mailapp.core.di

import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class SessionInvalidationTest {

    private fun makeCoordinator(
        authenticatedRef: BooleanArray = booleanArrayOf(true),
        onClearProvider: () -> Unit = {},
        onClearDatabase: suspend () -> Unit = {},
        onClearPdf: () -> Unit = {},
        onClearHistory: suspend () -> Unit = {},
        onClearCredentials: suspend () -> Unit = {},
        onReactivate: () -> Unit = {}
    ): SessionCoordinator = SessionCoordinator(
        clearProvider = onClearProvider,
        clearDatabase = onClearDatabase,
        clearPdfCache = onClearPdf,
        clearSearchHistory = onClearHistory,
        clearCredentials = onClearCredentials,
        isAuthenticated = { authenticatedRef[0] },
        reactivateProvider = onReactivate
    )

    @Test
    fun `invalidation order - provider db pdf history credentials`() = runBlocking {
        val order = mutableListOf<String>()
        makeCoordinator(
            onClearProvider = { order.add("provider") },
            onClearDatabase = { order.add("db") },
            onClearPdf = { order.add("pdf") },
            onClearHistory = { order.add("history") },
            onClearCredentials = { order.add("credentials") }
        ).invalidateExpiredSession()
        assertEquals(listOf("provider", "db", "pdf", "history", "credentials"), order)
    }

    @Test
    fun `clearProvider lanza exception - continúan los demás pasos y credenciales exactamente una vez`() = runBlocking {
        val executed = mutableSetOf<String>()
        val credCount = AtomicInteger(0)
        makeCoordinator(
            onClearProvider = { executed.add("provider"); throw RuntimeException("provider failed") },
            onClearDatabase = { executed.add("db") },
            onClearPdf = { executed.add("pdf") },
            onClearHistory = { executed.add("history") },
            onClearCredentials = { executed.add("credentials"); credCount.incrementAndGet() }
        ).invalidateExpiredSession()
        assertEquals(setOf("provider", "db", "pdf", "history", "credentials"), executed)
        assertEquals(1, credCount.get())
    }

    @Test
    fun `Room lanza excepcion ordinaria - continúan PDF, historial y credenciales`() = runBlocking {
        val executed = mutableSetOf<String>()
        makeCoordinator(
            onClearProvider = { executed.add("provider") },
            onClearDatabase = { executed.add("db"); throw RuntimeException("db failed") },
            onClearPdf = { executed.add("pdf") },
            onClearHistory = { executed.add("history") },
            onClearCredentials = { executed.add("credentials") }
        ).invalidateExpiredSession()
        assertEquals(setOf("provider", "db", "pdf", "history", "credentials"), executed)
    }

    @Test
    fun `Cancelacion mientras Room esta suspendido - ejecuta clearCredentials y propaga cancelacion`() = runBlocking {
        val enteredRoom = kotlinx.coroutines.CompletableDeferred<Unit>()
        val blockRoom = kotlinx.coroutines.CompletableDeferred<Unit>()
        val credCount = AtomicInteger(0)
        val coordinator = makeCoordinator(
            onClearProvider = {},
            onClearDatabase = {
                enteredRoom.complete(Unit)
                blockRoom.await()
            },
            onClearPdf = {},
            onClearHistory = {},
            onClearCredentials = { credCount.incrementAndGet() }
        )

        val job = launch {
            coordinator.invalidateExpiredSession()
        }

        enteredRoom.await() // Wait until Room is suspended
        job.cancel() // Cancel the job while it's waiting in Room

        job.join()
        
        assertTrue("Job is cancelled", job.isCancelled)
        assertEquals("Credentials should be cleared exactly once in finally block", 1, credCount.get())
    }

    @Test
    fun `cancelación mientras isAuthenticated está suspendido limpia credenciales y propaga cancelación`() = runBlocking {
        val enteredAuth = kotlinx.coroutines.CompletableDeferred<Unit>()
        val blockAuth = kotlinx.coroutines.CompletableDeferred<Unit>()
        val executed = mutableSetOf<String>()
        val credCount = AtomicInteger(0)
        val reactivateCount = AtomicInteger(0)

        val coordinator = SessionCoordinator(
            clearProvider = { executed.add("provider") },
            clearDatabase = { executed.add("db") },
            clearPdfCache = { executed.add("pdf") },
            clearSearchHistory = { executed.add("history") },
            clearCredentials = { credCount.incrementAndGet() },
            isAuthenticated = {
                enteredAuth.complete(Unit)
                blockAuth.await()
                true
            },
            reactivateProvider = { reactivateCount.incrementAndGet() }
        )

        val job = launch {
            coordinator.invalidateExpiredSession()
        }

        enteredAuth.await() // Wait until isAuthenticated is suspended
        job.cancel() // Cancel the job

        job.join()

        assertTrue("Job is cancelled", job.isCancelled)
        assertEquals("Credentials should be cleared exactly once", 1, credCount.get())
        assertTrue("No other cleanups executed", executed.isEmpty())
        assertEquals("Provider not reactivated", 0, reactivateCount.get())
    }

    @Test
    fun `isAuthenticated lanza excepcion - ejecuta invalidacion fail-closed y limpia credenciales`() = runBlocking {
        val credCount = AtomicInteger(0)
        val coordinator = SessionCoordinator(
            clearProvider = {},
            clearDatabase = {},
            clearPdfCache = {},
            clearSearchHistory = {},
            clearCredentials = { credCount.incrementAndGet() },
            isAuthenticated = { throw RuntimeException("auth check failed") },
            reactivateProvider = {}
        )
        val result = coordinator.invalidateExpiredSession()
        assertTrue(result is SessionCoordinator.InvalidationResult.Completed)
        assertEquals(1, credCount.get())
    }

    @Test
    fun `clearCredentials lanza excepcion - excepcion no se oculta, no Completed, no reactiva provider`() = runBlocking {
        val reactivateCount = AtomicInteger(0)
        val coordinator = SessionCoordinator(
            clearProvider = {},
            clearDatabase = {},
            clearPdfCache = {},
            clearSearchHistory = {},
            clearCredentials = { throw RuntimeException("credentials failed") },
            isAuthenticated = { true },
            reactivateProvider = { reactivateCount.incrementAndGet() }
        )
        var exceptionThrown = false
        var result: SessionCoordinator.InvalidationResult? = null
        try {
            result = coordinator.invalidateExpiredSession()
        } catch (e: Exception) {
            exceptionThrown = true
            assertEquals("credentials failed", e.message)
        }
        assertTrue("Exception must be propagated", exceptionThrown)
        assertTrue("Result must not be returned", result == null)
        assertEquals("Provider must not be reactivated", 0, reactivateCount.get())
    }

    @Test
    fun `manual logout failed - automatic invalidation runs cleanup`() = runBlocking {
        val auth = booleanArrayOf(true)
        val credCount = AtomicInteger(0)
        val coordinator = SessionCoordinator(
            clearProvider = {},
            clearDatabase = { throw RuntimeException("db failed") },
            clearPdfCache = {}, clearSearchHistory = {},
            clearCredentials = { credCount.incrementAndGet(); auth[0] = false },
            isAuthenticated = { auth[0] },
            reactivateProvider = {}
        )
        val result = coordinator.signOut()
        assertTrue(result is SessionCoordinator.SignOutResult.Failed)
        assertTrue("Sigue autenticado tras logout fallido", auth[0])
        coordinator.invalidateExpiredSession()
        assertFalse("No autenticado tras invalidación", auth[0])
        assertEquals(1, credCount.get())
    }

    @Test
    fun `concurrent manual logout and invalidation - uses barrier for single cleanup`() = runBlocking {
        val enteredMutex = kotlinx.coroutines.CompletableDeferred<Unit>()
        val blockMutex = kotlinx.coroutines.CompletableDeferred<Unit>()
        var auth = true
        val credCount = AtomicInteger(0)
        val coordinator = SessionCoordinator(
            clearProvider = {}, 
            clearDatabase = {
                // Block the first one to enter
                if (enteredMutex.isActive) {
                    enteredMutex.complete(Unit)
                    blockMutex.await()
                }
            }, 
            clearPdfCache = {}, clearSearchHistory = {},
            clearCredentials = { credCount.incrementAndGet(); auth = false },
            isAuthenticated = { auth },
            reactivateProvider = {}
        )
        val manual = async { coordinator.signOut() }
        enteredMutex.await() // Ensure manual has entered and holds mutex
        
        val auto = async { coordinator.invalidateExpiredSession() }
        kotlinx.coroutines.yield() // Ensure auto is waiting for mutex
        
        blockMutex.complete(Unit) // unblock manual
        
        manual.await()
        auto.await()
        assertEquals("Credenciales limpiadas una sola vez", 1, credCount.get())
        assertFalse(auth)
    }

    @Test
    fun `AlreadySignedOut - continua sin ejecutar ninguna limpieza`() = runBlocking {
        val executed = mutableSetOf<String>()
        val auth = booleanArrayOf(true)
        val coordinator = SessionCoordinator(
            clearProvider = { executed.add("provider") },
            clearDatabase = { executed.add("db") },
            clearPdfCache = { executed.add("pdf") },
            clearSearchHistory = { executed.add("history") },
            clearCredentials = { executed.add("credentials"); auth[0] = false },
            isAuthenticated = { auth[0] },
            reactivateProvider = {}
        )
        val signOutResult = coordinator.signOut()
        assertTrue(signOutResult is SessionCoordinator.SignOutResult.Success)
        
        executed.clear() // reset to see what invalidate does
        
        val invalidationResult = coordinator.invalidateExpiredSession()
        assertTrue(invalidationResult is SessionCoordinator.InvalidationResult.AlreadySignedOut)
        assertTrue("No cleanup steps executed", executed.isEmpty())
    }
}
