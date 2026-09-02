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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import java.io.IOException
import com.david.mailapp.core.perf.MailOpenPerformanceTrace

private const val MAX_CONCURRENT_DETAIL_REQUESTS = 6

internal suspend fun fetchGmailPage(
    client: HttpClient,
    labelId: String? = null,
    query: String? = null,
    pageToken: String? = null,
    maxResults: Int = 20,
    transientRetries: Int = 2,
    backoffMillis: List<Long> = emptyList(),
    delayFn: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
    clock: () -> Long = { MailOpenPerformanceTrace.now() },
    sink: NetworkDiagnosticSink? = null
): PaginatedResult<Email> {
    // 1. List message IDs
    val listResponse: MessageListResponse = client.get("users/me/messages") {
        parameter("fields", GmailProjections.LIST_FIELDS)
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
    val semaphore = Semaphore(MAX_CONCURRENT_DETAIL_REQUESTS)
    val results: List<DetailResult> = supervisorScope {
        messages.mapIndexed { index, header ->
            async {
                val result = fetchWithRetry(
                    client = client,
                    messageId = header.id,
                    maxAttempts = transientRetries + 1, // initial + retries
                    backoffMillis = backoffMillis,
                    semaphore = semaphore,
                    delayFn = delayFn,
                    clock = clock,
                    sink = sink
                )
                IndexedResult(index, result)
            }
        }.awaitAll().sortedBy { it.index }.map { it.result }
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

private data class IndexedResult(val index: Int, val result: DetailResult)


private sealed interface AttemptOutcome {
    data class Success(val msg: MessageResponse) : AttemptOutcome
    data class HttpError(val statusCode: Int) : AttemptOutcome
    object IoError : AttemptOutcome
    object InvalidResponse : AttemptOutcome
    object OtherError : AttemptOutcome
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
    backoffMillis: List<Long> = emptyList(),
    semaphore: Semaphore = Semaphore(1),
    delayFn: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
    clock: () -> Long = { MailOpenPerformanceTrace.now() },
    sink: NetworkDiagnosticSink? = null
): DetailResult {
    val mailKey = MailOpenPerformanceTrace.mailKey(messageId)

    for (attempt in 0 until maxAttempts) {
        val t0 = clock()
        val attemptResult = try {
            semaphore.withPermit {
                val response: HttpResponse = requestFullMessage(client, messageId)
                if (response.status.isSuccess()) {
                    try {
                        val msg: MessageResponse = response.body()
                        AttemptOutcome.Success(msg)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        AttemptOutcome.InvalidResponse
                    }
                } else {
                    AttemptOutcome.HttpError(response.status.value)
                }
            }
        } catch (e: CancellationException) {
            val t1 = clock()
            sink?.invoke(NetworkDiagnosticEvent(mailKey, attempt + 1, t1 - t0, DiagnosticCategory.CANCELLED))
            throw e
        } catch (e: IOException) {
            AttemptOutcome.IoError
        } catch (e: Exception) {
            AttemptOutcome.OtherError
        }

        val t1 = clock()
        val durationMs = t1 - t0

        when (attemptResult) {
            is AttemptOutcome.Success -> {
                try {
                    val email = attemptResult.msg.toDomainEmail()
                    sink?.invoke(NetworkDiagnosticEvent(mailKey, attempt + 1, durationMs, DiagnosticCategory.SUCCESS))
                    return DetailResult(email = email)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    sink?.invoke(NetworkDiagnosticEvent(mailKey, attempt + 1, durationMs, DiagnosticCategory.INVALID_RESPONSE))
                    return DetailResult(
                        failure = PageItemFailure(
                            itemId = messageId,
                            kind = PageItemFailureKind.PERMANENT,
                            attempts = attempt + 1
                        )
                    )
                }
            }
            is AttemptOutcome.HttpError -> {
                val status = attemptResult.statusCode
                if (isTransientHttpError(status)) {
                    sink?.invoke(NetworkDiagnosticEvent(mailKey, attempt + 1, durationMs, DiagnosticCategory.TRANSIENT_HTTP))
                    if (attempt < maxAttempts - 1) {
                        if (backoffMillis.isNotEmpty()) {
                            val delay = backoffMillis.getOrElse(attempt) { backoffMillis.last() }
                            delayFn(delay)
                        }
                        continue
                    }
                    return DetailResult(
                        failure = PageItemFailure(
                            itemId = messageId,
                            kind = PageItemFailureKind.TRANSIENT_EXHAUSTED,
                            attempts = attempt + 1
                        )
                    )
                }
                val category = when (status) {
                    401 -> DiagnosticCategory.SESSION_EXPIRED
                    404 -> DiagnosticCategory.NOT_FOUND
                    else -> DiagnosticCategory.PERMANENT_HTTP
                }
                sink?.invoke(NetworkDiagnosticEvent(mailKey, attempt + 1, durationMs, category))
                return DetailResult(
                    failure = PageItemFailure(
                        itemId = messageId,
                        kind = PageItemFailureKind.PERMANENT,
                        attempts = attempt + 1
                    )
                )
            }
            is AttemptOutcome.IoError -> {
                sink?.invoke(NetworkDiagnosticEvent(mailKey, attempt + 1, durationMs, DiagnosticCategory.IO))
                if (attempt < maxAttempts - 1) {
                    if (backoffMillis.isNotEmpty()) {
                        val delay = backoffMillis.getOrElse(attempt) { backoffMillis.last() }
                        delayFn(delay)
                    }
                    continue
                }
                return DetailResult(
                    failure = PageItemFailure(
                        itemId = messageId,
                        kind = PageItemFailureKind.TRANSIENT_EXHAUSTED,
                        attempts = attempt + 1
                    )
                )
            }
            is AttemptOutcome.InvalidResponse -> {
                sink?.invoke(NetworkDiagnosticEvent(mailKey, attempt + 1, durationMs, DiagnosticCategory.INVALID_RESPONSE))
                return DetailResult(
                    failure = PageItemFailure(
                        itemId = messageId,
                        kind = PageItemFailureKind.PERMANENT,
                        attempts = attempt + 1
                    )
                )
            }
            is AttemptOutcome.OtherError -> {
                sink?.invoke(NetworkDiagnosticEvent(mailKey, attempt + 1, durationMs, DiagnosticCategory.PERMANENT_HTTP))
                return DetailResult(
                    failure = PageItemFailure(
                        itemId = messageId,
                        kind = PageItemFailureKind.PERMANENT,
                        attempts = attempt + 1
                    )
                )
            }
        }
    }
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
