package com.david.mailapp.core.session

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface SessionWriteLease {
    val generation: Long
}

interface SessionWriteGuard {
    suspend fun activate()
    suspend fun capture(): SessionWriteLease?
    suspend fun <T> commit(lease: SessionWriteLease, block: suspend () -> T): T?
    suspend fun invalidate()
}

class SessionWriteGuardImpl : SessionWriteGuard {
    private data class Lease(override val generation: Long) : SessionWriteLease

    private val mutex = Mutex()
    private var currentGeneration: Long = 0L
    private var isActive = false

    override suspend fun activate() {
        mutex.withLock {
            if (!isActive) {
                currentGeneration++
                isActive = true
            }
        }
    }

    override suspend fun capture(): SessionWriteLease? {
        return mutex.withLock {
            if (isActive) {
                Lease(currentGeneration)
            } else {
                null
            }
        }
    }

    override suspend fun <T> commit(lease: SessionWriteLease, block: suspend () -> T): T? {
        mutex.withLock {
            if (!isActive || lease.generation != currentGeneration) {
                return null
            }
            return block()
        }
    }

    override suspend fun invalidate() {
        mutex.withLock {
            isActive = false
        }
    }
}
