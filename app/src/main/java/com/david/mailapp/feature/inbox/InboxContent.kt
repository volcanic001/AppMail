package com.david.mailapp.feature.inbox

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Internal characterization seam for Inbox UI.
 *
 * This function owns only the existing presentation/effects from [InboxScreen].
 * The public screen remains responsible for ViewModel creation and dependency wiring.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    onUndoMoveToTrash: (String) -> Unit,
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
        onUndoMoveToTrash = onUndoMoveToTrash
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

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("inbox_root"),
        topBar = {
            InboxTopBar(onMenuClick = onMenuClick, onSearchClick = onSearchClick)
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
        ) {
            when (val state = uiState) {
                InboxUiState.Loading -> ShimmerLoading()
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
                        modifier = Modifier.fillMaxSize()
                    ) {
                        InboxEmailList(
                            state = state,
                            listState = listState,
                            highlightedEmailId = highlightedEmailId,
                            showEmailDividers = showEmailDividers,
                            onClearHighlight = onClearHighlight,
                            onEmailClick = onEmailClick,
                            onMoveToTrash = onMoveToTrash,
                            contentPadding = PaddingValues(bottom = paddingValues.calculateBottomPadding() + 24.dp),
                            modifier = Modifier.fillMaxSize()
                        )

                        LaunchedEffect(listState, state.emails.isEmpty()) {
                            snapshotFlow {
                                val layout = listState.layoutInfo
                                val lastIndex = layout.visibleItemsInfo.lastOrNull()?.index ?: 0
                                lastIndex to layout.totalItemsCount
                            }
                                .distinctUntilChanged()
                                .collect { (lastVisible, total) ->
                                    if (state.emails.isNotEmpty() && total > 0 && lastVisible >= total - 3) {
                                        onLoadNextPage()
                                    }
                                }
                        }
                    }
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)
            )
        }
    }
}
