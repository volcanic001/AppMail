package com.david.mailapp.feature.trash

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
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
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
fun TrashScreen(
    listState: LazyListState,
    highlightedEmailId: String? = null,
    showEmailDividers: Boolean = true,
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
    val successState = uiState as? TrashUiState.Success
    var showEmptyDialog by remember { mutableStateOf(false) }

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
                is TrashUiState.Loading -> ShimmerLoading(
                    topPadding = topBarHeight,
                    bottomPadding = bottomInset
                )

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
                    TrashContent(
                        state = state,
                        listState = listState,
                        snackbarHostState = snackbarHostState,
                        highlightedEmailId = highlightedEmailId,
                        showEmailDividers = showEmailDividers,
                        onEmailClick = onEmailClick,
                        onDeletePermanently = viewModel::deletePermanently,
                        onRestoreToInbox = viewModel::restoreToInbox,
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
            title = { Text(stringResource(R.string.trash_title), style = MaterialTheme.typography.titleLarge) },
            navigationIcon = {
                IconButton(onClick = onMenuClick) {
                    Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.action_menu))
                }
            },
            actions = {
                if (successState != null && successState.emails.isNotEmpty()) {
                    IconButton(
                        onClick = { showEmptyDialog = true },
                        enabled = successState.canEmptyTrash
                    ) {
                        Icon(
                            Icons.Default.DeleteSweep,
                            contentDescription = stringResource(R.string.trash_empty_action)
                        )
                    }
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

        if (showEmptyDialog) {
            AlertDialog(
                onDismissRequest = { showEmptyDialog = false },
                title = { Text(stringResource(R.string.trash_empty_dialog_title)) },
                text = { Text(stringResource(R.string.trash_empty_dialog_body)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showEmptyDialog = false
                            viewModel.emptyTrash()
                        }
                    ) { Text(stringResource(R.string.trash_empty_dialog_confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { showEmptyDialog = false }) {
                        Text(stringResource(R.string.trash_empty_dialog_cancel))
                    }
                }
            )
        }
    }
}

// ── Sub-composables ─────────────────────────────────────────────

@Composable
internal fun EmptyTrash() {
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
