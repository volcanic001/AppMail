package com.david.mailapp.feature.inbox

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.test.platform.app.InstrumentationRegistry
import com.david.mailapp.R
import com.david.mailapp.core.localization.UiErrorReason
import com.david.mailapp.domain.model.Email
import com.david.mailapp.domain.model.EmailFolder
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Characterization tests for the internal InboxContent seam introduced in 2.1. */
class InboxContentCharacterizationTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loading_state_renders_shimmer_contract() {
        setContent(InboxUiState.Loading)

        composeRule.onNodeWithTag("inbox_loading").assertIsDisplayed()
        composeRule.onNodeWithText("Bandeja").assertIsDisplayed()
    }

    @Test
    fun error_state_renders_reason_and_retry_callback() {
        var refreshCalls = 0
        setContent(InboxUiState.Error(UiErrorReason.NO_CONNECTION), onRefresh = { refreshCalls++ })

        composeRule.onNodeWithTag("inbox_error").assertIsDisplayed()
        composeRule.onNodeWithText("Reintentar").performClick()

        assertEquals(1, refreshCalls)
    }

    @Test
    fun empty_success_state_keeps_list_and_empty_item() {
        setContent(InboxUiState.Success(emails = emptyList()))

        composeRule.onNodeWithTag("inbox_list").assertIsDisplayed()
        composeRule.onNodeWithTag("inbox_empty").assertIsDisplayed()
    }

    @Test
    fun success_state_delivers_email_id_on_click() {
        var clickedId: String? = null
        setContent(
            InboxUiState.Success(emails = listOf(testEmail("e1"))),
            onEmailClick = { clickedId = it }
        )

        composeRule.onNodeWithText("Asunto e1").performTouchInput { click() }

        assertEquals("e1", clickedId)
    }

    @Test
    fun refreshing_success_state_renders_refresh_indicator_and_list() {
        setContent(
            InboxUiState.Success(
                emails = listOf(testEmail("e1")),
                isRefreshing = true
            )
        )

        composeRule.onNodeWithTag("inbox_list").assertIsDisplayed()
        composeRule.onNodeWithTag("inbox_refresh_indicator").assertIsDisplayed()
    }

    @Test
    fun populated_list_triggers_menu_and_search_callbacks() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val menuDescription = context.getString(R.string.action_menu)
        val searchDescription = context.getString(R.string.action_search)

        var menuCalls = 0
        var searchCalls = 0

        composeRule.setContent {
            MaterialTheme {
                InboxContent(
                    uiState = InboxUiState.Success(emails = listOf(testEmail("e1"))),
                    listState = rememberLazyListState(),
                    highlightedEmailId = null,
                    showEmailDividers = true,
                    onClearHighlight = {},
                    onMenuClick = { menuCalls++ },
                    onSearchClick = { searchCalls++ },
                    onEmailClick = {},
                    onRefresh = {},
                    onLoadNextPage = {},
                    onMoveToTrash = {},
                    onFeedbackConsumed = {},
                    onUndoMoveToTrash = {},
                    snackbarHostState = SnackbarHostState()
                )
            }
        }

        // Tap Menu
        composeRule.onNodeWithContentDescription(menuDescription).performClick()
        assertEquals(1, menuCalls)

        // Tap Search
        composeRule.onNodeWithContentDescription(searchDescription).performClick()
        assertEquals(1, searchCalls)
    }

    @Test
    fun highlight_fallback_clears_after_2500ms_without_row() {
        var clearCalls = 0
        composeRule.mainClock.autoAdvance = false

        composeRule.setContent {
            MaterialTheme {
                InboxContent(
                    uiState = InboxUiState.Success(emails = emptyList()),
                    listState = rememberLazyListState(),
                    highlightedEmailId = "missing_email",
                    showEmailDividers = true,
                    onClearHighlight = { clearCalls++ },
                    onMenuClick = {},
                    onSearchClick = {},
                    onEmailClick = {},
                    onRefresh = {},
                    onLoadNextPage = {},
                    onMoveToTrash = {},
                    onFeedbackConsumed = {},
                    onUndoMoveToTrash = {},
                    snackbarHostState = SnackbarHostState()
                )
            }
        }

        // Before 2500ms, not cleared
        composeRule.mainClock.advanceTimeBy(2400)
        assertEquals(0, clearCalls)

        // At/after 2500ms, cleared exactly once
        composeRule.mainClock.advanceTimeBy(200)
        assertEquals(1, clearCalls)
    }

    @Test
    fun refresh_transition_resets_scroll_only_when_started_below_offset_50() {
        var isRefreshing by mutableStateOf(true)
        val listState = LazyListState(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 49)

        composeRule.setContent {
            MaterialTheme {
                InboxContent(
                    uiState = InboxUiState.Success(
                        emails = (1..20).map { testEmail("e$it") },
                        isRefreshing = isRefreshing
                    ),
                    listState = listState,
                    highlightedEmailId = null,
                    showEmailDividers = true,
                    onClearHighlight = {},
                    onMenuClick = {},
                    onSearchClick = {},
                    onEmailClick = {},
                    onRefresh = {},
                    onLoadNextPage = {},
                    onMoveToTrash = {},
                    onFeedbackConsumed = {},
                    onUndoMoveToTrash = {},
                    snackbarHostState = SnackbarHostState()
                )
            }
        }

        // When refreshing ends and was at offset 49, it scrolls to (0, 0)
        isRefreshing = false
        composeRule.waitForIdle()

        assertEquals(0, listState.firstVisibleItemIndex)
        assertEquals(0, listState.firstVisibleItemScrollOffset)
    }

    @Test
    fun refresh_transition_does_not_reset_scroll_when_started_at_offset_50() {
        var isRefreshing by mutableStateOf(true)
        val listState = LazyListState(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 50)

        composeRule.setContent {
            MaterialTheme {
                InboxContent(
                    uiState = InboxUiState.Success(
                        emails = (1..20).map { testEmail("e$it") },
                        isRefreshing = isRefreshing
                    ),
                    listState = listState,
                    highlightedEmailId = null,
                    showEmailDividers = true,
                    onClearHighlight = {},
                    onMenuClick = {},
                    onSearchClick = {},
                    onEmailClick = {},
                    onRefresh = {},
                    onLoadNextPage = {},
                    onMoveToTrash = {},
                    onFeedbackConsumed = {},
                    onUndoMoveToTrash = {},
                    snackbarHostState = SnackbarHostState()
                )
            }
        }

        // When refreshing ends and was at offset 50, it does not reset to 0
        isRefreshing = false
        composeRule.waitForIdle()

        assertEquals(0, listState.firstVisibleItemIndex)
        assertEquals(50, listState.firstVisibleItemScrollOffset)
    }

    @Test
    fun action_feedback_shows_snackbar_and_handles_undo_and_consume() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val undoLabel = context.getString(R.string.action_undo)
        var consumedId: ActionFeedbackId? = null
        var undoId: String? = null

        val feedback = ActionFeedback.MovedToTrash(emailId = "e1")

        composeRule.setContent {
            MaterialTheme {
                InboxContent(
                    uiState = InboxUiState.Success(
                        emails = emptyList(),
                        pendingFeedbackQueue = listOf(feedback)
                    ),
                    listState = rememberLazyListState(),
                    highlightedEmailId = null,
                    showEmailDividers = true,
                    onClearHighlight = {},
                    onMenuClick = {},
                    onSearchClick = {},
                    onEmailClick = {},
                    onRefresh = {},
                    onLoadNextPage = {},
                    onMoveToTrash = {},
                    onFeedbackConsumed = { consumedId = it },
                    onUndoMoveToTrash = { undoId = it },
                    snackbarHostState = remember { SnackbarHostState() }
                )
            }
        }

        composeRule.onNodeWithText(undoLabel).performClick()

        assertEquals(feedback.id, consumedId)
        assertEquals("e1", undoId)
    }

    private fun setContent(
        state: InboxUiState,
        onRefresh: () -> Unit = {},
        onEmailClick: (String) -> Unit = {}
    ) {
        composeRule.setContent {
            MaterialTheme {
                InboxContent(
                    uiState = state,
                    listState = rememberLazyListState(),
                    highlightedEmailId = null,
                    showEmailDividers = true,
                    onClearHighlight = {},
                    onMenuClick = {},
                    onSearchClick = {},
                    onEmailClick = onEmailClick,
                    onRefresh = onRefresh,
                    onLoadNextPage = {},
                    onMoveToTrash = {},
                    onFeedbackConsumed = {},
                    onUndoMoveToTrash = {},
                    snackbarHostState = SnackbarHostState()
                )
            }
        }
    }

    private fun testEmail(id: String) = Email(
        id = id,
        threadId = "thread-$id",
        from = "sender@example.com",
        fromInitials = "S",
        to = "me@example.com",
        subject = "Asunto $id",
        snippet = "Contenido $id",
        timestamp = 1_000L,
        isRead = false,
        isStarred = false,
        hasAttachments = false,
        labels = emptyList(),
        folder = EmailFolder.Inbox
    )

}
