package com.david.mailapp.feature.inbox

/**
 * Represents a group of emails that were swiped to trash in a short time window.
 *
 * Used for:
 * - Accumulating the Snackbar message ("N emails moved to trash")
 * - Tracking which IDs to restore when the user taps UNDO
 *
 * A batch is "open" while [InboxViewModel.BATCH_WINDOW_MS] has not elapsed
 * since the last swipe. After the window closes the batch is sealed and a
 * new swipe opens a fresh one.
 */
data class TrashBatch(
    /** Ordered list of email IDs added to this batch, oldest first. */
    val emailIds: List<String>,
    /** Monotonic timestamp (from [InboxViewModel.nowMillis]) of the most-recent addition. */
    val lastSwipeAtMs: Long,
    /** Unique ID used to correlate this batch with its Snackbar feedback event. */
    val feedbackId: ActionFeedbackId = ActionFeedbackId.next()
)
