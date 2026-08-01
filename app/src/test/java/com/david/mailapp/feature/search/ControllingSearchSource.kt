package com.david.mailapp.feature.search

import com.david.mailapp.domain.model.Email
import com.david.mailapp.domain.model.PaginatedResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class ControllingSearchSource : SearchEmailSource {

    data class Entry(
        val result: PaginatedResult<Email>,
        val gate: CompletableDeferred<Unit>? = null,
        val ignoreCancellation: Boolean = false,
        val started: CompletableDeferred<Unit> = CompletableDeferred(),
        val cancelled: CompletableDeferred<Unit> = CompletableDeferred()
    )

    private val resultMap = mutableMapOf<String, MutableList<Entry>>()
    private val callCounts = mutableMapOf<String, Int>()
    val receivedTokens = mutableListOf<String?>()

    fun addResults(
        query: String,
        pageToken: String?,
        result: PaginatedResult<Email>,
        gate: CompletableDeferred<Unit>? = null,
        ignoreCancellation: Boolean = false
    ): Entry {
        val key = "$query|$pageToken"
        val entry = Entry(result, gate, ignoreCancellation)
        resultMap.getOrPut(key) { mutableListOf() }.add(entry)
        return entry
    }

    override suspend fun search(query: String, pageToken: String?): PaginatedResult<Email> {
        receivedTokens += pageToken
        val key = "$query|$pageToken"
        val entries = resultMap[key] ?: return PaginatedResult(emptyList(), null)
        val count = callCounts.getOrDefault(key, 0)
        callCounts[key] = count + 1

        val entry = entries.getOrNull(count) ?: return PaginatedResult(emptyList(), null)
        entry.started.complete(Unit)

        try {
            entry.gate?.await()
        } catch (e: CancellationException) {
            entry.cancelled.complete(Unit)
            if (!entry.ignoreCancellation) {
                throw e
            }
            withContext(NonCancellable) {
                entry.gate?.await()
            }
        }
        return entry.result
    }
}
