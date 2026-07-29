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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.david.mailapp.R
import com.david.mailapp.core.localization.asString
import com.david.mailapp.core.localization.toUiText
import com.david.mailapp.domain.model.Email
import com.david.mailapp.feature.inbox.components.EmailListItem
import com.david.mailapp.ui.components.ContainedLoadingIndicator
import com.david.mailapp.ui.theme.MotionTokens
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Internal composable extracted from [TrashScreen] for testability.
 *
 * Receives all UI state and callbacks as parameters so that C9
 * (delete-permanently contract) can be verified via Compose UI tests
 * without depending on the full TrashScreen composable.
 *
 * Behavior is identical to the inline code in TrashScreen; no
 * functional changes are introduced here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashContent(
    state: TrashUiState.Success,
    listState: LazyListState,
    snackbarHostState: SnackbarHostState,
    highlightedEmailId: String?,
    onEmailClick: (String) -> Unit,
    onDeletePermanently: (String) -> Unit,
    onRestoreToInbox: (String) -> Unit,
    onRefresh: () -> Unit,
    onLoadNextPage: () -> Unit,
    onClearHighlight: () -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    val snackbarDeletedPermanently = stringResource(R.string.snackbar_deleted_permanently)
    val snackbarUndo = stringResource(R.string.action_undo)
    val snackbarRestoredToInbox = stringResource(R.string.snackbar_restored_to_inbox)
    val scope = rememberCoroutineScope()
    var snackbarJob: Job? by remember { androidx.compose.runtime.mutableStateOf(null) }
    val deleteCoordinator = remember(onDeletePermanently, onRestoreToInbox) {
        TrashDeleteCoordinator(onDeletePermanently, onRestoreToInbox)
    }

    LaunchedEffect(highlightedEmailId) {
        if (highlightedEmailId != null) {
            kotlinx.coroutines.delay(2500)
            onClearHighlight()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (state.emails.isEmpty() && !state.isRefreshing) {
            // Empty state is rendered in TrashScreen
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
                                    translationY = if (state.isRefreshing) 24.dp.toPx() else (fraction * 40.dp.toPx())
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
                            {
                                snackbarJob?.cancel()
                                snackbarJob = scope.launch {
                                    deleteCoordinator.requestDelete(email.id)
                                    val result = snackbarHostState.showSnackbar(
                                        message = snackbarDeletedPermanently,
                                        actionLabel = snackbarUndo,
                                        duration = androidx.compose.material3.SnackbarDuration.Short
                                    )
                                    if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                                        deleteCoordinator.undo(email.id)
                                    }
                                }
                            }
                        }
                        val onRestoreRemembered = remember(email.id) {
                            {
                                snackbarJob?.cancel()
                                snackbarJob = scope.launch {
                                    onRestoreToInbox(email.id)
                                    snackbarHostState.showSnackbar(snackbarRestoredToInbox)
                                }
                            }
                        }
                        EmailListItem(
                            email = email,
                            onClick = onClickRemembered,
                            onDelete = onDeleteRemembered,
                            onRestore = onRestoreRemembered,
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
                .padding(bottom = 24.dp)
        )
    }
}
