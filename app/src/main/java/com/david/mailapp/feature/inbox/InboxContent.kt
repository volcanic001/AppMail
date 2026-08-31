package com.david.mailapp.feature.inbox

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import com.david.mailapp.R
import com.david.mailapp.core.localization.asString
import com.david.mailapp.core.localization.toUiText
import com.david.mailapp.feature.inbox.components.EmailListItem
import com.david.mailapp.ui.components.ContainedLoadingIndicator
import com.david.mailapp.ui.theme.MotionTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

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
    val scope = rememberCoroutineScope()
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

    val searchIconScale = remember { Animatable(1f) }
    val onSearchTap: () -> Unit = {
        scope.launch {
            searchIconScale.snapTo(MotionTokens.pressScale)
            searchIconScale.animateTo(1.02f, MotionTokens.iconTap)
            searchIconScale.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 500f))
        }
        onSearchClick()
    }

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("inbox_root"),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.inbox_title), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.action_menu))
                    }
                },
                actions = {
                    IconButton(onClick = onSearchTap) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(R.string.action_search),
                            modifier = Modifier.scale(searchIconScale.value)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                )
            )
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
                    Column(
                        modifier = Modifier.fillMaxSize().testTag("inbox_error"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(text = stringResource(R.string.error_symbol), fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = state.reason.toUiText().asString(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onRefresh) { Text(stringResource(R.string.action_retry)) }
                    }
                }
                is InboxUiState.Success -> {
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
                                        .testTag("inbox_refresh_indicator")
                                        .graphicsLayer {
                                            val fraction = ptrState.distanceFraction.coerceIn(0f, 1.5f)
                                            translationY = if (state.isRefreshing) 24.dp.toPx() else fraction * 40.dp.toPx()
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
                            modifier = Modifier.fillMaxSize().testTag("inbox_list"),
                            contentPadding = PaddingValues(bottom = paddingValues.calculateBottomPadding() + 24.dp)
                        ) {
                            if (state.emails.isEmpty()) {
                                item(key = "empty") {
                                    Box(
                                        modifier = Modifier.fillParentMaxSize().testTag("inbox_empty"),
                                        contentAlignment = Alignment.Center
                                    ) { EmptyInbox() }
                                }
                            } else {
                                items(items = state.emails, key = { it.id }) { email ->
                                    val onClickRemembered = remember(email.id) { { onEmailClick(email.id) } }
                                    val onDeleteRemembered = remember(email.id) { { onMoveToTrash(email.id) } }
                                    EmailListItem(
                                        email = email,
                                        onClick = onClickRemembered,
                                        onDelete = onDeleteRemembered,
                                        actionsEnabled = email.id !in state.activeActionEmailIds,
                                        showDivider = showEmailDividers,
                                        isHighlighted = email.id == highlightedEmailId,
                                        onClearHighlight = onClearHighlight,
                                        modifier = Modifier.animateItem(
                                            fadeInSpec = tween(durationMillis = 280),
                                            placementSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            ),
                                            fadeOutSpec = tween(durationMillis = 180)
                                        )
                                    )
                                }
                            }

                            if (state.isLoadingNextPage) {
                                item(key = "loader") {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("inbox_next_page_loader"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        ContainedLoadingIndicator(containerSize = 44.dp, indicatorSize = 28.dp)
                                    }
                                }
                            }
                        }
                    }

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

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)
            )
        }
    }
}
