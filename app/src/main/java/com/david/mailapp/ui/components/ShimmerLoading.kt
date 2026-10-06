package com.david.mailapp.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shared email-list loading skeleton for the Inbox and Trash screens.
 *
 * Both screens draw their content *behind* a translucent glass top bar and
 * above the navigation bar, so the skeleton must be inset by the same amounts
 * as the real list — otherwise the first row renders under the bar (clipped)
 * and the list stops short of the bottom. [topPadding]/[bottomPadding] carry
 * those insets, and the row count is derived from the available height so the
 * skeleton fills the screen on any device instead of a fixed count.
 */
@Composable
fun ShimmerLoading(
    modifier: Modifier = Modifier,
    topPadding: Dp = 0.dp,
    bottomPadding: Dp = 0.dp
) {
    val shimmerBase = MaterialTheme.colorScheme.surfaceVariant
    val shimmerColors = listOf(
        shimmerBase.copy(alpha = 0.3f),
        shimmerBase.copy(alpha = 0.6f),
        shimmerBase.copy(alpha = 0.3f)
    )
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateX = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerX"
    )
    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateX.value - 200f, 0f),
        end = Offset(translateX.value + 200f, 0f)
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(top = topPadding, bottom = bottomPadding)
    ) {
        // Each row is ~64.dp tall (40.dp avatar + 12.dp vertical padding ×2)
        // plus the 4.dp spacer between rows.
        val rowCount = (maxHeight.value / ROW_HEIGHT_DP).toInt().coerceAtLeast(1)
        Column(modifier = Modifier.fillMaxSize()) {
            repeat(rowCount) {
                ShimmerRow(brush)
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

private const val ROW_HEIGHT_DP = 68f

@Composable
private fun ShimmerRow(brush: Brush) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
