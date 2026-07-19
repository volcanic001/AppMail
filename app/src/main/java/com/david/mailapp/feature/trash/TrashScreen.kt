package com.david.mailapp.feature.trash

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import com.david.mailapp.ui.components.ContainedLoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.david.mailapp.core.di.AppContainer
import com.david.mailapp.feature.inbox.components.EmailListItem
import com.david.mailapp.ui.theme.MotionTokens
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    listState: LazyListState,
    highlightedEmailId: String? = null,
    onClearHighlight: () -> Unit = {},
    onMenuClick: () -> Unit = {},
    onEmailClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val repository = AppContainer.emailRepository
    val viewModel: TrashViewModel = viewModel(
        factory = TrashViewModel.Factory(repository)
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var snackbarJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(highlightedEmailId) {
        if (highlightedEmailId != null) {
            delay(2500)
            onClearHighlight()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Papelera", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.Menu, contentDescription = "Menú")
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
            is TrashUiState.Loading -> ShimmerLoading()

            is TrashUiState.Error -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("⚠️", fontSize = 48.sp)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        state.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { viewModel.refresh() }) { Text("Retry") }
                }
            }

            is TrashUiState.Success -> {
                if (state.emails.isEmpty() && !state.isRefreshing) {
                    EmptyTrash()
                } else {
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
                                            viewModel.deletePermanently(email.id)
                                            val result = snackbarHostState.showSnackbar(
                                                message = "Deleted permanently",
                                                actionLabel = "Undo",
                                                duration = SnackbarDuration.Short
                                            )
                                            if (result == SnackbarResult.ActionPerformed) {
                                                viewModel.restoreToInbox(email.id)
                                            }
                                        }
                                    }
                                }
                                val onRestoreRemembered = remember(email.id) {
                                    {
                                        snackbarJob?.cancel()
                                        snackbarJob = scope.launch {
                                            viewModel.restoreToInbox(email.id)
                                            snackbarHostState.showSnackbar("Restored to inbox")
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
                                    modifier = Modifier.animateItem(
                                        placementSpec = MotionTokens.listReorganize
                                    )
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
                                    viewModel.loadNextPage()
                                }
                            }
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
}

// ── Sub-composables ─────────────────────────────────────────────

@Composable
private fun EmptyTrash() {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🗑️", fontSize = 48.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            "Trash is empty",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ShimmerLoading() {
    val base = MaterialTheme.colorScheme.surfaceVariant
    val colors = listOf(base.copy(alpha = 0.3f), base.copy(alpha = 0.6f), base.copy(alpha = 0.3f))
    val transition = rememberInfiniteTransition(label = "s")
    val tx = transition.animateFloat(0f, 1200f, infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart), label = "sx")
    val brush = Brush.linearGradient(colors, Offset(tx.value - 200f, 0f), Offset(tx.value + 200f, 0f))
    Column(Modifier.padding(top = 16.dp)) {
        repeat(8) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(brush))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Box(Modifier.fillMaxWidth(0.7f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(brush))
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.fillMaxWidth(0.9f).height(12.dp).clip(RoundedCornerShape(4.dp)).background(brush))
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.fillMaxWidth(0.5f).height(10.dp).clip(RoundedCornerShape(4.dp)).background(brush))
                }
            }
        }
    }
}
