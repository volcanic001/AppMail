package com.david.mailapp.feature.search.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.david.mailapp.R
import com.david.mailapp.domain.model.Email
import com.david.mailapp.feature.inbox.components.EmailAvatar
import com.david.mailapp.ui.theme.MotionTokens
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date

/**
 * Search result row with query highlighting and staggered entrance.
 *
 * Unlike [EmailListItem], this has:
 * - Highlighted subject + snippet (matching query text)
 * - No swipe-to-delete (search results are read-only)
 * - Staggered animation: alpha 0→1 + scale 0.94→1.0 with delay
 */
@Composable
fun SearchResultItem(
    email: Email,
    query: String,
    delayMs: Int,
    onClick: () -> Unit,
    isHighlighted: Boolean = false,
    onClearHighlight: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = MotionTokens.resultStagger,
        label = "resultAlpha"
    )
    val itemScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.94f,
        animationSpec = MotionTokens.resultStagger,
        label = "resultScale"
    )

    val timePattern = stringResource(R.string.date_pattern_time)
    val locale = LocalLocale.current.platformLocale
    val timeFormat = remember(timePattern, locale) {
        SimpleDateFormat(timePattern, locale)
    }

    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        visible = true
    }

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

    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .alpha(alpha)
            .scale(itemScale)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.Top
            ) {
            EmailAvatar(
                initials = email.fromInitials,
                isRead = email.isRead
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                // Sender + timestamp
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = email.from.ifEmpty { stringResource(R.string.fallback_unknown_sender) },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (email.isRead) FontWeight.Normal else FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = timeFormat.format(Date(email.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (email.isRead) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(2.dp))
                val displaySubject = email.subject.ifEmpty { stringResource(R.string.fallback_no_subject) }
                // Subject with highlight
                Text(
                    text = highlightQuery(displaySubject, query, email.isRead),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (email.isRead) FontWeight.Normal else FontWeight.Medium,
                    color = if (email.isRead) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                // Snippet with highlight
                Text(
                    text = highlightQuery(email.snippet, query, email.isRead),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (email.isRead) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
    HorizontalDivider(
        modifier = Modifier.padding(start = 72.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant
    )
}
}

/**
 * Builds an [androidx.compose.ui.text.AnnotatedString] where all
 * case-insensitive occurrences of [query] are highlighted with
 * primary color and semi-bold weight.
 */
@Composable
private fun highlightQuery(text: String, query: String, isRead: Boolean) = buildAnnotatedString {
    if (query.isBlank()) {
        append(text)
        return@buildAnnotatedString
    }

    val locale = LocalLocale.current.platformLocale
    var current = 0
    val lowerText = text.lowercase(locale)
    val lowerQuery = query.lowercase(locale)

    while (current < text.length) {
        val index = lowerText.indexOf(lowerQuery, current)
        if (index == -1) {
            append(text.substring(current))
            break
        }
        // Text before match
        if (index > current) {
            append(text.substring(current, index))
        }
        // Highlighted match
        withStyle(
            SpanStyle(
                color = if (isRead) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        ) {
            append(text.substring(index, index + query.length))
        }
        current = index + query.length
    }
}
