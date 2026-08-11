package com.david.mailapp.data.repository

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class FolderCommitCoordinator {
    private val mutex = Mutex()
    private var currentGeneration = 0L

    suspend fun nextGeneration(): Long = mutex.withLock {
        ++currentGeneration
    }

    suspend fun currentGeneration(): Long = mutex.withLock {
        currentGeneration
    }

    suspend fun commitIfValid(
        generation: Long,
        block: suspend () -> Unit
    ): Boolean = mutex.withLock {
        if (generation != currentGeneration) {
            false
        } else {
            block()
            true
        }
    }
}
