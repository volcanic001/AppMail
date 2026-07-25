package com.david.mailapp.feature.search

import com.david.mailapp.core.localization.UiErrorReason
import com.david.mailapp.domain.model.Email

/**
 * UI state for the Search screen — 5 discrete states.
 *
 * Flow:
 *   Idle (no query) → Loading (waiting for API) → Results | Empty | Error
 *   Type again     → back to Idle → Loading → ...
 */
sealed interface SearchUiState {

    /** No query entered yet — show history/chips. */
    data object Idle : SearchUiState

    /** Debounce finished, waiting for Gmail API response. */
    data object Loading : SearchUiState

    /** Search returned results. [query] stored so the UI can highlight matches. */
    data class Results(
        val emails: List<Email>,
        val query: String,
        val nextPageToken: String? = null,
        val isLoadingNextPage: Boolean = false
    ) : SearchUiState

    /** Search completed successfully but returned 0 emails. */
    data class Empty(val query: String) : SearchUiState

    /** Network or unexpected error during search. [query] preserved for retry. */
    data class Error(val reason: UiErrorReason, val query: String) : SearchUiState
}
