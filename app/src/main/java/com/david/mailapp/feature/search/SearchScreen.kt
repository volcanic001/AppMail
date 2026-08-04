package com.david.mailapp.feature.search

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.david.mailapp.ui.components.ContainedLoadingIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.david.mailapp.core.di.AppContainer
import com.david.mailapp.feature.search.components.SearchEmptyState
import com.david.mailapp.feature.search.components.SearchErrorState
import com.david.mailapp.feature.search.components.SearchResultItem
import com.david.mailapp.feature.search.components.SearchSuggestionChips
import com.david.mailapp.feature.search.components.SearchTopBar
import com.david.mailapp.ui.theme.MotionTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onEmailClick: (String) -> Unit,
    listState: LazyListState,
    highlightedEmailId: String? = null,
    showEmailDividers: Boolean = true,
    onClearHighlight: () -> Unit = {},
    modifier: Modifier = Modifier,
    entryKey: Any = Unit
) {
    val viewModel: SearchViewModel = viewModel(
        factory = SearchViewModel.Factory(
            AppContainer.emailRepository,
            AppContainer.searchHistoryStore,
            AppContainer.sessionWriteGuard
        )
    )

    val query by viewModel.query.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val history by viewModel.historyFlow.collectAsState(initial = emptyList())
    
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(highlightedEmailId) {
        if (highlightedEmailId != null) {
            delay(2500)
            onClearHighlight()
        }
    }

    LaunchedEffect(uiState) {
        Log.d("SearchDebug", "[SearchScreen] Recomposed with UI State: ${uiState::class.simpleName}")
    }

    BackHandler(onBack = {
        Log.d("SearchDebug", "[SearchScreen] BackHandler triggered")
        keyboardController?.hide()
        focusManager.clearFocus()
        onBack()
    })

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            SearchTopBar(
                entryKey = entryKey,
                query = query,
                onQueryChange = viewModel::onQueryChange,
                onBack = {
                    Log.d("SearchDebug", "[SearchScreen] TopBar onBack clicked")
                    keyboardController?.hide()
                    focusManager.clearFocus()
                    onBack()
                },
                onClear = viewModel::clearQuery
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
        ) {
            when (val state = uiState) {
                is SearchUiState.Idle -> {
                    SearchSuggestionChips(
                        history = history,
                        onQuerySelected = viewModel::onQueryChange,
                        onClearHistory = viewModel::clearHistory
                    )
                }

                is SearchUiState.Loading -> {
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(MotionTokens.tweenShort()),
                        exit = fadeOut(MotionTokens.tweenShort())
                    ) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    // Keep showing results or chips underneath
                    SearchSuggestionChips(
                        history = history,
                        onQuerySelected = viewModel::onQueryChange,
                        onClearHistory = viewModel::clearHistory
                    )
                }

                is SearchUiState.Results -> {
                    ResultList(
                        state = state,
                        viewModel = viewModel,
                        listState = listState,
                        highlightedEmailId = highlightedEmailId,
                        showEmailDividers = showEmailDividers,
                        onClearHighlight = onClearHighlight,
                        onEmailClick = onEmailClick
                    )
                }

                is SearchUiState.Empty -> {
                    SearchEmptyState(query = state.query)
                }

                is SearchUiState.Error -> {
                    SearchErrorState(
                        reason = state.reason,
                        onRetry = viewModel::retry
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultList(
    state: SearchUiState.Results,
    viewModel: SearchViewModel,
    listState: LazyListState,
    highlightedEmailId: String? = null,
    showEmailDividers: Boolean = true,
    onClearHighlight: () -> Unit = {},
    onEmailClick: (String) -> Unit
) {
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        itemsIndexed(
            items = state.emails,
            key = { _, email -> email.id }
        ) { index, email ->
            SearchResultItem(
                email = email,
                query = state.query,
                delayMs = index * MotionTokens.staggerDelayMs,
                isHighlighted = (email.id == highlightedEmailId),
                showDivider = showEmailDividers,
                onClearHighlight = onClearHighlight,
                onClick = { onEmailClick(email.id) }
            )
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

    // Pagination trigger
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
