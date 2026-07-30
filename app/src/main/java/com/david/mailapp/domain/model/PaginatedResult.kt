package com.david.mailapp.domain.model

/**
 * Generic paginated result from any provider.
 *
 * [nextPageToken] is null when there are no more pages.
 * [isComplete] is false when some message detail fetches failed —
 * consumers must not advance [nextPageToken] for incomplete pages.
 * [failures] lists the items that could not be retrieved.
 */
data class PaginatedResult<T>(
    val items: List<T>,
    val nextPageToken: String?,
    val isComplete: Boolean = true,
    val failures: List<PageItemFailure> = emptyList()
)

data class PageItemFailure(
    val itemId: String,
    val kind: PageItemFailureKind,
    val attempts: Int
)

enum class PageItemFailureKind {
    /** Retries exhausted for a transient failure (e.g. timeout, 5xx). */
    TRANSIENT_EXHAUSTED,
    /** Non-recoverable error (e.g. 404, parse error). */
    PERMANENT
}
