package com.david.mailapp.data.repository

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class FolderCommitCoordinatorTest {

    @Test
    fun `el coordinador acepta la generacion vigente y rechaza generaciones antiguas`() = runTest {
        val coordinator = FolderCommitCoordinator()
        val g1 = coordinator.nextGeneration()
        val g2 = coordinator.nextGeneration()

        var executedG1 = false
        val acceptedG1 = coordinator.commitIfValid(g1) {
            executedG1 = true
        }
        assertFalse("g1 should be rejected as obsolete", acceptedG1)
        assertFalse("g1 block should not be executed", executedG1)

        var executedG2 = false
        val acceptedG2 = coordinator.commitIfValid(g2) {
            executedG2 = true
        }
        assertTrue("g2 should be accepted as current", acceptedG2)
        assertTrue("g2 block should be executed", executedG2)
    }

    @Test
    fun `dos commits de la misma carpeta nunca ejecutan simultaneamente su seccion critica`() = runTest {
        val coordinator = FolderCommitCoordinator()
        val gen = coordinator.currentGeneration()

        val activeCount = AtomicInteger(0)
        val maxConcurrent = AtomicInteger(0)

        launch {
            coordinator.commitIfValid(gen) {
                val active = activeCount.incrementAndGet()
                if (active > maxConcurrent.get()) maxConcurrent.set(active)
                delay(100)
                activeCount.decrementAndGet()
            }
        }

        launch {
            coordinator.commitIfValid(gen) {
                val active = activeCount.incrementAndGet()
                if (active > maxConcurrent.get()) maxConcurrent.set(active)
                delay(100)
                activeCount.decrementAndGet()
            }
        }

        delay(300)

        assertEquals("Should only run one at a time", 1, maxConcurrent.get())
        assertEquals("Should finish with zero active", 0, activeCount.get())
    }

    @Test
    fun `una generacion nueva no se registra durante un commit activo`() = runTest {
        val coordinator = FolderCommitCoordinator()
        val genA = coordinator.nextGeneration()

        val enteredCommit = CompletableDeferred<Unit>()
        val releaseCommit = CompletableDeferred<Unit>()

        // Track order of completion
        val events = mutableListOf<String>()

        // Launch commit A — holds the mutex until released
        val commitAJob = launch {
            coordinator.commitIfValid(genA) {
                enteredCommit.complete(Unit)
                releaseCommit.await()           // blocks inside mutex
                events += "commitA-done"
            }
        }

        // Wait until commit A has entered the mutex
        enteredCommit.await()

        // Now try to register generationB — must wait because A holds the mutex
        var genBValue = -1L
        val genBJob = launch {
            genBValue = coordinator.nextGeneration()
            events += "genB-registered"
        }

        // genBJob cannot proceed yet — commit A is still holding the mutex
        assertFalse("genB should not be registered while commit A holds the mutex",
            genBJob.isCompleted)

        // Release commit A
        releaseCommit.complete(Unit)
        commitAJob.join()
        genBJob.join()

        // After release, genB must have registered with a strictly higher generation
        assertEquals(listOf("commitA-done", "genB-registered"), events)
        assertTrue("genB must have a higher generation than genA", genBValue > genA)
        // Commit with genA must now be rejected
        val lateAccepted = coordinator.commitIfValid(genA) {}
        assertFalse("A late commit with genA must be rejected after genB is registered", lateAccepted)
    }
}
