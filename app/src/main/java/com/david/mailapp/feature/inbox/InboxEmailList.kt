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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.david.mailapp.R
import com.david.mailapp.feature.inbox.components.EmailListItem
import com.david.mailapp.ui.components.ContainedLoadingIndicator
import kotlinx.coroutines.flow.distinctUntilChanged
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone

internal class InboxTimeFormatter(
    private val pattern: String,
    private val locale: java.util.Locale,
    private val timeZone: TimeZone = TimeZone.getDefault()
) {
    private val simpleDateFormat = SimpleDateFormat(pattern, locale).apply {
        timeZone = this@InboxTimeFormatter.timeZone
    }

    @Synchronized
    fun format(timestamp: Long): String {
        return simpleDateFormat.format(Date(timestamp))
    }
}

@Composable
internal fun InboxEmailList(
    state: InboxUiState.Success,
    listState: LazyListState,
    highlightedEmailId: String?,
    showEmailDividers: Boolean,
    onClearHighlight: () -> Unit,
    onEmailClick: (String) -> Unit,
    onLoadNextPage: () -> Unit,
    onMoveToTrash: (String) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val timePattern = stringResource(R.string.date_pattern_time)
    val locale = LocalLocale.current.platformLocale
    val timeZoneId = TimeZone.getDefault().id
    val formatter = remember(timePattern, locale, timeZoneId) {
        InboxTimeFormatter(
            pattern = timePattern,
            locale = locale,
            timeZone = TimeZone.getTimeZone(timeZoneId)
        )
    }

    LazyColumn(
        state = listState,
        modifier = modifier.testTag("inbox_list"),
        contentPadding = contentPadding
    ) {
        if (state.visibleEmails.isEmpty()) {
            item(key = "empty") {
                Box(
                    modifier = Modifier.fillParentMaxSize().testTag("inbox_empty"),
                    contentAlignment = Alignment.Center
                ) { EmptyInbox() }
            }
        } else {
            items(items = state.visibleEmails, key = { it.id }) { email ->
                val formattedTime = remember(email.timestamp, formatter) {
                    formatter.format(email.timestamp)
                }
                val onClickRemembered = remember(email.id) {
                    {
                        com.david.mailapp.core.perf.MailOpenPerformanceTrace.onInboxItemClicked(email.id)
                        onEmailClick(email.id)
                    }
                }
                val onDeleteRemembered = remember(email.id) { { onMoveToTrash(email.id) } }
                EmailListItem(
                    email = email,
                    onClick = onClickRemembered,
                    onDelete = onDeleteRemembered,
                    actionsEnabled = email.id !in state.activeActionEmailIds,
                    showDivider = showEmailDividers,
                    isHighlighted = email.id == highlightedEmailId,
                    onClearHighlight = onClearHighlight,
                    formattedTimeOverride = formattedTime,
                    modifier = Modifier.animateItem(
                        fadeInSpec = tween(durationMillis = 280),
                        placementSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        // Swipe dismissal already owns the horizontal exit.
                        // Retaining a second disappearing layer can replay a stale
                        // frame while the remaining rows are being repositioned.
                        fadeOutSpec = null
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

    InboxPaginationEffect(
        state = state,
        listState = listState,
        onLoadNextPage = onLoadNextPage
    )
}

@Composable
private fun InboxPaginationEffect(
    state: InboxUiState.Success,
    listState: LazyListState,
    onLoadNextPage: () -> Unit
) {
    LaunchedEffect(listState, state.visibleEmails.isEmpty()) {
        snapshotFlow {
            val layout = listState.layoutInfo
            val lastIndex = layout.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastIndex to layout.totalItemsCount
        }
            .distinctUntilChanged()
            .collect { (lastVisible, total) ->
                if (state.visibleEmails.isNotEmpty() && total > 0 && lastVisible >= total - 3) {
                    onLoadNextPage()
                }
            }
    }
}
