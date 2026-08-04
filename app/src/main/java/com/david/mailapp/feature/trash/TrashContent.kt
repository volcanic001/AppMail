package com.david.mailapp.feature.trash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.david.mailapp.R
import com.david.mailapp.core.localization.asString
import com.david.mailapp.core.localization.toUiText
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
 * Owns the delete-confirmation seam and renders only feedback already
 * confirmed by the ViewModel.
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
    modifier: Modifier = Modifier
) {
    val currentDeleteCallback by rememberUpdatedState(onDeletePermanently)
    val deleteCoordinator = remember { TrashDeleteCoordinator() }
    var pendingDeleteEmailId by remember { mutableStateOf<String?>(null) }
    SideEffect {
        deleteCoordinator.onConfirmed = currentDeleteCallback
    }

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
                                deleteCoordinator.requestDelete(email.id)
                                pendingDeleteEmailId = deleteCoordinator.pendingDeleteEmailId
                            }
                        }
                        val onRestoreRemembered = remember(email.id) {
                            { onRestoreToInbox(email.id) }
                        }
                        EmailListItem(
                            email = email,
                            onClick = onClickRemembered,
                            onDelete = onDeleteRemembered,
                            onRestore = onRestoreRemembered,
                            actionsEnabled = email.id !in state.activeActionEmailIds &&
                                email.id != pendingDeleteEmailId,
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
                .padding(bottom = 24.dp)
        )
    }

    if (pendingDeleteEmailId != null) {
        BasicAlertDialog(
            onDismissRequest = {
                deleteCoordinator.cancelDelete()
                pendingDeleteEmailId = null
            }
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Ícono con contenedor circular semántico
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteForever,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Título
                    Text(
                        text = stringResource(R.string.trash_delete_dialog_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Subtítulo
                    Text(
                        text = stringResource(R.string.trash_delete_dialog_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Botón Cancelar (menor jerarquía visual)
                    TextButton(
                        onClick = {
                            deleteCoordinator.cancelDelete()
                            pendingDeleteEmailId = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Text(stringResource(R.string.trash_delete_dialog_cancel))
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Botón Eliminar permanentemente (acción destructiva principal)
                    Button(
                        onClick = {
                            deleteCoordinator.confirmDelete()
                            pendingDeleteEmailId = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Text(stringResource(R.string.trash_delete_dialog_confirm))
                    }
                }
            }
        }
    }
}
