package com.david.mailapp.feature.inbox

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
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
