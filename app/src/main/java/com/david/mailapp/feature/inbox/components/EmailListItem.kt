package com.david.mailapp.feature.inbox.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.david.mailapp.domain.model.Email
import com.david.mailapp.domain.model.parseEmailSender
import com.david.mailapp.ui.theme.MotionTokens
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sign

private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

/**
 * Email row with custom physics-driven swipe gesture handler.
 *
 * Swipe end→start (left):  delete  — moveToTrash / deletePermanently
 * Swipe start→end (right): restore — only enabled when [onRestore] != null (Trash)
 *
 * Physics:
 * - Progressive resistance: dampedDelta = dragAmount × resistance curve
 * - Continuous spatial transforms via [graphicsLayer]: alpha, scale, rotation, shadow
 * - Release below 35% threshold → elastic return ([MotionTokens.swipeReturn])
 * - Release above 35% threshold → momentum exit ([MotionTokens.swipeDismiss])
 * - Single haptic tick at threshold crossing
 */
@Composable
fun EmailListItem(
    email: Email,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRestore: (() -> Unit)? = null,
    isHighlighted: Boolean = false,
    onClearHighlight: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val pressScale = remember { Animatable(1f) }

    val canSwipeLeft = true
    val canSwipeRight = onRestore != null

    // ── Swipe state ────────────────────────────────────────────
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val screenWidth = remember(density, config) {
        with(density) { config.screenWidthDp.dp.toPx() }
    }
    val threshold = remember(screenWidth) {
        screenWidth * MotionTokens.swipeThreshold
    }
    val offsetX = remember { Animatable(0f, Float.VectorConverter) }
    var isDismissed by remember { mutableStateOf(false) }
    var thresholdWasCrossed by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                val progress = (abs(offsetX.value) / screenWidth).coerceIn(0f, 1f)
                translationX = offsetX.value
                alpha = 1f - (progress * 0.6f).coerceAtMost(0.5f)
                scaleX = 1f - (progress * 0.03f)
                scaleY = 1f - (progress * 0.02f)
                shadowElevation = (progress * 8f).coerceAtMost(6f)
                rotationZ = -(offsetX.value / screenWidth) * 1.5f
            }
            .background(MaterialTheme.colorScheme.background)
            // ── Swipe gesture ──────────────────────────────────
            .pointerInput(email.id) {
                detectHorizontalDragGestures(
                    onDragStart = { },
                    onDragEnd = {
                        if (isDismissed) return@detectHorizontalDragGestures

                        scope.launch {
                            if (abs(offsetX.value) > threshold) {
                                // DISMISS — momentum exit
                                isDismissed = true
                                scope.launch {
                                    offsetX.animateTo(
                                        targetValue = sign(offsetX.value) * screenWidth * 1.2f,
                                        animationSpec = MotionTokens.swipeDismiss
                                    )
                                }
                                if (offsetX.value < 0) onDelete() else onRestore?.invoke()
                            } else {
                                // RETURN — rubber band overshoot
                                offsetX.animateTo(
                                    targetValue = 0f,
                                    animationSpec = MotionTokens.swipeReturn
                                )
                                thresholdWasCrossed = false
                            }
                        }
                    },
                    onDragCancel = {
                        scope.launch {
                            offsetX.animateTo(0f, MotionTokens.swipeReturn)
                            thresholdWasCrossed = false
                        }
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        scope.launch {
                            if (isDismissed) return@launch

                            // ── Resistance curve ────────────────
                            val current = offsetX.value
                            val resistanceFactor = 1f - (MotionTokens.swipeResistance *
                                    (abs(current) / screenWidth).coerceIn(0f, 1f))
                            val dampedDelta = dragAmount * resistanceFactor
                            val newOffset = current + dampedDelta

                            // Block unallowed direction
                            if (!canSwipeRight && newOffset > 0f) return@launch
                            if (!canSwipeLeft && newOffset < 0f) return@launch

                            offsetX.snapTo(newOffset)

                            // ── Haptic at threshold ─────────────
                            val crossed = abs(offsetX.value) > threshold
                            if (crossed && !thresholdWasCrossed) {
                                thresholdWasCrossed = true
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            } else if (!crossed) {
                                thresholdWasCrossed = false
                            }
                        }
                    }
                )
            }
            // ── Tap gesture ────────────────────────────────────
            .pointerInput(email.id) {
                detectTapGestures(
                    onPress = {
                        pressScale.snapTo(MotionTokens.pressScale)
                        val released = tryAwaitRelease()
                        if (released) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            pressScale.animateTo(1f, MotionTokens.itemPress)
                        }
                    },
                    onTap = { onClick() }
                )
            }
    ) {
        val formattedTime = remember(email.timestamp) {
            timeFormat.format(Date(email.timestamp))
        }
        val parsedSender = remember(email.from) { parseEmailSender(email.from) }

        // ── Tonal highlight background when returning from email detail ──
        val highlightAlpha = remember { Animatable(if (isHighlighted) 1f else 0f) }
        LaunchedEffect(isHighlighted) {
            if (isHighlighted) {
                highlightAlpha.snapTo(1f)
                try {
                    highlightAlpha.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(
                            durationMillis = MotionTokens.highlightDurationMs,
                            easing = FastOutSlowInEasing
                        )
                    )
                } finally {
                    onClearHighlight()
                }
            } else {
                highlightAlpha.snapTo(0f)
            }
        }
        if (highlightAlpha.value > 0f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        MaterialTheme.colorScheme.primaryContainer.copy(
                            alpha = 0.6f * highlightAlpha.value
                        )
                    )
            )
            Spacer(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(
                        MaterialTheme.colorScheme.primary.copy(
                            alpha = highlightAlpha.value
                        )
                    )
            )
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.Top
            ) {
                EmailAvatar(
                    initials = email.fromInitials,
                    isRead = email.isRead,
                    modifier = Modifier.scaleFrom(pressScale)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = parsedSender.displayCollapsed,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (email.isRead) FontWeight.Normal else FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = formattedTime,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (email.isRead) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = email.subject,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (email.isRead) FontWeight.Normal else FontWeight.Medium,
                            color = if (email.isRead) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (email.isStarred) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = "Starred",
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = email.snippet,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (email.isRead) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(start = 72.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }
    }
}

private fun Modifier.scaleFrom(animatable: Animatable<Float, *>) =
    this.scale(animatable.value)
