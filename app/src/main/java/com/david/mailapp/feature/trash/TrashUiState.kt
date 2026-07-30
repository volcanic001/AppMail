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
        val activeActionEmailIds: Set<String> = emptySet(),
        val pendingFeedbackQueue: List<ActionFeedback> = emptyList()
    ) : TrashUiState {
        fun withFeedback(feedback: ActionFeedback) = copy(pendingFeedbackQueue = pendingFeedbackQueue + feedback)
        fun consumeFeedback(feedbackId: ActionFeedbackId) = copy(
            pendingFeedbackQueue = pendingFeedbackQueue.filterNot { it.id == feedbackId }
        )
        fun withActive(emailId: String) = copy(activeActionEmailIds = activeActionEmailIds + emailId)
        fun withoutActive(emailId: String) = copy(activeActionEmailIds = activeActionEmailIds - emailId)
    }

    data class Error(val reason: UiErrorReason) : TrashUiState
}
