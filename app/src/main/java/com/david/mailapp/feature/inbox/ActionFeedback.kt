package com.david.mailapp.feature.inbox

import com.david.mailapp.core.localization.UiErrorReason
import java.util.concurrent.atomic.AtomicLong

/**
 * Monotonic feedback identifier. Every enqueued feedback gets a unique id
 * so consumers can observe it exactly once via [ActionFeedback.id].
 */
class ActionFeedbackId private constructor(val value: Long) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is ActionFeedbackId && value == other.value)

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = "ActionFeedbackId($value)"

    companion object {
        private val counter = AtomicLong(0)
        fun next() = ActionFeedbackId(counter.incrementAndGet())
    }
}

/**
 * Typed, UI-only action feedback. No exceptions, no technical messages.
 *
 * Each instance carries a unique [id] so consumers can observe
 * exactly that feedback and no other — even two [MovedToTrashBatch] or
 * two [RestoredToInbox] for different actions are distinguishable.
 * The id is a data-class component so Compose also treats repeated
 * feedback with identical payloads as distinct values.
 */
sealed class ActionFeedback {
    abstract val id: ActionFeedbackId

    /**
     * One or more emails moved to trash as a single optimistic batch.
     * [emailIds] are the IDs in this group — used for batch UNDO.
     * [count] is pre-computed for display; equals [emailIds].size but
     * kept explicit so the UI does not need to re-compute it.
     */
    data class MovedToTrashBatch(
        val emailIds: List<String>,
        val count: Int = emailIds.size,
        override val id: ActionFeedbackId = ActionFeedbackId.next()
    ) : ActionFeedback()

    data class RestoredToInbox(
        val emailId: String,
        override val id: ActionFeedbackId = ActionFeedbackId.next()
    ) : ActionFeedback()

    data class DeletedPermanently(
        val emailId: String,
        override val id: ActionFeedbackId = ActionFeedbackId.next()
    ) : ActionFeedback()

    /**
     * Remote trash operation failed for [failedCount] emails.
     * Distinct from the generic [Failure] so the UI can show a
     * context-specific message ("Could not move N email(s) to trash").
     */
    data class TrashFailure(
        val failedCount: Int,
        override val id: ActionFeedbackId = ActionFeedbackId.next()
    ) : ActionFeedback()

    data class Failure(
        val reason: UiErrorReason,
        override val id: ActionFeedbackId = ActionFeedbackId.next()
    ) : ActionFeedback()
}
