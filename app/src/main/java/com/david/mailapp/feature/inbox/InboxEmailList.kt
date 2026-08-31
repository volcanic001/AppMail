package com.david.mailapp.feature.inbox

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.david.mailapp.feature.inbox.components.EmailListItem
import com.david.mailapp.ui.components.ContainedLoadingIndicator

@Composable
internal fun InboxEmailList(
    state: InboxUiState.Success,
    listState: LazyListState,
    highlightedEmailId: String?,
    showEmailDividers: Boolean,
    onClearHighlight: () -> Unit,
    onEmailClick: (String) -> Unit,
    onMoveToTrash: (String) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = listState,
        modifier = modifier.testTag("inbox_list"),
        contentPadding = contentPadding
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
