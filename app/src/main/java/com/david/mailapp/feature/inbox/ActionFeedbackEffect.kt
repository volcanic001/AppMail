package com.david.mailapp.feature.inbox

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import com.david.mailapp.R
import com.david.mailapp.core.localization.asString
import com.david.mailapp.core.localization.toUiText

/**
 * Displays one confirmed action result and consumes it after presentation.
 *
 * For [ActionFeedback.MovedToTrashBatch] the Snackbar message reads:
 *   - count = 1  → "Movido a la papelera"
 *   - count > 1  → "N correos movidos a la papelera"
 *
 * UNDO on a batch restores all emails in the batch via [onUndoBatch].
 */
@Composable
fun ActionFeedbackEffect(
    feedback: ActionFeedback?,
    snackbarHostState: SnackbarHostState,
    onConsumed: (ActionFeedbackId) -> Unit,
    onUndoMoveToTrash: ((String) -> Unit)? = null,
    onUndoBatch: ((List<String>) -> Unit)? = null
) {
    val movedSingle  = stringResource(R.string.snackbar_moved_to_trash)
    val movedPlural  = stringResource(R.string.snackbar_moved_to_trash_plural)
    val restoredMessage = stringResource(R.string.snackbar_restored_to_inbox)
    val deletedMessage  = stringResource(R.string.snackbar_deleted_permanently)
    val failedSingle = stringResource(R.string.snackbar_trash_failed_single)
    val failedPlural = stringResource(R.string.snackbar_trash_failed_plural)
    val undoLabel    = stringResource(R.string.action_undo)
    val failureMessage = (feedback as? ActionFeedback.Failure)?.reason?.toUiText()?.asString()

    LaunchedEffect(feedback?.id) {
        val current = feedback ?: return@LaunchedEffect

        try {
            val result = when (current) {
                is ActionFeedback.MovedToTrashBatch -> {
                    val message = if (current.count == 1) movedSingle
                                  else movedPlural.format(current.count)
                    val canUndo = onUndoBatch != null && current.emailIds.isNotEmpty()
                    snackbarHostState.showSnackbar(
                        message = message,
                        actionLabel = if (canUndo) undoLabel else null,
                        duration = SnackbarDuration.Short
                    )
                }
                is ActionFeedback.RestoredToInbox -> snackbarHostState.showSnackbar(
                    message = restoredMessage,
                    duration = SnackbarDuration.Short
                )
                is ActionFeedback.DeletedPermanently -> snackbarHostState.showSnackbar(
                    message = deletedMessage,
                    duration = SnackbarDuration.Short
                )
                is ActionFeedback.TrashFailure -> {
                    val message = if (current.failedCount == 1) failedSingle
                                  else failedPlural.format(current.failedCount)
                    snackbarHostState.showSnackbar(
                        message = message,
                        duration = SnackbarDuration.Short
                    )
                }
                is ActionFeedback.Failure -> snackbarHostState.showSnackbar(
                    message = requireNotNull(failureMessage),
                    duration = SnackbarDuration.Short
                )
            }

            if (current is ActionFeedback.MovedToTrashBatch && result == SnackbarResult.ActionPerformed) {
                onUndoBatch?.invoke(current.emailIds)
            }
        } finally {
            // Leaving this destination cancels showSnackbar(). Consume the event
            // on that path too so returning to the same ViewModel cannot replay it.
            onConsumed(current.id)
        }
    }
}
