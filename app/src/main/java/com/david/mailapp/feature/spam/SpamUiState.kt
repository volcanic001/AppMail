package com.david.mailapp.feature.spam

import com.david.mailapp.core.localization.UiErrorReason
import com.david.mailapp.domain.model.Email
import com.david.mailapp.feature.inbox.ActionFeedback
import com.david.mailapp.feature.inbox.ActionFeedbackId

sealed interface SpamUiState {
    data object Loading : SpamUiState

    data class Success(
        val emails: List<Email>,
        val nextPageToken: String? = null,
        val isRefreshing: Boolean = false,
        val isLoadingNextPage: Boolean = false,
        val activeActionEmailIds: Set<String> = emptySet(),
        val pendingFeedbackQueue: List<ActionFeedback> = emptyList(),
        /**
         * IDs removed optimistically from the visible list the instant the user
         * swipes (not-spam / delete). The remote operation runs in the background;
         * the id is dropped when Room publishes the change (success) or when the
         * operation fails (the row reappears). Mirrors InboxUiState.
         */
        val pendingOptimisticRemovalIds: Set<String> = emptySet()
    ) : SpamUiState {
        /**
         * The list the UI actually renders: excludes rows whose action is still
         * in flight, so a swipe feels instant.
         */
        val visibleEmails: List<Email>
            get() = if (pendingOptimisticRemovalIds.isEmpty()) emails
            else emails.filterNot { it.id in pendingOptimisticRemovalIds }

        fun withFeedback(feedback: ActionFeedback) = copy(pendingFeedbackQueue = pendingFeedbackQueue + feedback)
        fun consumeFeedback(feedbackId: ActionFeedbackId) = copy(
            pendingFeedbackQueue = pendingFeedbackQueue.filterNot { it.id == feedbackId }
        )
        fun withActive(emailId: String) = copy(activeActionEmailIds = activeActionEmailIds + emailId)
        fun withoutActive(emailId: String) = copy(activeActionEmailIds = activeActionEmailIds - emailId)
        fun withOptimisticRemoval(emailId: String) =
            copy(pendingOptimisticRemovalIds = pendingOptimisticRemovalIds + emailId)
        fun withoutOptimisticRemoval(emailId: String) =
            copy(pendingOptimisticRemovalIds = pendingOptimisticRemovalIds - emailId)
    }

    data class Error(val reason: UiErrorReason) : SpamUiState
}
