package com.david.mailapp.core.di

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class GlobalReauthenticationCollectorTest {

    @Test
    fun `el primer evento lanza excepcion ordinaria y evento posterior todavia se procesa`() = runBlocking {
        // We use flow {} to emit two events synchronously
        val events = flow {
            emit(Unit)
            emit(Unit)
        }
        val signal = MutableStateFlow(false)
        val callCount = AtomicInteger(0)
        val errorLogCount = AtomicInteger(0)

        val collector = GlobalReauthenticationCollector(
            reauthenticationEvents = events,
            invalidateExpiredSession = {
                val count = callCount.incrementAndGet()
                if (count == 1) {
                    throw RuntimeException("First invalidation failed")
                } else {
                    SessionCoordinator.InvalidationResult.Completed
                }
            },
            sessionExpiredSignal = signal,
            errorLogger = { errorLogCount.incrementAndGet() }
        )

        collector.collectEvents() // Directly runs to completion since the flow is finite

        assertTrue("Signal should be true on Completed", signal.value)
        assertEquals("Invalidation should be called twice", 2, callCount.get())
        assertEquals("Error should be logged exactly once", 1, errorLogCount.get())
    }

    @Test
    fun `Completed activa la senal`() = runBlocking {
        val events = flowOf(Unit)
        val signal = MutableStateFlow(false)
        val collector = GlobalReauthenticationCollector(
            reauthenticationEvents = events,
            invalidateExpiredSession = { SessionCoordinator.InvalidationResult.Completed },
            sessionExpiredSignal = signal
        )
        
        collector.collectEvents()

        assertTrue(signal.value)
    }

    @Test
    fun `AlreadySignedOut no activa la senal`() = runBlocking {
        val events = flowOf(Unit)
        val signal = MutableStateFlow(false)
        val collector = GlobalReauthenticationCollector(
            reauthenticationEvents = events,
            invalidateExpiredSession = { SessionCoordinator.InvalidationResult.AlreadySignedOut },
            sessionExpiredSignal = signal
        )
        
        collector.collectEvents()

        assertFalse(signal.value)
    }

    @Test
    fun `CancellationException se propaga y finaliza el collector`() = runBlocking {
        val events = flowOf(Unit)
        val signal = MutableStateFlow(false)
        val collector = GlobalReauthenticationCollector(
            reauthenticationEvents = events,
            invalidateExpiredSession = { throw CancellationException("Cancelled") },
            sessionExpiredSignal = signal
        )

        var cancellationThrown = false
        try {
            collector.collectEvents()
        } catch (e: CancellationException) {
            cancellationThrown = true
        }
        
        assertTrue("CancellationException must be propagated", cancellationThrown)
    }
}
