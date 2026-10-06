package com.david.mailapp.feature.trash

import com.david.mailapp.core.localization.UiErrorReason
import com.david.mailapp.domain.model.Email
import com.david.mailapp.feature.inbox.ActionFeedback
import com.david.mailapp.feature.inbox.ActionFeedbackId

sealed interface TrashUiState {
    data object Loading : TrashUiState

    data class Success(
        val emails: List<Email>,
        val nextPageToken: String? = null,
        val isRefreshing: Boolean = false,
        val isLoadingNextPage: Boolean = false,
        val isEmptyingTrash: Boolean = false,
        val activeActionEmailIds: Set<String> = emptySet(),
        val pendingFeedbackQueue: List<ActionFeedback> = emptyList(),
        /**
         * IDs removed optimistically from the visible list the instant the user
         * swipes (delete / restore). The remote operation runs in the background;
         * the id is dropped from this set when Room publishes the change (success)
         * or when the operation fails (the row reappears). Mirrors InboxUiState.
         */
        val pendingOptimisticRemovalIds: Set<String> = emptySet()
    ) : TrashUiState {
        /**
         * The list the UI actually renders: excludes rows whose destructive
         * action is still in flight, so a swipe feels instant.
         */
        val visibleEmails: List<Email>
            get() = if (pendingOptimisticRemovalIds.isEmpty()) emails
            else emails.filterNot { it.id in pendingOptimisticRemovalIds }

        /** The empty-trash action is offered only when there is something to delete. */
        val canEmptyTrash: Boolean get() = emails.isNotEmpty() && !isEmptyingTrash
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

    data class Error(val reason: UiErrorReason) : TrashUiState
}
