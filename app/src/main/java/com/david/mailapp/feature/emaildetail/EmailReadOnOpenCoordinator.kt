package com.david.mailapp.feature.emaildetail

import com.david.mailapp.core.localization.UiErrorReason
import com.david.mailapp.core.localization.toUiErrorReason
import com.david.mailapp.data.repository.EmailActionResult
import com.david.mailapp.domain.model.Email
import kotlinx.coroutines.CancellationException

sealed interface EmailReadOnOpenOutcome {
    data object Marked : EmailReadOnOpenOutcome
    data class Failure(val reason: UiErrorReason) : EmailReadOnOpenOutcome
}

/**
 * Prepares at most one remote-first mark-as-read action for a detail instance.
 *
 * [prepare] claims synchronously, before the returned suspend action can be
 * launched, so repeated Room emissions cannot race into duplicate requests.
 */
internal class EmailReadOnOpenCoordinator(
    private val markAsRead: suspend (String) -> EmailActionResult,
    private val gate: EmailReadOnOpenGate = EmailReadOnOpenGate()
) {

    fun prepare(email: Email): (suspend () -> EmailReadOnOpenOutcome)? {
        if (!gate.claim(email)) return null
        val emailId = email.id
        return { execute(emailId) }
    }

    private suspend fun execute(emailId: String): EmailReadOnOpenOutcome {
        return try {
            when (val result = markAsRead(emailId)) {
                EmailActionResult.Success -> EmailReadOnOpenOutcome.Marked
                is EmailActionResult.Failure -> EmailReadOnOpenOutcome.Failure(result.reason)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            EmailReadOnOpenOutcome.Failure(error.toUiErrorReason())
        }
    }
}
