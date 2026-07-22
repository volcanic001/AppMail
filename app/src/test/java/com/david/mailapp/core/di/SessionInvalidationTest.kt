package com.david.mailapp.core.di

import com.david.mailapp.core.auth.OAuthRevocationService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class SessionInvalidationTest {

    private class FakeRevocationService(
        val onRevoke: suspend (String) -> Unit = {}
    ) : OAuthRevocationService {
        val revokeCount = AtomicInteger(0)
        override suspend fun revoke(refreshToken: String) {
            revokeCount.incrementAndGet()
            onRevoke(refreshToken)
        }
    }

    private fun makeCoordinator(
        authenticatedRef: BooleanArray = booleanArrayOf(true),
        onClearProvider: suspend () -> Unit = {},
        onClearDatabase: suspend () -> Unit = {},
        onClearPdf: () -> Unit = {},
        onClearHistory: suspend () -> Unit = {},
        onClearCredentials: suspend () -> Unit = {},
        onReactivate: suspend () -> Unit = {},
        onReadRefreshToken: suspend () -> String? = { null },
        revocationService: OAuthRevocationService? = null,
        revocationTimeoutMillis: Long = 3_000L
    ): SessionCoordinator = SessionCoordinator(
        clearProvider = onClearProvider,
        clearDatabase = onClearDatabase,
        clearPdfCache = onClearPdf,
        clearSearchHistory = onClearHistory,
        clearCredentials = onClearCredentials,
        isAuthenticated = { authenticatedRef[0] },
        reactivateProvider = onReactivate,
        readRefreshToken = onReadRefreshToken,
        revocationService = revocationService,
        revocationTimeoutMillis = revocationTimeoutMillis
    )

    // Tests de línea base (Fase 1C.2 y adaptaciones)

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
        val enteredRoom = CompletableDeferred<Unit>()
        val blockRoom = CompletableDeferred<Unit>()
        val credCount = AtomicInteger(0)
        val coordinator = makeCoordinator(
            onClearDatabase = {
                enteredRoom.complete(Unit)
                blockRoom.await()
            },
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
        val enteredAuth = CompletableDeferred<Unit>()
        val blockAuth = CompletableDeferred<Unit>()
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
        val enteredMutex = CompletableDeferred<Unit>()
        val blockMutex = CompletableDeferred<Unit>()
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

    // --- Nuevas Pruebas Fase 1C.3-B ---

    @Test
    fun `1 Orden exitoso exacto de signOut manual`() = runBlocking {
        val order = mutableListOf<String>()
        val fakeRevocation = FakeRevocationService { order.add("revoke") }
        val coordinator = makeCoordinator(
            onClearProvider = { order.add("provider") },
            onClearDatabase = { order.add("db") },
            onClearPdf = { order.add("pdf") },
            onClearHistory = { order.add("history") },
            onReadRefreshToken = { order.add("readToken"); "token123" },
            onClearCredentials = { order.add("credentials") },
            revocationService = fakeRevocation
        )

        val result = coordinator.signOut()
        assertTrue(result is SessionCoordinator.SignOutResult.Success)
        assertEquals(listOf("provider", "db", "pdf", "history", "readToken", "credentials", "revoke"), order)
        assertEquals(1, fakeRevocation.revokeCount.get())
    }

    @Test
    fun `2 Token valido ejecuta revocacion y Success`() = runBlocking {
        val fakeRevocation = FakeRevocationService()
        val coordinator = makeCoordinator(
            onReadRefreshToken = { "valid_token" },
            revocationService = fakeRevocation
        )
        val result = coordinator.signOut()
        assertTrue(result is SessionCoordinator.SignOutResult.Success)
        assertEquals(1, fakeRevocation.revokeCount.get())
    }

    @Test
    fun `3 Token no disponible (null, blank, excepcion) no revoca y Success`() = runBlocking {
        val cases = listOf<suspend () -> String?>(
            { null },
            { "" },
            { "   " },
            { throw RuntimeException("Error reading token") }
        )
        for (readToken in cases) {
            val fakeRevocation = FakeRevocationService()
            val credCount = AtomicInteger(0)
            val coordinator = makeCoordinator(
                onReadRefreshToken = readToken,
                onClearCredentials = { credCount.incrementAndGet() },
                revocationService = fakeRevocation
            )
            val result = coordinator.signOut()
            assertTrue(result is SessionCoordinator.SignOutResult.Success)
            assertEquals(1, credCount.get())
            assertEquals(0, fakeRevocation.revokeCount.get())
        }
    }

    @Test
    fun `4 Error remoto lanza excepcion pero completa con Success`() = runBlocking {
        val cases = listOf(
            RuntimeException("Remote error"),
            CancellationException("Remote cancel")
        )
        for (exception in cases) {
            val fakeRevocation = FakeRevocationService { throw exception }
            val reactivateCount = AtomicInteger(0)
            val credCount = AtomicInteger(0)
            val coordinator = makeCoordinator(
                onReadRefreshToken = { "token123" },
                onClearCredentials = { credCount.incrementAndGet() },
                onReactivate = { reactivateCount.incrementAndGet() },
                revocationService = fakeRevocation
            )
            val result = coordinator.signOut()
            assertTrue(result is SessionCoordinator.SignOutResult.Success)
            assertEquals(1, credCount.get())
            assertEquals(1, fakeRevocation.revokeCount.get())
            assertEquals(0, reactivateCount.get())
        }
    }

    @Test
    fun `5 Timeout interrumpe revocacion pero devuelve Success`() = runBlocking {
        val fakeRevocation = FakeRevocationService {
            awaitCancellation() // Bloquea infinitamente
        }
        val reactivateCount = AtomicInteger(0)
        val credCount = AtomicInteger(0)
        val coordinator = makeCoordinator(
            onReadRefreshToken = { "token" },
            onClearCredentials = { credCount.incrementAndGet() },
            onReactivate = { reactivateCount.incrementAndGet() },
            revocationService = fakeRevocation,
            revocationTimeoutMillis = 100L // Timeout muy corto
        )

        withTimeout(2000L) {
            val result = coordinator.signOut()
            assertTrue(result is SessionCoordinator.SignOutResult.Success)
        }
        assertEquals(1, credCount.get())
        assertEquals(1, fakeRevocation.revokeCount.get())
        assertEquals(0, reactivateCount.get())
    }

    @Test
    fun `6 Fallos pre-commit devuelven Failed sin leer token ni revocar e intentan reactivar`() = runBlocking {
        val steps = listOf("provider", "db", "pdf", "history")
        for (step in steps) {
            val reactivateCount = AtomicInteger(0)
            val credCount = AtomicInteger(0)
            val readTokenCount = AtomicInteger(0)
            val fakeRevocation = FakeRevocationService()

            val coordinator = makeCoordinator(
                onClearProvider = { if (step == "provider") throw RuntimeException() },
                onClearDatabase = { if (step == "db") throw RuntimeException() },
                onClearPdf = { if (step == "pdf") throw RuntimeException() },
                onClearHistory = { if (step == "history") throw RuntimeException() },
                onReadRefreshToken = {
                    readTokenCount.incrementAndGet()
                    "token"
                },
                onClearCredentials = { credCount.incrementAndGet() },
                onReactivate = { reactivateCount.incrementAndGet() },
                revocationService = fakeRevocation
            )

            val result = coordinator.signOut()
            assertTrue(result is SessionCoordinator.SignOutResult.Failed)
            assertEquals(0, readTokenCount.get())
            assertEquals(0, credCount.get())
            assertEquals(0, fakeRevocation.revokeCount.get())
            assertEquals(1, reactivateCount.get())
        }
    }

    @Test
    fun `7 Fallo de clearCredentials no compromite el flag y permite reintento`() = runBlocking {
        val reactivateCount = AtomicInteger(0)
        val credCount = AtomicInteger(0)
        val auth = booleanArrayOf(true)
        val fakeRevocation = FakeRevocationService()

        val coordinator = makeCoordinator(
            authenticatedRef = auth,
            onClearCredentials = {
                if (credCount.incrementAndGet() == 1) {
                    throw RuntimeException("fail")
                } else {
                    auth[0] = false
                }
            },
            onReactivate = { reactivateCount.incrementAndGet() },
            onReadRefreshToken = { "token" },
            revocationService = fakeRevocation
        )

        val result1 = coordinator.signOut()
        assertTrue(result1 is SessionCoordinator.SignOutResult.Failed)
        assertEquals(1, reactivateCount.get())
        assertEquals(0, fakeRevocation.revokeCount.get())
        assertTrue(auth[0])

        val result2 = coordinator.signOut()
        assertTrue(result2 is SessionCoordinator.SignOutResult.Success)
        assertEquals(2, credCount.get())
        assertEquals(1, fakeRevocation.revokeCount.get())
    }

    @Test
    fun `8 Cancelacion antes del commit propaga cancelacion, ejecuta rollback y no revoca`() = runBlocking {
        val fakeRevocation = FakeRevocationService()
        val reactivateCount = AtomicInteger(0)
        val credCount = AtomicInteger(0)
        val enteredRoom = CompletableDeferred<Unit>()
        val blockRoom = CompletableDeferred<Unit>()

        val coordinator = makeCoordinator(
            onClearDatabase = {
                enteredRoom.complete(Unit)
                blockRoom.await()
            },
            onClearCredentials = { credCount.incrementAndGet() },
            onReactivate = { reactivateCount.incrementAndGet() },
            revocationService = fakeRevocation
        )

        val job = launch {
            try {
                coordinator.signOut()
                fail("Should throw CancellationException")
            } catch (e: CancellationException) {
                // expected
            }
        }

        enteredRoom.await()
        job.cancel()
        job.join()

        assertEquals(0, credCount.get())
        assertEquals(0, fakeRevocation.revokeCount.get())
        assertEquals(1, reactivateCount.get())

        // Ahora cancelar en readRefreshToken
        val enteredRead = CompletableDeferred<Unit>()
        val blockRead = CompletableDeferred<Unit>()
        val reactivateCount2 = AtomicInteger(0)
        val credCount2 = AtomicInteger(0)
        val fakeRevocation2 = FakeRevocationService()
        val coordinator2 = makeCoordinator(
            onReadRefreshToken = {
                enteredRead.complete(Unit)
                blockRead.await()
                "token"
            },
            onClearCredentials = { credCount2.incrementAndGet() },
            onReactivate = { reactivateCount2.incrementAndGet() },
            revocationService = fakeRevocation2
        )

        val job2 = launch {
            try {
                coordinator2.signOut()
                fail("Should throw CancellationException")
            } catch (e: CancellationException) {
                // expected
            }
        }

        enteredRead.await()
        job2.cancel()
        job2.join()

        assertEquals(0, credCount2.get())
        assertEquals(0, fakeRevocation2.revokeCount.get())
        assertEquals(1, reactivateCount2.get())
    }

    @Test
    fun `9 Dos logouts manuales concurrentes solo uno avanza`() = runBlocking {
        val enteredMutex = CompletableDeferred<Unit>()
        val blockMutex = CompletableDeferred<Unit>()
        val credCount = AtomicInteger(0)
        val fakeRevocation = FakeRevocationService()

        val coordinator = makeCoordinator(
            onClearDatabase = {
                if (enteredMutex.isActive) {
                    enteredMutex.complete(Unit)
                    blockMutex.await()
                }
            },
            onClearCredentials = { credCount.incrementAndGet() },
            onReadRefreshToken = { "token" },
            revocationService = fakeRevocation
        )

        val job1 = async { coordinator.signOut() }
        enteredMutex.await() // job1 está en db

        val job2 = async { coordinator.signOut() }
        val result2 = job2.await()
        assertTrue(result2 is SessionCoordinator.SignOutResult.Failed)
        assertEquals("Ya hay un cierre de sesión en curso.", (result2 as SessionCoordinator.SignOutResult.Failed).message)

        blockMutex.complete(Unit)
        val result1 = job1.await()
        assertTrue(result1 is SessionCoordinator.SignOutResult.Success)

        assertEquals(1, credCount.get())
        assertEquals(1, fakeRevocation.revokeCount.get())
    }

    @Test
    fun `10 Carrera manual primero - manual hace todo, invalidacion no hace nada`() = runBlocking {
        val enteredMutex = CompletableDeferred<Unit>()
        val blockMutex = CompletableDeferred<Unit>()
        val auth = booleanArrayOf(true)
        val credCount = AtomicInteger(0)
        val fakeRevocation = FakeRevocationService()

        val coordinator = makeCoordinator(
            authenticatedRef = auth,
            onClearDatabase = {
                if (enteredMutex.isActive) {
                    enteredMutex.complete(Unit)
                    blockMutex.await()
                }
            },
            onClearCredentials = { credCount.incrementAndGet(); auth[0] = false },
            onReadRefreshToken = { "token" },
            revocationService = fakeRevocation
        )

        val manual = async { coordinator.signOut() }
        enteredMutex.await() // manual holds lock

        val auto = async { coordinator.invalidateExpiredSession() }
        kotlinx.coroutines.yield()

        blockMutex.complete(Unit)
        val resManual = manual.await()
        val resAuto = auto.await()

        assertTrue(resManual is SessionCoordinator.SignOutResult.Success)
        assertTrue(resAuto is SessionCoordinator.InvalidationResult.AlreadySignedOut)
        assertEquals(1, credCount.get())
        assertEquals(1, fakeRevocation.revokeCount.get())
    }

    @Test
    fun `11 Carrera automatica primero - auto limpia, manual devuelve success sin repetir`() = runBlocking {
        val enteredMutex = CompletableDeferred<Unit>()
        val blockMutex = CompletableDeferred<Unit>()
        val auth = booleanArrayOf(true)
        val credCount = AtomicInteger(0)
        val fakeRevocation = FakeRevocationService()

        val coordinator = makeCoordinator(
            authenticatedRef = auth,
            onClearDatabase = {
                if (enteredMutex.isActive) {
                    enteredMutex.complete(Unit)
                    blockMutex.await()
                }
            },
            onClearCredentials = { credCount.incrementAndGet(); auth[0] = false },
            onReadRefreshToken = { "token" },
            revocationService = fakeRevocation
        )

        val auto = async { coordinator.invalidateExpiredSession() }
        enteredMutex.await() // auto holds lock

        val manual = async { coordinator.signOut() }
        kotlinx.coroutines.yield()

        blockMutex.complete(Unit)
        val resAuto = auto.await()
        val resManual = manual.await()

        assertTrue(resAuto is SessionCoordinator.InvalidationResult.Completed)
        assertTrue(resManual is SessionCoordinator.SignOutResult.Success)
        assertEquals("Auto cleans only once", 1, credCount.get())
        assertEquals("Manual should not revoke since it was already done by auto effectively without network", 0, fakeRevocation.revokeCount.get())
    }

    @Test
    fun `12 Nueva sesion despues de commit permite otro ciclo completo`() = runBlocking {
        val auth = booleanArrayOf(true)
        val credCount = AtomicInteger(0)
        val fakeRevocation = FakeRevocationService()

        val coordinator = makeCoordinator(
            authenticatedRef = auth,
            onClearCredentials = { credCount.incrementAndGet(); auth[0] = false },
            onReadRefreshToken = { "token" },
            revocationService = fakeRevocation
        )

        // Primer logout
        coordinator.signOut()
        assertEquals(1, credCount.get())
        assertEquals(1, fakeRevocation.revokeCount.get())

        // Simular inicio de sesion (no reseteamos credCount para ver si suma)
        auth[0] = true

        // Segundo logout
        val res2 = coordinator.signOut()
        assertTrue(res2 is SessionCoordinator.SignOutResult.Success)
        assertEquals(2, credCount.get())
        assertEquals(2, fakeRevocation.revokeCount.get())
    }

    @Test
    fun `13 Invalidacion automatica aislada nunca revoca`() = runBlocking {
        val auth = booleanArrayOf(true)
        val fakeRevocation = FakeRevocationService()
        val coordinator = makeCoordinator(
            authenticatedRef = auth,
            onClearCredentials = { auth[0] = false },
            revocationService = fakeRevocation
        )

        val res = coordinator.invalidateExpiredSession()
        assertTrue(res is SessionCoordinator.InvalidationResult.Completed)
        assertEquals(0, fakeRevocation.revokeCount.get())
    }

    @Test
    fun `14 Timeout invalido rechaza constructor`() = runBlocking {
        var failed = false
        try {
            makeCoordinator(revocationTimeoutMillis = 0L)
        } catch (e: IllegalArgumentException) {
            failed = true
        }
        assertTrue("Debe fallar con timeout 0", failed)

        failed = false
        try {
            makeCoordinator(revocationTimeoutMillis = -100L)
        } catch (e: IllegalArgumentException) {
            failed = true
        }
        assertTrue("Debe fallar con timeout negativo", failed)
    }

    @Test
    fun `15 Cancelacion externa despues del commit`() = runBlocking {
        val authenticatedRef = booleanArrayOf(true)
        val credCount = AtomicInteger(0)
        val reactivateCount = AtomicInteger(0)
        val revocationStarted = CompletableDeferred<Unit>()
        val allowRevocationToFinish = CompletableDeferred<Unit>()

        val fakeRevocation = FakeRevocationService {
            revocationStarted.complete(Unit)
            allowRevocationToFinish.await()
        }

        val coordinator = makeCoordinator(
            authenticatedRef = authenticatedRef,
            onClearCredentials = {
                credCount.incrementAndGet()
                authenticatedRef[0] = false
            },
            onReactivate = { reactivateCount.incrementAndGet() },
            onReadRefreshToken = { "token" },
            revocationService = fakeRevocation,
            revocationTimeoutMillis = 10_000L
        )

        val job = launch {
            coordinator.signOut()
        }

        withTimeout(5000L) {
            revocationStarted.await()
        }
        job.cancel()
        allowRevocationToFinish.complete(Unit)
        job.join()

        assertTrue(job.isCancelled)
        assertEquals(1, credCount.get())
        assertEquals(1, fakeRevocation.revokeCount.get())
        assertEquals(0, reactivateCount.get())
        assertFalse(authenticatedRef[0])

        val result2 = coordinator.signOut()
        assertTrue(result2 is SessionCoordinator.SignOutResult.Success)
        assertEquals(1, credCount.get())
        assertEquals(1, fakeRevocation.revokeCount.get())
    }

    @Test
    fun `16 Fallo durante la reactivacion no se propaga y devuelve Failed`() = runBlocking {
        val reactivateCount = AtomicInteger(0)
        val credCount = AtomicInteger(0)
        val fakeRevocation = FakeRevocationService()

        val coordinator = makeCoordinator(
            onClearDatabase = { throw RuntimeException("db error") },
            onClearCredentials = { credCount.incrementAndGet() },
            onReactivate = {
                reactivateCount.incrementAndGet()
                throw RuntimeException("reactivation error")
            },
            revocationService = fakeRevocation
        )

        val result = coordinator.signOut()
        assertTrue(result is SessionCoordinator.SignOutResult.Failed)
        assertEquals("No se pudo cerrar sesión. Inténtalo nuevamente.", (result as SessionCoordinator.SignOutResult.Failed).message)
        assertEquals(1, reactivateCount.get())
        assertEquals(0, credCount.get())
        assertEquals(0, fakeRevocation.revokeCount.get())
    }
}
