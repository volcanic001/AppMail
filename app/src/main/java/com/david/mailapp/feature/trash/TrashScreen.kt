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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.david.mailapp.R
import com.david.mailapp.core.di.AppContainer
import com.david.mailapp.core.localization.asString
import com.david.mailapp.core.localization.toUiText

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
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.trash_title), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.action_menu))
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
                    Text(stringResource(R.string.error_symbol), fontSize = 48.sp)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        state.reason.toUiText().asString(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { viewModel.refresh() }) { Text(stringResource(R.string.action_retry)) }
                }
            }

            is TrashUiState.Success -> {
                if (state.emails.isEmpty() && !state.isRefreshing) {
                    EmptyTrash()
                } else {
                    TrashContent(
                        state = state,
                        listState = listState,
                        snackbarHostState = snackbarHostState,
                        highlightedEmailId = highlightedEmailId,
                        onEmailClick = onEmailClick,
                        onDeletePermanently = viewModel::deletePermanently,
                        onRestoreToInbox = viewModel::restoreToInbox,
                        onRefresh = viewModel::refresh,
                        onLoadNextPage = viewModel::loadNextPage,
                        onClearHighlight = onClearHighlight,
                        bottomPadding = paddingValues.calculateBottomPadding()
                    )
                }
            }
        }
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
        Text(stringResource(R.string.trash_empty_symbol), fontSize = 48.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.trash_empty),
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
