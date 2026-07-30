package com.david.mailapp.feature.trash

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import com.david.mailapp.core.localization.UiErrorReason
import com.david.mailapp.domain.model.Email
import com.david.mailapp.domain.model.EmailFolder
import com.david.mailapp.feature.inbox.ActionFeedback
import com.david.mailapp.feature.inbox.ActionFeedbackEffect
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TrashContentActionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val email = Email(
        id = "e1",
        threadId = "t1",
        from = "sender@example.com",
        fromInitials = "S",
        to = "me@example.com",
        subject = "Correo de prueba",
        snippet = "Contenido",
        timestamp = 1_000L,
        isRead = false,
        isStarred = false,
        hasAttachments = false,
        labels = emptyList(),
        folder = EmailFolder.Trash
    )

    @Test
    fun swipe_opens_confirmation_without_deleting_and_cancel_keeps_row() {
        var deleteCalls = 0
        setTrashContent(onDelete = { deleteCalls++ })

        composeRule.onNodeWithText(email.subject).performTouchInput { swipeLeft() }

        composeRule.onNodeWithText("¿Eliminar permanentemente?").assertExists()
        assertEquals(0, deleteCalls)
        composeRule.onNodeWithText("Cancelar").performClick()

        composeRule.onNodeWithText("¿Eliminar permanentemente?").assertDoesNotExist()
        composeRule.onNodeWithText(email.subject).assertExists()
        assertEquals(0, deleteCalls)
    }

    @Test
    fun confirm_deletes_exactly_once_and_closes_dialog() {
        var deleteCalls = 0
        setTrashContent(onDelete = { deleteCalls++ })

        composeRule.onNodeWithText(email.subject).performTouchInput { swipeLeft() }
        composeRule.onNodeWithText("Eliminar permanentemente").performClick()
        composeRule.waitForIdle()

        assertEquals(1, deleteCalls)
        composeRule.onNodeWithText("¿Eliminar permanentemente?").assertDoesNotExist()
    }

    @Test
    fun confirmed_delete_feedback_has_no_undo() {
        val feedback = ActionFeedback.DeletedPermanently(email.id)
        setTrashContent(
            initialState = TrashUiState.Success(
                emails = listOf(email),
                pendingFeedbackQueue = listOf(feedback)
            )
        )

        composeRule.onNodeWithText("Eliminado permanentemente").assertExists()
        composeRule.onNodeWithText("Deshacer").assertDoesNotExist()
    }

    @Test
    fun failure_shows_error_not_success_and_row_remains() {
        var state by mutableStateOf(TrashUiState.Success(emails = listOf(email)))
        setTrashContent(
            stateProvider = { state },
            onDelete = {
                state = state.copy(
                    pendingFeedbackQueue = listOf(ActionFeedback.Failure(UiErrorReason.NO_CONNECTION))
                )
            },
            onFeedbackConsumed = { id -> state = state.consumeFeedback(id) }
        )

        composeRule.onNodeWithText(email.subject).performTouchInput { swipeLeft() }
        composeRule.onNodeWithText("Eliminar permanentemente").performClick()

        composeRule.onNodeWithText("Sin conexión a Internet").assertExists()
        composeRule.onNodeWithText("Eliminado permanentemente").assertDoesNotExist()
        composeRule.onNodeWithText(email.subject).assertExists()
    }

    @Test
    fun active_row_rejects_second_action_gesture() {
        var deleteCalls = 0
        setTrashContent(
            initialState = TrashUiState.Success(
                emails = listOf(email),
                activeActionEmailIds = setOf(email.id)
            ),
            onDelete = { deleteCalls++ }
        )

        composeRule.onNodeWithText(email.subject).performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("¿Eliminar permanentemente?").assertDoesNotExist()
        assertEquals(0, deleteCalls)
    }

    @Test
    fun inbox_feedback_is_absent_until_confirmed_event_then_undo_is_remote_action() {
        var feedback: ActionFeedback? by mutableStateOf(null)
        var consumed = false
        var undoneEmailId: String? = null

        composeRule.setContent {
            MaterialTheme {
                val host = remember { SnackbarHostState() }
                Box {
                    ActionFeedbackEffect(
                        feedback = feedback,
                        snackbarHostState = host,
                        onConsumed = { consumed = true },
                        onUndoMoveToTrash = { undoneEmailId = it }
                    )
                    SnackbarHost(hostState = host)
                }
            }
        }

        composeRule.onNodeWithText("Movido a la papelera").assertDoesNotExist()
        composeRule.runOnIdle { feedback = ActionFeedback.MovedToTrash(email.id) }
        composeRule.onNodeWithText("Movido a la papelera").assertExists()
        composeRule.onNodeWithText("Deshacer").performClick()
        composeRule.waitForIdle()

        assertEquals(email.id, undoneEmailId)
        assertEquals(true, consumed)
    }

    private fun setTrashContent(
        initialState: TrashUiState.Success = TrashUiState.Success(emails = listOf(email)),
        stateProvider: (() -> TrashUiState.Success)? = null,
        onDelete: (String) -> Unit = {},
        onFeedbackConsumed: (com.david.mailapp.feature.inbox.ActionFeedbackId) -> Unit = {}
    ) {
        composeRule.setContent {
            MaterialTheme {
                val host = remember { SnackbarHostState() }
                val state = stateProvider?.invoke() ?: initialState
                TrashContent(
                    state = state,
                    listState = androidx.compose.foundation.lazy.rememberLazyListState(),
                    snackbarHostState = host,
                    highlightedEmailId = null,
                    onEmailClick = {},
                    onDeletePermanently = onDelete,
                    onRestoreToInbox = {},
                    onFeedbackConsumed = onFeedbackConsumed,
                    onRefresh = {},
                    onLoadNextPage = {},
                    onClearHighlight = {}
                )
            }
        }
    }
}
