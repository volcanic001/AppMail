package com.david.mailapp.feature.inbox

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.david.mailapp.core.di.AppContainer

/** Public Inbox entry point. ViewModel creation and dependency wiring stay here. */
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

    InboxContent(
        uiState = uiState,
        listState = listState,
        highlightedEmailId = highlightedEmailId,
        showEmailDividers = showEmailDividers,
        onClearHighlight = onClearHighlight,
        onMenuClick = onMenuClick,
        onSearchClick = onSearchClick,
        onEmailClick = onEmailClick,
        onRefresh = viewModel::refresh,
        onLoadNextPage = viewModel::loadNextPage,
        onMoveToTrash = viewModel::moveToTrash,
        onFeedbackConsumed = viewModel::consumeFeedback,
        onUndoMoveToTrash = viewModel::undoMoveToTrash,
        snackbarHostState = snackbarHostState,
        modifier = modifier
    )
}
