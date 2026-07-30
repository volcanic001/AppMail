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
 * exactly that feedback and no other — even two [MovedToTrash] or
 * two [RestoredToInbox] for different actions are distinguishable.
 */
sealed class ActionFeedback(val id: ActionFeedbackId = ActionFeedbackId.next()) {
    data class MovedToTrash(val emailId: String) : ActionFeedback()
    data class RestoredToInbox(val emailId: String) : ActionFeedback()
    data class DeletedPermanently(val emailId: String) : ActionFeedback()
    data class Failure(val reason: UiErrorReason) : ActionFeedback()
}
