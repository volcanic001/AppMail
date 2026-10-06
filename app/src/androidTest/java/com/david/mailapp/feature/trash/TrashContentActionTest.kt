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
import java.util.concurrent.atomic.AtomicInteger

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
    fun swipe_deletes_immediately_without_confirmation() {
        var deleteCalls = 0
        setTrashContent(onDelete = { deleteCalls++ })

        composeRule.onNodeWithText(email.subject).performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        // No confirmation dialog: the swipe deletes directly, exactly once.
        composeRule.onNodeWithText("¿Eliminar permanentemente?").assertDoesNotExist()
        assertEquals(1, deleteCalls)
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
        var deleteCalls = 0
        val snackbarHostState = SnackbarHostState()
        setTrashContent(
            stateProvider = { state },
            snackbarHostState = snackbarHostState,
            onDelete = {
                deleteCalls++
                // The ViewModel blocks the row while the action runs; without that
                // toggle the swiped row never snaps back and ignores later swipes.
                state = state.copy(
                    activeActionEmailIds = setOf(email.id),
                    pendingFeedbackQueue = listOf(ActionFeedback.Failure(UiErrorReason.NO_CONNECTION))
                )
            },
            onFeedbackConsumed = { id -> state = state.consumeFeedback(id) }
        )

        composeRule.onNodeWithText(email.subject).performTouchInput { swipeLeft() }

        composeRule.onNodeWithText("Sin conexión a Internet").assertExists()
        composeRule.onNodeWithText("Eliminado permanentemente").assertDoesNotExist()
        composeRule.onNodeWithText(email.subject).assertExists()

        composeRule.runOnIdle {
            snackbarHostState.currentSnackbarData?.dismiss()
        }
        composeRule.waitUntil(timeoutMillis = 2_000) {
            state.pendingFeedbackQueue.isEmpty()
        }

        // The action settles: releasing the row is what makes it snap back.
        composeRule.runOnIdle { state = state.copy(activeActionEmailIds = emptySet()) }
        // The snap-back waits 250 ms before the row takes gestures again.
        val readyAt = System.currentTimeMillis() + 700
        composeRule.waitUntil(timeoutMillis = 3_000) { System.currentTimeMillis() >= readyAt }

        composeRule.onNodeWithText(email.subject).performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Sin conexión a Internet").assertExists()
        composeRule.onNodeWithText(email.subject).assertExists()
        assertEquals(2, deleteCalls)
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

        // Row has an action in flight → the swipe gesture is rejected, no delete fires.
        assertEquals(0, deleteCalls)
    }

    @Test
    fun inbox_batch_feedback_undoes_full_batch() {
        var feedback: ActionFeedback? by mutableStateOf(null)
        var consumed = false
        var undoneEmailIds: List<String>? = null

        composeRule.setContent {
            MaterialTheme {
                val host = remember { SnackbarHostState() }
                Box {
                    ActionFeedbackEffect(
                        feedback = feedback,
                        snackbarHostState = host,
                        onConsumed = { consumed = true },
                        onUndoBatch = { undoneEmailIds = it }
                    )
                    SnackbarHost(hostState = host)
                }
            }
        }

        composeRule.onNodeWithText("Movido a la papelera").assertDoesNotExist()
        composeRule.runOnIdle {
            feedback = ActionFeedback.MovedToTrashBatch(emailIds = listOf(email.id))
        }
        composeRule.onNodeWithText("Movido a la papelera").assertExists()
        composeRule.onNodeWithText("Deshacer").performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(email.id), undoneEmailIds)
        assertEquals(true, consumed)
    }

    @Test
    fun rapid_identical_failures_are_consumed_without_stalling_snackbar_queue() {
        var queue by mutableStateOf<List<ActionFeedback>>(emptyList())
        val consumedCount = AtomicInteger(0)
        val snackbarHostState = SnackbarHostState()

        composeRule.setContent {
            MaterialTheme {
                Box {
                    ActionFeedbackEffect(
                        feedback = queue.firstOrNull(),
                        snackbarHostState = snackbarHostState,
                        onConsumed = { id ->
                            queue = queue.filterNot { it.id == id }
                            consumedCount.incrementAndGet()
                        }
                    )
                    SnackbarHost(hostState = snackbarHostState)
                }
            }
        }

        composeRule.runOnIdle {
            queue = List(5) { ActionFeedback.Failure(UiErrorReason.NO_CONNECTION) }
        }

        repeat(5) { index ->
            composeRule.waitUntil(timeoutMillis = 2_000) {
                snackbarHostState.currentSnackbarData != null
            }
            composeRule.runOnIdle {
                snackbarHostState.currentSnackbarData?.dismiss()
            }
            composeRule.waitUntil(timeoutMillis = 2_000) {
                consumedCount.get() == index + 1
            }
        }
        assertEquals(5, consumedCount.get())
        assertEquals(true, queue.isEmpty())

        composeRule.runOnIdle {
            queue = listOf(ActionFeedback.Failure(UiErrorReason.NO_CONNECTION))
        }
        composeRule.waitUntil(timeoutMillis = 2_000) {
            snackbarHostState.currentSnackbarData != null
        }
        composeRule.runOnIdle {
            snackbarHostState.currentSnackbarData?.dismiss()
        }
        composeRule.waitUntil(timeoutMillis = 2_000) {
            consumedCount.get() == 6
        }
        assertEquals(6, consumedCount.get())
        assertEquals(true, queue.isEmpty())
    }

    @Test
    fun visible_feedback_is_consumed_when_destination_leaves_composition() {
        var queue by mutableStateOf<List<ActionFeedback>>(
            listOf(ActionFeedback.MovedToTrashBatch(emailIds = listOf(email.id)))
        )
        var destinationVisible by mutableStateOf(true)
        val consumedCount = AtomicInteger(0)
        val snackbarHostState = SnackbarHostState()

        composeRule.setContent {
            MaterialTheme {
                Box {
                    if (destinationVisible) {
                        ActionFeedbackEffect(
                            feedback = queue.firstOrNull(),
                            snackbarHostState = snackbarHostState,
                            onConsumed = { id ->
                                queue = queue.filterNot { it.id == id }
                                consumedCount.incrementAndGet()
                            },
                            onUndoBatch = {}
                        )
                        SnackbarHost(hostState = snackbarHostState)
                    }
                }
            }
        }

        composeRule.onNodeWithText("Movido a la papelera").assertExists()
        composeRule.runOnIdle { destinationVisible = false }
        composeRule.waitUntil(timeoutMillis = 2_000) {
            queue.isEmpty() && consumedCount.get() == 1
        }

        composeRule.runOnIdle { destinationVisible = true }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Movido a la papelera").assertDoesNotExist()
        assertEquals(1, consumedCount.get())
    }

    private fun setTrashContent(
        initialState: TrashUiState.Success = TrashUiState.Success(emails = listOf(email)),
        stateProvider: (() -> TrashUiState.Success)? = null,
        snackbarHostState: SnackbarHostState = SnackbarHostState(),
        onDelete: (String) -> Unit = {},
        onFeedbackConsumed: (com.david.mailapp.feature.inbox.ActionFeedbackId) -> Unit = {}
    ) {
        composeRule.setContent {
            MaterialTheme {
                val state = stateProvider?.invoke() ?: initialState
                TrashContent(
                    state = state,
                    listState = androidx.compose.foundation.lazy.rememberLazyListState(),
                    snackbarHostState = snackbarHostState,
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
