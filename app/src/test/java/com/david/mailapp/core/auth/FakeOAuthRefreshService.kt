package com.david.mailapp.core.auth

import java.util.concurrent.atomic.AtomicInteger

/**
 * Test double for [OAuthRefreshService].
 *
 * Returns a configurable sequence of results from [results] and records
 * the number of calls to [invocationCount].
 */
class FakeOAuthRefreshService(
    private val results: List<OAuthRefreshResult>
) : OAuthRefreshService {
    private val _invocationCount = AtomicInteger(0)
    val invocationCount: Int get() = _invocationCount.get()

    override suspend fun refresh(refreshToken: String): OAuthRefreshResult {
        _invocationCount.incrementAndGet()
        val index = (_invocationCount.get() - 1).coerceAtMost(results.lastIndex)
        return results[index]
    }
}
