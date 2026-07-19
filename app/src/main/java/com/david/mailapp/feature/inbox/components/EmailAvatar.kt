package com.david.mailapp.feature.inbox.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.david.mailapp.ui.theme.MotionTokens
import com.david.mailapp.ui.theme.ShapeTokens

/**
 * Circular avatar with the sender's initials.
 *
 * Colors change based on read/unread state:
 * - Unread → primaryContainer background + blue dot indicator
 * - Read   → secondaryContainer background (muted)
 *
 * The color transition is animated so the shift from bold → muted
 * is smooth when marking as read.
 */
@Composable
fun EmailAvatar(
    initials: String,
    isRead: Boolean,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue = if (isRead) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        animationSpec = tween(MotionTokens.micro),
        label = "avatarBg"
    )

    val textColor by animateColorAsState(
        targetValue = if (isRead) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        },
        animationSpec = tween(MotionTokens.micro),
        label = "avatarText"
    )

    Box(modifier = modifier.size(40.dp)) {
        // Background circle
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(ShapeTokens.Avatar)
                .background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials.uppercase(),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = textColor
            )
        }

        // Unread dot indicator
        if (!isRead) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .align(Alignment.TopEnd)
                    .padding(0.dp)
            )
        }
    }
}
