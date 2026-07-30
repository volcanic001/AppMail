package com.david.mailapp.feature.inbox

import com.david.mailapp.core.localization.UiErrorReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ActionFeedbackTest {

    @Test fun repeated_feedback_instances_have_unique_ids() {
        val restored1 = ActionFeedback.RestoredToInbox("e1")
        val restored2 = ActionFeedback.RestoredToInbox("e1")
        val deleted1 = ActionFeedback.DeletedPermanently("e1")
        val deleted2 = ActionFeedback.DeletedPermanently("e1")

        assertNotEquals(restored1.id, restored2.id)
        assertNotEquals(deleted1.id, deleted2.id)
    }

    @Test fun consume_feedback_removes_only_matching_id() {
        val first = ActionFeedback.Failure(UiErrorReason.NO_CONNECTION)
        val second = ActionFeedback.Failure(UiErrorReason.NO_CONNECTION)
        val state = InboxUiState.Success(
            emails = emptyList(),
            pendingFeedbackQueue = listOf(first, second)
        )

        val consumed = state.consumeFeedback(second.id)

        assertEquals(listOf(first), consumed.pendingFeedbackQueue)
    }
}
