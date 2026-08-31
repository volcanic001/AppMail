package com.david.mailapp.feature.inbox

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.david.mailapp.ui.components.ContainedLoadingIndicator

/**
 * Success content container managing pull-to-refresh state and custom indicator.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InboxSuccessContent(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val ptrState = rememberPullToRefreshState()
    PullToRefreshBox(
        state = ptrState,
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        indicator = {
            val isVisible = isRefreshing || ptrState.distanceFraction > 0f
            if (isVisible) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .testTag("inbox_refresh_indicator")
                        .graphicsLayer {
                            val fraction = ptrState.distanceFraction.coerceIn(0f, 1.5f)
                            translationY = if (isRefreshing) 24.dp.toPx() else fraction * 40.dp.toPx()
                            val scale = if (isRefreshing) 1f else (fraction * 1.2f).coerceIn(0f, 1f)
                            scaleX = scale
                            scaleY = scale
                            alpha = if (isRefreshing) 1f else fraction.coerceIn(0f, 1f)
                        }
                ) {
                    ContainedLoadingIndicator(
                        containerSize = 48.dp,
                        indicatorSize = 32.dp,
                        progress = if (isRefreshing) null else ptrState.distanceFraction.coerceIn(0f, 1f)
                    )
                }
            }
        }
    ) {
        content()
    }
}
