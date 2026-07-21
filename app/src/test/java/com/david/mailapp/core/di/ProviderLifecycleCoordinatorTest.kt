package com.david.mailapp.core.di

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class ProviderLifecycleCoordinatorTest {

    private class DummyProvider(val client: HttpClient)

    @Test
    fun `activaciones concurrentes o repetidas - una sola instancia y ningun cliente filtrado`() = runBlocking {
        val clientCreations = AtomicInteger(0)
        val providerCreations = AtomicInteger(0)
        val closures = AtomicInteger(0)
        var isPending = false
        val coordinator = ProviderLifecycleCoordinator(
            isReauthPending = { isPending },
            createClient = {
                clientCreations.incrementAndGet()
                HttpClient(MockEngine { respondOk() })
            },
            createProvider = {
                providerCreations.incrementAndGet()
                DummyProvider(it)
            },
            closeClient = { closures.incrementAndGet() }
        )

        val startTogether = CompletableDeferred<Unit>()
        val allReady = CompletableDeferred<Unit>()
        val readyCount = AtomicInteger(0)
        val jobs = List(10) {
            async {
                if (readyCount.incrementAndGet() == 10) {
                    allReady.complete(Unit)
                }
                startTogether.await()
                coordinator.activateProvider()
            }
        }
        allReady.await()
        startTogether.complete(Unit)
        jobs.awaitAll()

        assertEquals("Solo se debe haber creado un cliente", 1, clientCreations.get())
        assertEquals("Solo se debe haber creado un provider", 1, providerCreations.get())
        assertEquals("Ningun cierre en activa", 0, closures.get())
    }

    @Test
    fun `latch pendiente antes de activar - no se crea cliente o provider`() = runBlocking {
        val clientCreations = AtomicInteger(0)
        var isPending = true
        val coordinator = ProviderLifecycleCoordinator(
            isReauthPending = { isPending },
            createClient = {
                clientCreations.incrementAndGet()
                HttpClient(MockEngine { respondOk() })
            },
            createProvider = { DummyProvider(it) }
        )

        coordinator.activateProvider()

        assertEquals("No se debio crear ningun cliente", 0, clientCreations.get())
        assertNull("Provider debe ser null", coordinator.provider)
    }

    @Test
    fun `activacion concurrente bloqueada y desactivacion - estado final desactivado y cliente cerrado una vez`() = runBlocking {
        val blockCreate = CompletableDeferred<Unit>()
        val reachedCreate = CompletableDeferred<Unit>()
        val closures = AtomicInteger(0)

        val coordinator = ProviderLifecycleCoordinator(
            isReauthPending = { false },
            createClient = {
                reachedCreate.complete(Unit)
                runBlocking { blockCreate.await() }
                HttpClient(MockEngine { respondOk() })
            },
            createProvider = { DummyProvider(it) },
            closeClient = { closures.incrementAndGet() }
        )

        // Launch activation that will block in createClient
        val activateJob = launch { coordinator.activateProvider() }
        reachedCreate.await()

        // Launch deactivation while activation is holding the mutex
        val deactivateJob = launch { coordinator.deactivateProvider() }
        
        // Unblock creation
        blockCreate.complete(Unit)
        
        activateJob.join()
        deactivateJob.join()
        
        // State should be deactivated
        assertNull(coordinator.provider)
        assertEquals("Cliente debe haberse cerrado una vez", 1, closures.get())
    }

    @Test
    fun `desactivacion repetida - no duplica el cierre`() = runBlocking {
        val closures = AtomicInteger(0)
        val coordinator = ProviderLifecycleCoordinator(
            isReauthPending = { false },
            createClient = { HttpClient(MockEngine { respondOk() }) },
            createProvider = { DummyProvider(it) },
            closeClient = { closures.incrementAndGet() }
        )
        
        coordinator.activateProvider()
        
        coordinator.deactivateProvider()
        coordinator.deactivateProvider() // second call
        
        assertNull(coordinator.provider)
        assertEquals("Cliente debe haberse cerrado exactamente una vez", 1, closures.get())
    }

    @Test
    fun `fallo al crear provider cierra cliente y no publica estado parcial`() = runBlocking {
        val client = HttpClient(MockEngine { respondOk() })
        val closures = AtomicInteger(0)
        val coordinator = ProviderLifecycleCoordinator<DummyProvider>(
            isReauthPending = { false },
            createClient = { client },
            createProvider = { throw IllegalStateException("provider creation failed") },
            closeClient = {
                closures.incrementAndGet()
                it.close()
            }
        )

        var thrown: IllegalStateException? = null
        try {
            coordinator.activateProvider()
        } catch (e: IllegalStateException) {
            thrown = e
        }

        assertEquals("provider creation failed", thrown?.message)
        assertNull("No debe publicarse un provider parcial", coordinator.provider)
        assertEquals("El cliente creado debe cerrarse exactamente una vez", 1, closures.get())
    }
    
    @Test
    fun `rollback intenta reactivar con latch pendiente - permanece desactivado`() = runBlocking {
        var isPending = true // Reauth is pending (e.g. invalid_grant occurred)
        val coordinator = ProviderLifecycleCoordinator(
            isReauthPending = { isPending },
            createClient = { HttpClient(MockEngine { respondOk() }) },
            createProvider = { DummyProvider(it) }
        )
        
        // Try to reactivate (like in rollback)
        coordinator.activateProvider()
        
        assertNull("Provider should not reactivate because latch is pending", coordinator.provider)
    }
}
