package com.david.mailapp.feature.emaildetail

import com.david.mailapp.core.localization.UiErrorReason
import com.david.mailapp.data.repository.EmailActionResult
import com.david.mailapp.domain.model.Email
import com.david.mailapp.domain.model.EmailFolder
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailReadOnOpenCoordinatorTest {

    @Test
    fun unread_email_executes_remote_first_action_once() = runTest {
        var calls = 0
        val coordinator = EmailReadOnOpenCoordinator(
            markAsRead = {
                calls++
                EmailActionResult.Success
            }
        )

        val action = coordinator.prepare(email(isRead = false))

        assertTrue(action != null)
        assertSame(EmailReadOnOpenOutcome.Marked, action?.invoke())
        assertNull(coordinator.prepare(email(isRead = false)))
        assertEquals(1, calls)
    }

    @Test
    fun already_read_email_does_not_prepare_an_action() {
        val coordinator = EmailReadOnOpenCoordinator(
            markAsRead = {
                error("markAsRead must not be called")
            }
        )

        assertNull(coordinator.prepare(email(isRead = true)))
    }

    @Test
    fun typed_failure_is_returned_for_visible_feedback() = runTest {
        val coordinator = EmailReadOnOpenCoordinator(
            markAsRead = {
                EmailActionResult.Failure(
                    reason = UiErrorReason.NO_CONNECTION,
                    remoteApplied = false
                )
            }
        )

        val outcome = requireNotNull(coordinator.prepare(email(isRead = false))).invoke()

        assertEquals(
            EmailReadOnOpenOutcome.Failure(UiErrorReason.NO_CONNECTION),
            outcome
        )
    }

    @Test
    fun unexpected_exception_is_mapped_without_technical_text() = runTest {
        val coordinator = EmailReadOnOpenCoordinator(
            markAsRead = {
                throw IOException("private transport detail")
            }
        )

        val outcome = requireNotNull(coordinator.prepare(email(isRead = false))).invoke()

        assertEquals(
            EmailReadOnOpenOutcome.Failure(UiErrorReason.NO_CONNECTION),
            outcome
        )
    }

    @Test
    fun cancellation_is_rethrown_and_not_converted_to_feedback() = runTest {
        val cancellation = CancellationException("detail closed")
        val coordinator = EmailReadOnOpenCoordinator(
            markAsRead = {
                throw cancellation
            }
        )

        val thrown = try {
            requireNotNull(coordinator.prepare(email(isRead = false))).invoke()
            null
        } catch (error: CancellationException) {
            error
        }

        assertSame(cancellation, thrown)
    }

    @Test
    fun failure_does_not_retry_until_a_new_detail_instance_is_created() = runTest {
        var calls = 0
        val markAsRead: suspend (String) -> EmailActionResult = {
            calls++
            EmailActionResult.Failure(UiErrorReason.NO_CONNECTION, false)
        }
        val firstOpening = EmailReadOnOpenCoordinator(markAsRead)
        val unreadEmail = email(isRead = false)

        requireNotNull(firstOpening.prepare(unreadEmail)).invoke()
        assertNull(firstOpening.prepare(unreadEmail))

        val reopenedDetail = EmailReadOnOpenCoordinator(markAsRead)
        requireNotNull(reopenedDetail.prepare(unreadEmail)).invoke()

        assertEquals(2, calls)
    }

    private fun email(isRead: Boolean) = Email(
        id = "message-id",
        threadId = "thread-id",
        from = "sender@example.com",
        fromInitials = "S",
        to = "recipient@example.com",
        subject = "Read-on-open integration",
        snippet = "Coordinator fixture",
        timestamp = 1_000L,
        isRead = isRead,
        isStarred = false,
        hasAttachments = false,
        labels = emptyList(),
        folder = EmailFolder.Trash
    )
}
