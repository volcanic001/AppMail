package com.david.mailapp.feature.inbox

import com.david.mailapp.core.localization.UiErrorReason
import com.david.mailapp.domain.model.Email

sealed interface InboxUiState {
    data object Loading : InboxUiState

    data class Success(
        val emails: List<Email>,
        val nextPageToken: String? = null,
        val isRefreshing: Boolean = false,
        val isLoadingNextPage: Boolean = false,
        val activeActionEmailIds: Set<String> = emptySet(),
        val pendingFeedbackQueue: List<ActionFeedback> = emptyList()
    ) : InboxUiState {
        fun withFeedback(feedback: ActionFeedback) = copy(pendingFeedbackQueue = pendingFeedbackQueue + feedback)
        fun consumeFeedback(feedbackId: ActionFeedbackId) = copy(
            pendingFeedbackQueue = pendingFeedbackQueue.filterNot { it.id == feedbackId }
        )
        fun withActive(emailId: String) = copy(activeActionEmailIds = activeActionEmailIds + emailId)
        fun withoutActive(emailId: String) = copy(activeActionEmailIds = activeActionEmailIds - emailId)
    }

    data class Error(val reason: UiErrorReason) : InboxUiState
}
