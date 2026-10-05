package com.david.mailapp.feature.trash

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.david.mailapp.feature.inbox.ActionFeedbackEffect
import com.david.mailapp.feature.inbox.ActionFeedbackId
import com.david.mailapp.feature.inbox.components.EmailListItem
import com.david.mailapp.ui.components.ContainedLoadingIndicator
import com.david.mailapp.ui.theme.MotionTokens
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Internal composable extracted from [TrashScreen] for testability.
 *
 * Receives all UI state and callbacks as parameters so that C9
 * (delete-permanently contract) can be verified via Compose UI tests
 * without depending on the full TrashScreen composable.
 *
 * Swipe-to-delete removes the email immediately (no confirmation dialog);
 * the resulting "deleted permanently" feedback is surfaced by the ViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashContent(
    state: TrashUiState.Success,
    listState: LazyListState,
    snackbarHostState: SnackbarHostState,
    highlightedEmailId: String?,
    showEmailDividers: Boolean = true,
    onEmailClick: (String) -> Unit,
    onDeletePermanently: (String) -> Unit,
    onRestoreToInbox: (String) -> Unit,
    onFeedbackConsumed: (ActionFeedbackId) -> Unit,
    onRefresh: () -> Unit,
    onLoadNextPage: () -> Unit,
    onClearHighlight: () -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
    /** Offsets the list and refresh indicator below the glass top bar. */
    topPadding: androidx.compose.ui.unit.Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    ActionFeedbackEffect(
        feedback = state.pendingFeedbackQueue.firstOrNull(),
        snackbarHostState = snackbarHostState,
        onConsumed = onFeedbackConsumed
    )

    LaunchedEffect(highlightedEmailId) {
        if (highlightedEmailId != null) {
            kotlinx.coroutines.delay(2500)
            onClearHighlight()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (state.emails.isEmpty() && !state.isRefreshing) {
            EmptyTrash()
        } else {
            val ptrState = rememberPullToRefreshState()
            PullToRefreshBox(
                state = ptrState,
                isRefreshing = state.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
                indicator = {
                    val isVisible = state.isRefreshing || ptrState.distanceFraction > 0f
                    if (isVisible) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .graphicsLayer {
                                    val fraction = ptrState.distanceFraction.coerceIn(0f, 1.5f)
                                    val base = topPadding.toPx()
                                    translationY = base + if (state.isRefreshing) 24.dp.toPx() else (fraction * 40.dp.toPx())
                                    val scale = if (state.isRefreshing) 1f else (fraction * 1.2f).coerceIn(0f, 1f)
                                    scaleX = scale
                                    scaleY = scale
                                    alpha = if (state.isRefreshing) 1f else fraction.coerceIn(0f, 1f)
                                }
                        ) {
                            ContainedLoadingIndicator(
                                containerSize = 48.dp,
                                indicatorSize = 32.dp,
                                progress = if (state.isRefreshing) null else ptrState.distanceFraction.coerceIn(0f, 1f)
                            )
                        }
                    }
                }
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("trash_list"),
                    contentPadding = PaddingValues(
                        top = topPadding,
                        bottom = bottomPadding + 24.dp
                    )
                ) {
                    items(
                        items = state.emails,
                        key = { it.id }
                    ) { email ->
                        val onClickRemembered = remember(email.id) {
                            { onEmailClick(email.id) }
                        }
                        val onDeleteRemembered = remember(email.id) {
                            { onDeletePermanently(email.id) }
                        }
                        val onRestoreRemembered = remember(email.id) {
                            { onRestoreToInbox(email.id) }
                        }
                        EmailListItem(
                            email = email,
                            onClick = onClickRemembered,
                            onDelete = onDeleteRemembered,
                            onRestore = onRestoreRemembered,
                            actionsEnabled = email.id !in state.activeActionEmailIds,
                            showDivider = showEmailDividers,
                            isHighlighted = (email.id == highlightedEmailId),
                            onClearHighlight = onClearHighlight,
                            modifier = Modifier.animateItem(placementSpec = MotionTokens.listReorganize)
                        )
                    }

                    if (state.isLoadingNextPage) {
                        item(key = "loader") {
                            Box(
                                Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                ContainedLoadingIndicator(
                                    containerSize = 44.dp,
                                    indicatorSize = 28.dp
                                )
                            }
                        }
                    }
                }
            }

            LaunchedEffect(listState) {
                snapshotFlow {
                    val layout = listState.layoutInfo
                    val lastIdx = layout.visibleItemsInfo.lastOrNull()?.index ?: 0
                    lastIdx to layout.totalItemsCount
                }
                    .distinctUntilChanged()
                    .collect { (last, total) ->
                        if (total > 0 && last >= total - 3) {
                            onLoadNextPage()
                        }
                    }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 88.dp, start = 16.dp, end = 16.dp)
        )
    }
}
