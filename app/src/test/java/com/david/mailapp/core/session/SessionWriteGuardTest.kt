package com.david.mailapp.core.session

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SessionWriteGuardTest {

    @Test
    fun `inactive session cannot capture a write lease`() = runBlocking {
        val guard = SessionWriteGuardImpl()

        assertNull(guard.capture())
    }

    @Test
    fun `lease from previous account cannot commit after reactivation`() = runBlocking {
        val guard = SessionWriteGuardImpl()
        guard.activate()
        val oldLease = requireNotNull(guard.capture())

        guard.invalidate()
        guard.activate()
        var writes = 0

        val staleResult = guard.commit(oldLease) {
            writes++
            "written"
        }
        val currentLease = requireNotNull(guard.capture())
        val currentResult = guard.commit(currentLease) {
            writes++
            "written"
        }

        assertNull(staleResult)
        assertEquals("written", currentResult)
        assertEquals(1, writes)
    }

    @Test
    fun `invalidation waits for an in-progress committed write`() = runBlocking {
        val guard = SessionWriteGuardImpl()
        guard.activate()
        val lease = requireNotNull(guard.capture())
        val writeStarted = CompletableDeferred<Unit>()
        val allowWriteToFinish = CompletableDeferred<Unit>()

        val write = async {
            guard.commit(lease) {
                writeStarted.complete(Unit)
                allowWriteToFinish.await()
                "done"
            }
        }
        writeStarted.await()
        val invalidation = async { guard.invalidate() }

        assertEquals(false, invalidation.isCompleted)
        allowWriteToFinish.complete(Unit)

        assertEquals("done", write.await())
        invalidation.await()
        assertNull(guard.capture())
    }

    @Test
    fun `repeated activation preserves leases for the same active session`() = runBlocking {
        val guard = SessionWriteGuardImpl()
        guard.activate()
        val lease = requireNotNull(guard.capture())

        guard.activate()

        assertNotNull(guard.commit(lease) { Unit })
    }
}
