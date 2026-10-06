package com.david.mailapp.feature.spam

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.david.mailapp.R
import com.david.mailapp.core.di.AppContainer
import com.david.mailapp.core.localization.asString
import com.david.mailapp.core.localization.toUiText
import com.david.mailapp.ui.components.ShimmerLoading
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.HazeMaterials

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpamScreen(
    listState: LazyListState,
    highlightedEmailId: String? = null,
    showEmailDividers: Boolean = true,
    onClearHighlight: () -> Unit = {},
    onMenuClick: () -> Unit = {},
    onEmailClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val repository = AppContainer.emailRepository
    val viewModel: SpamViewModel = viewModel(
        factory = SpamViewModel.Factory(repository)
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val hazeState = remember { HazeState() }
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // Small Material3 top app bar is 64.dp tall; add the status bar it sits under.
    val topBarHeight = statusBarTop + 64.dp

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // Content fills the whole screen and scrolls *behind* the glass bar,
        // acting as the haze source that the bar blurs.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState)
        ) {
            when (val state = uiState) {
                is SpamUiState.Loading -> ShimmerLoading(
                    topPadding = topBarHeight,
                    bottomPadding = bottomInset
                )

                is SpamUiState.Error -> {
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

                is SpamUiState.Success -> {
                    SpamContent(
                        state = state,
                        listState = listState,
                        snackbarHostState = snackbarHostState,
                        highlightedEmailId = highlightedEmailId,
                        showEmailDividers = showEmailDividers,
                        onEmailClick = onEmailClick,
                        onMarkNotSpam = viewModel::markNotSpam,
                        onDeleteToTrash = viewModel::deleteToTrash,
                        onFeedbackConsumed = viewModel::consumeFeedback,
                        onRefresh = viewModel::refresh,
                        onLoadNextPage = viewModel::loadNextPage,
                        onClearHighlight = onClearHighlight,
                        topPadding = topBarHeight,
                        bottomPadding = bottomInset
                    )
                }
            }
        }

        TopAppBar(
            title = { Text(stringResource(R.string.spam_title), style = MaterialTheme.typography.titleLarge) },
            navigationIcon = {
                IconButton(onClick = onMenuClick) {
                    Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.action_menu))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent
            ),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .hazeEffect(state = hazeState, style = HazeMaterials.thin())
        )
    }
}

// ── Sub-composables ─────────────────────────────────────────────

@Composable
internal fun EmptySpam() {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(R.string.spam_empty_symbol), fontSize = 48.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.spam_empty),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
