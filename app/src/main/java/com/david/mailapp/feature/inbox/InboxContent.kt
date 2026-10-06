package com.david.mailapp.feature.inbox

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.david.mailapp.ui.components.ShimmerLoading
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay

/**
 * Internal characterization seam for Inbox UI.
 *
 * This function owns only the existing presentation/effects from [InboxScreen].
 * The public screen remains responsible for ViewModel creation and dependency wiring.
 */
@Composable
internal fun InboxContent(
    uiState: InboxUiState,
    listState: LazyListState,
    highlightedEmailId: String?,
    showEmailDividers: Boolean,
    onClearHighlight: () -> Unit,
    onMenuClick: () -> Unit,
    onSearchClick: () -> Unit,
    onEmailClick: (String) -> Unit,
    onRefresh: () -> Unit,
    onLoadNextPage: () -> Unit,
    onMoveToTrash: (String) -> Unit,
    onFeedbackConsumed: (ActionFeedbackId) -> Unit,
    /** Restores a batch of emails from trash. Receives the full list of IDs in the batch. */
    onUndoBatch: (List<String>) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    val pendingFeedback = (uiState as? InboxUiState.Success)
        ?.pendingFeedbackQueue
        ?.firstOrNull()

    ActionFeedbackEffect(
        feedback = pendingFeedback,
        snackbarHostState = snackbarHostState,
        onConsumed = onFeedbackConsumed,
        onUndoBatch = onUndoBatch
    )

    var wasAtTopWhenRefreshStarted by remember { mutableStateOf(false) }

    LaunchedEffect(highlightedEmailId) {
        if (highlightedEmailId != null) {
            delay(2500)
            onClearHighlight()
        }
    }

    val isRefreshing = (uiState as? InboxUiState.Success)?.isRefreshing ?: false
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            wasAtTopWhenRefreshStarted = listState.firstVisibleItemIndex == 0 &&
                listState.firstVisibleItemScrollOffset < 50
        } else {
            if (wasAtTopWhenRefreshStarted) {
                delay(100)
                listState.scrollToItem(0, 0)
            }
            wasAtTopWhenRefreshStarted = false
        }
    }

    val hazeState = remember { HazeState() }
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // Small Material3 top app bar is 64.dp tall; add the status bar it sits under.
    val topBarHeight = statusBarTop + 64.dp

    Box(
        modifier = modifier.fillMaxSize().testTag("inbox_root")
    ) {
        // Content fills the whole screen and scrolls *behind* the glass bar,
        // acting as the haze source that the bar blurs.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState)
        ) {
            when (val state = uiState) {
                InboxUiState.Loading -> ShimmerLoading(
                    modifier = Modifier.testTag("inbox_loading"),
                    topPadding = topBarHeight,
                    bottomPadding = bottomInset
                )
                is InboxUiState.Error -> {
                    InboxErrorContent(
                        reason = state.reason,
                        onRetry = onRefresh,
                        modifier = Modifier.testTag("inbox_error")
                    )
                }
                is InboxUiState.Success -> {
                    InboxSuccessContent(
                        isRefreshing = state.isRefreshing,
                        onRefresh = onRefresh,
                        indicatorTopOffset = topBarHeight,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        InboxEmailList(
                            state = state,
                            listState = listState,
                            highlightedEmailId = highlightedEmailId,
                            showEmailDividers = showEmailDividers,
                            onClearHighlight = onClearHighlight,
                            onEmailClick = onEmailClick,
                            onLoadNextPage = onLoadNextPage,
                            onMoveToTrash = onMoveToTrash,
                            contentPadding = PaddingValues(top = topBarHeight, bottom = bottomInset + 24.dp),
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        InboxTopBar(
            onMenuClick = onMenuClick,
            onSearchClick = onSearchClick,
            hazeState = hazeState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 88.dp, start = 16.dp, end = 16.dp)
        )
    }
}
