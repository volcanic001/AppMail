package com.david.mailapp.feature.inbox

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.david.mailapp.R
import com.david.mailapp.core.di.AppContainer
import com.david.mailapp.core.localization.asString
import com.david.mailapp.core.localization.toUiText
import com.david.mailapp.feature.inbox.components.EmailListItem
import com.david.mailapp.ui.components.ContainedLoadingIndicator
import com.david.mailapp.ui.theme.MotionTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    listState: LazyListState,
    highlightedEmailId: String? = null,
    showEmailDividers: Boolean = true,
    onClearHighlight: () -> Unit = {},
    onMenuClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onEmailClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val repository = AppContainer.emailRepository
    val viewModel: InboxViewModel = viewModel(
        factory = InboxViewModel.Factory(repository)
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val pendingFeedback = (uiState as? InboxUiState.Success)
        ?.pendingFeedbackQueue
        ?.firstOrNull()

    ActionFeedbackEffect(
        feedback = pendingFeedback,
        snackbarHostState = snackbarHostState,
        onConsumed = viewModel::consumeFeedback,
        onUndoMoveToTrash = viewModel::undoMoveToTrash
    )

    // ── Pull-to-refresh UX state ───────────────────────────────
    // Tracks whether the list was at the top when the user started refreshing.
    // If so, after refresh completes we snap back to 0 so new emails animate in
    // with the existing animateItem "push down" effect.
    var wasAtTopWhenRefreshStarted by remember { mutableStateOf(false) }

    LaunchedEffect(highlightedEmailId) {
        if (highlightedEmailId != null) {
            delay(2500)
            onClearHighlight()
        }
    }

    // ── Refresh state transitions ──────────────────────────────
    // When isRefreshing starts: snapshot position.
    // When isRefreshing ends:
    //   • If user was at top → snap to 0 so animateItem does the visual work.
    val isRefreshing = (uiState as? InboxUiState.Success)?.isRefreshing ?: false
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            wasAtTopWhenRefreshStarted = listState.firstVisibleItemIndex == 0
                    && listState.firstVisibleItemScrollOffset < 50
        } else {
            if (wasAtTopWhenRefreshStarted) {
                // Wait for the PTR indicator collapse animation to finish,
                // then snap the list to pixel-0 so the newest email is fully visible.
                delay(100)
                listState.scrollToItem(0, 0)
            }
            wasAtTopWhenRefreshStarted = false
        }
    }

    // ── Search icon tap animation ──────────────────────────────
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
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.inbox_title), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(imageVector = Icons.Default.Menu, contentDescription = stringResource(R.string.action_menu))
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
            is InboxUiState.Loading -> {
                ShimmerLoading()
            }

            is InboxUiState.Error -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
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
                    Button(onClick = { viewModel.refresh() }) {
                        Text(stringResource(R.string.action_retry))
                    }
                }
            }

            is InboxUiState.Success -> {
                val ptrState = rememberPullToRefreshState()
                PullToRefreshBox(
                    state = ptrState,
                    isRefreshing = state.isRefreshing,
                    onRefresh = { viewModel.refresh() },
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
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            bottom = paddingValues.calculateBottomPadding() + 24.dp
                        )
                    ) {
                        if (state.emails.isEmpty()) {
                            item(key = "empty") {
                                Box(
                                    modifier = Modifier.fillParentMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    EmptyInbox()
                                }
                            }
                        }
                        else {
                            items(
                                items = state.emails,
                                key = { it.id }
                            ) { email ->
                                val onClickRemembered = remember(email.id) {
                                    {
                                        onEmailClick(email.id)
                                    }
                                }
                                val onDeleteRemembered = remember(email.id) {
                                    { viewModel.moveToTrash(email.id) }
                                }
                                EmailListItem(
                                    email = email,
                                    onClick = onClickRemembered,
                                    onDelete = onDeleteRemembered,
                                    actionsEnabled = email.id !in state.activeActionEmailIds,
                                    showDivider = showEmailDividers,
                                    isHighlighted = (email.id == highlightedEmailId),
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
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
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

                // Pagination: trigger loadMore when near the bottom.
                LaunchedEffect(listState, state.emails.isEmpty()) {
                    snapshotFlow {
                        val layout = listState.layoutInfo
                        val lastIndex = layout.visibleItemsInfo.lastOrNull()?.index ?: 0
                        lastIndex to layout.totalItemsCount
                    }
                        .distinctUntilChanged()
                        .collect { (lastVisible, total) ->
                            if (state.emails.isNotEmpty() && total > 0 && lastVisible >= total - 3) {
                                viewModel.loadNextPage()
                            }
                        }
                }
            }
        }

        // Snackbar — above the nav bar
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        )
    }
    }
}

// ── Sub-composables ─────────────────────────────────────────────


@Composable
private fun EmptyInbox() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = stringResource(R.string.inbox_empty_symbol), fontSize = 48.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.inbox_empty),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ShimmerLoading() {
    val shimmerBase = MaterialTheme.colorScheme.surfaceVariant
    val shimmerColors = listOf(
        shimmerBase.copy(alpha = 0.3f),
        shimmerBase.copy(alpha = 0.6f),
        shimmerBase.copy(alpha = 0.3f)
    )

    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateX = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerX"
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateX.value - 200f, 0f),
        end = Offset(translateX.value + 200f, 0f)
    )

    Column(modifier = Modifier.padding(top = 16.dp)) {
        repeat(8) {
            ShimmerRow(brush)
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun ShimmerRow(brush: Brush) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(brush)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )
        }
    }
}
