package com.david.mailapp.domain.model

/**
 * Generic paginated result from any provider.
 *
 * [nextPageToken] is null when there are no more pages.
 * The token is opaque — its format depends on the provider (Gmail uses
 * a string token, IMAP uses sequence numbers, etc.).
 */
data class PaginatedResult<T>(
    val items: List<T>,
    val nextPageToken: String?
)
