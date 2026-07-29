package com.david.mailapp.feature.search

import com.david.mailapp.domain.model.Email
import com.david.mailapp.domain.model.PaginatedResult
import kotlinx.coroutines.CompletableDeferred

class ControllingSearchSource : SearchEmailSource {

    data class Entry(
        val result: PaginatedResult<Email>,
        val gate: CompletableDeferred<Unit>? = null
    )

    private val resultMap = mutableMapOf<String, MutableList<Entry>>()
    private val callCounts = mutableMapOf<String, Int>()

    fun addResults(
        query: String,
        pageToken: String?,
        result: PaginatedResult<Email>,
        gate: CompletableDeferred<Unit>? = null
    ) {
        val key = "$query|$pageToken"
        resultMap.getOrPut(key) { mutableListOf() }.add(Entry(result, gate))
    }

    override suspend fun search(query: String, pageToken: String?): PaginatedResult<Email> {
        val key = "$query|$pageToken"
        val entries = resultMap[key] ?: return PaginatedResult(emptyList(), null)
        val count = callCounts.getOrDefault(key, 0)
        callCounts[key] = count + 1

        val entry = entries.getOrNull(count) ?: return PaginatedResult(emptyList(), null)
        entry.gate?.await()
        return entry.result
    }
}
