package com.david.mailapp.testhelpers

import com.david.mailapp.core.session.SessionWriteGuard
import com.david.mailapp.core.session.SessionWriteLease

class FakeSessionWriteGuard : SessionWriteGuard {

    data class SimpleLease(override val generation: Long) : SessionWriteLease

    var captureResult: SessionWriteLease? = SimpleLease(1L)
    var commitReturnsNull: Boolean = false
    var commitReturnsNullByCall: List<Boolean> = emptyList()
    var commitCalls: Int = 0
    var commitError: Exception? = null
    var commitErrorsByCall: Map<Int, Exception> = emptyMap()
    var eventLog: MutableList<String>? = null

    private var active = true

    override suspend fun activate() { active = true }

    override suspend fun capture(): SessionWriteLease? = if (active) captureResult else null

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T> commit(lease: SessionWriteLease, block: suspend () -> T): T? {
        eventLog?.add("room.commit")
        val callIndex = commitCalls++
        commitErrorsByCall[callIndex]?.let { throw it }
        commitError?.let { throw it }
        val returnsNull = commitReturnsNullByCall.getOrNull(callIndex) ?: commitReturnsNull
        if (returnsNull) return null
        return block()
    }

    override suspend fun invalidate() { active = false }
}
