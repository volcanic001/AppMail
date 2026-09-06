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
        /**
         * IDs of emails that are still in active non-destructive actions
         * (mark-as-read, star, etc.). These are NOT hidden from the list;
         * they only disable gesture interactions on that row.
         */
        val activeActionEmailIds: Set<String> = emptySet(),
        val pendingFeedbackQueue: List<ActionFeedback> = emptyList(),
        /**
         * IDs of emails removed optimistically from the visible list.
         * Added immediately when the user swipes; removed when:
         *   - the remote operation succeeds and Room publishes its removal, OR
         *   - the remote operation fails  (email is restored to visibility).
         *
         * ONLY used for destructive (trash) operations — NOT for read/star/etc.
         */
        val pendingOptimisticRemovalIds: Set<String> = emptySet(),
        /** Rows shown immediately while their Undo restore is confirmed remotely. */
        val optimisticRestoredEmails: Map<String, Email> = emptyMap(),
        /**
         * The currently-open trash batch.  Null when no batch is in progress.
         * The batch is "open" while [InboxViewModel.BATCH_WINDOW_MS] has not
         * elapsed since the last swipe; after that window closes a new swipe
         * opens a fresh batch.
         */
        val activeBatch: TrashBatch? = null
    ) : InboxUiState {

        /**
         * The list the UI should actually render.
         * Excludes emails that have been optimistically removed but whose
         * remote operation is still in flight (or recently failed but not yet
         * reflected in Room).
         */
        val visibleEmails: List<Email>
            get() {
                val mergedEmails = if (optimisticRestoredEmails.isEmpty()) {
                    emails
                } else {
                    val persistedIds = emails.mapTo(mutableSetOf()) { it.id }
                    (emails + optimisticRestoredEmails.values.filterNot { it.id in persistedIds })
                        .sortedByDescending { it.timestamp }
                }
                return if (pendingOptimisticRemovalIds.isEmpty()) mergedEmails
                else mergedEmails.filterNot { it.id in pendingOptimisticRemovalIds }
            }

        fun withFeedback(feedback: ActionFeedback) =
            copy(pendingFeedbackQueue = pendingFeedbackQueue + feedback)

        fun consumeFeedback(feedbackId: ActionFeedbackId) = copy(
            pendingFeedbackQueue = pendingFeedbackQueue.filterNot { it.id == feedbackId }
        )

        fun withActive(emailId: String) =
            copy(activeActionEmailIds = activeActionEmailIds + emailId)

        fun withoutActive(emailId: String) =
            copy(activeActionEmailIds = activeActionEmailIds - emailId)

        fun withOptimisticRemoval(emailId: String) =
            copy(pendingOptimisticRemovalIds = pendingOptimisticRemovalIds + emailId)

        fun withoutOptimisticRemoval(emailId: String) =
            copy(pendingOptimisticRemovalIds = pendingOptimisticRemovalIds - emailId)

        fun withoutOptimisticRemovals(emailIds: Collection<String>) =
            copy(pendingOptimisticRemovalIds = pendingOptimisticRemovalIds - emailIds.toSet())

        fun withOptimisticRestores(emails: Collection<Email>) = copy(
            optimisticRestoredEmails = optimisticRestoredEmails + emails.associateBy { it.id }
        )

        fun withoutOptimisticRestore(emailId: String) = copy(
            optimisticRestoredEmails = optimisticRestoredEmails - emailId
        )
    }

    data class Error(val reason: UiErrorReason) : InboxUiState
}
