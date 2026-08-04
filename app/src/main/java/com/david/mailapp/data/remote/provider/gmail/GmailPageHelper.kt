package com.david.mailapp.data.remote.provider.gmail

import com.david.mailapp.domain.model.Email
import com.david.mailapp.domain.model.PageItemFailure
import com.david.mailapp.domain.model.PageItemFailureKind
import com.david.mailapp.domain.model.PaginatedResult
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.supervisorScope
import java.io.IOException

/**
 * Fetches a page of Gmail messages with controlled concurrency, retry,
 * and partial-page awareness.
 *
 * The caller provides the HTTP [client], the API path/query parameters via
 * [request], and a backoff strategy (default: one-shot with no delay for tests).
 */
internal data class PageRequest(
    val queryParams: Map<String, String?>
)

internal suspend fun fetchGmailPage(
    client: HttpClient,
    labelId: String? = null,
    query: String? = null,
    pageToken: String? = null,
    maxResults: Int = 20,
    transientRetries: Int = 2,
    backoffMillis: List<Long> = emptyList()
): PaginatedResult<Email> {
    // 1. List message IDs
    val listResponse: MessageListResponse = client.get("users/me/messages") {
        if (labelId != null) parameter("labelIds", labelId)
        if (query != null) parameter("q", query)
        parameter("maxResults", maxResults)
        if (pageToken != null) parameter("pageToken", pageToken)
    }.body()

    val messages = listResponse.messages ?: emptyList()
    if (messages.isEmpty()) {
        return PaginatedResult(emptyList(), listResponse.nextPageToken, isComplete = true)
    }

    // 2. Fetch details with bounded concurrency + retry
    val results: List<DetailResult> = supervisorScope {
        // Process in batches of 6
        messages.chunked(6).flatMap { batch ->
            batch.map { header ->
                async {
                    fetchWithRetry(
                        client = client,
                        messageId = header.id,
                        maxAttempts = transientRetries + 1, // initial + retries
                        backoffMillis = backoffMillis
                    )
                }
            }.map { it.await() }
        }
    }

    val emails = mutableListOf<Email>()
    val failures = mutableListOf<PageItemFailure>()

    for (result in results) {
        if (result.email != null) {
            emails.add(result.email)
        } else if (result.failure != null) {
            failures.add(result.failure)
        }
    }

    // 3. Determine completeness
    val isComplete = failures.isEmpty()
    val nextToken = if (isComplete) listResponse.nextPageToken else null

    return PaginatedResult(
        items = emails,
        nextPageToken = nextToken,
        isComplete = isComplete,
        failures = failures
    )
}

/**
 * Fetch a single message detail with retry for transient failures.
 *
 * - [maxAttempts] includes the initial attempt (e.g. 3 = initial + 2 retries).
 * - [backoffMillis] provides per-call backoff delays; if empty, no delay.
 * - IOException, HTTP 408, 429, 5xx are transient.
 * - All other HTTP errors are permanent (no retry).
 * - CancellationException is always rethrown.
 */
internal suspend fun fetchWithRetry(
    client: HttpClient,
    messageId: String,
    maxAttempts: Int = 3,
    backoffMillis: List<Long> = emptyList()
): DetailResult {
    for (attempt in 0 until maxAttempts) {
        try {
            val response: HttpResponse = client.get("users/me/messages/$messageId") {
                parameter("format", "full")
            }
            if (response.status.isSuccess()) {
                val msg: MessageResponse = response.body()
                return DetailResult(email = msg.toDomainEmail())
            }
            // Non-success HTTP status
            val statusCode = response.status.value
            if (isTransientHttpError(statusCode)) {
                if (attempt < maxAttempts - 1) {
                    if (backoffMillis.isNotEmpty()) {
                        val delay = backoffMillis.getOrElse(attempt) { backoffMillis.last() }
                        kotlinx.coroutines.delay(delay)
                    }
                    continue // retry transient
                }
                // Retries exhausted for transient error
                return DetailResult(
                    failure = PageItemFailure(
                        itemId = messageId,
                        kind = PageItemFailureKind.TRANSIENT_EXHAUSTED,
                        attempts = attempt + 1
                    )
                )
            }
            // Permanent HTTP error
            return DetailResult(
                failure = PageItemFailure(
                    itemId = messageId,
                    kind = PageItemFailureKind.PERMANENT,
                    attempts = attempt + 1
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            if (attempt < maxAttempts - 1) {
                if (backoffMillis.isNotEmpty()) {
                    val delay = backoffMillis.getOrElse(attempt) { backoffMillis.last() }
                    kotlinx.coroutines.delay(delay)
                }
                continue // retry
            }
            return DetailResult(
                failure = PageItemFailure(
                    itemId = messageId,
                    kind = PageItemFailureKind.TRANSIENT_EXHAUSTED,
                    attempts = attempt + 1
                )
            )
        } catch (e: Exception) {
            // Non-IO, non-cancellation, non-http: treat as permanent
            return DetailResult(
                failure = PageItemFailure(
                    itemId = messageId,
                    kind = PageItemFailureKind.PERMANENT,
                    attempts = attempt + 1
                )
            )
        }
    }
    // Shouldn't reach here, but satisfy the compiler
    return DetailResult(
        failure = PageItemFailure(
            itemId = messageId,
            kind = PageItemFailureKind.TRANSIENT_EXHAUSTED,
            attempts = maxAttempts
        )
    )
}

internal fun isTransientHttpError(statusCode: Int): Boolean {
    return statusCode == 408 || statusCode == 429 || statusCode in 500..599
}

/**
 * Internal result carrier for [fetchWithRetry].
 */
internal data class DetailResult(
    val email: Email? = null,
    val failure: PageItemFailure? = null
)
