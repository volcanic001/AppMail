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

/** Displays one confirmed action result and consumes it after presentation. */
@Composable
fun ActionFeedbackEffect(
    feedback: ActionFeedback?,
    snackbarHostState: SnackbarHostState,
    onConsumed: (ActionFeedbackId) -> Unit,
    onUndoMoveToTrash: ((String) -> Unit)? = null
) {
    val movedMessage = stringResource(R.string.snackbar_moved_to_trash)
    val restoredMessage = stringResource(R.string.snackbar_restored_to_inbox)
    val deletedMessage = stringResource(R.string.snackbar_deleted_permanently)
    val undoLabel = stringResource(R.string.action_undo)
    val failureMessage = (feedback as? ActionFeedback.Failure)?.reason?.toUiText()?.asString()

    LaunchedEffect(feedback?.id) {
        val current = feedback ?: return@LaunchedEffect
        val result = when (current) {
            is ActionFeedback.MovedToTrash -> snackbarHostState.showSnackbar(
                message = movedMessage,
                actionLabel = if (onUndoMoveToTrash != null) undoLabel else null,
                duration = SnackbarDuration.Short
            )
            is ActionFeedback.RestoredToInbox -> snackbarHostState.showSnackbar(
                message = restoredMessage,
                duration = SnackbarDuration.Short
            )
            is ActionFeedback.DeletedPermanently -> snackbarHostState.showSnackbar(
                message = deletedMessage,
                duration = SnackbarDuration.Short
            )
            is ActionFeedback.Failure -> snackbarHostState.showSnackbar(
                message = requireNotNull(failureMessage),
                duration = SnackbarDuration.Short
            )
        }

        onConsumed(current.id)
        if (
            current is ActionFeedback.MovedToTrash &&
            result == SnackbarResult.ActionPerformed
        ) {
            onUndoMoveToTrash?.invoke(current.emailId)
        }
    }
}
