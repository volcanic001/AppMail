package com.david.mailapp.feature.inbox

import com.david.mailapp.core.localization.UiErrorReason
import com.david.mailapp.domain.model.Email

/**
 * UI state for the Inbox screen.
 *
 * The Room Flow always emits the latest cached data. The ViewModel
 * merges those emissions with transient UI flags (isRefreshing,
 * isLoadingNextPage) to produce this sealed state.
 */
sealed interface InboxUiState {

    /** Initial state — no data yet. UI shows shimmer skeleton. */
    data object Loading : InboxUiState

    /**
     * Data is available (possibly empty).
     *
     * [isRefreshing] — pull-to-refresh or initial load in progress.
     * [isLoadingNextPage] — pagination load at the bottom of the list.
     */
    data class Success(
        val emails: List<Email>,
        val nextPageToken: String? = null,
        val isRefreshing: Boolean = false,
        val isLoadingNextPage: Boolean = false
    ) : InboxUiState

    /** Network or unexpected error. [reason] maps to a localized resource. */
    data class Error(val reason: UiErrorReason) : InboxUiState
}
