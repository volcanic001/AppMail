package com.david.mailapp.feature.emaildetail.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.david.mailapp.R
import com.david.mailapp.domain.model.Email
import com.david.mailapp.feature.emaildetail.EmailRenderTrace
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
// FloatingHeaderPanel
// An overlay panel anchored to the top of the screen.
// Collapsed: shows only a pill handle with a rotating arrow.
// Expanded: shows a Card with email metadata fields.
// Never pushes the WebView — it lives in a Box overlay at zIndex(2).
// ─────────────────────────────────────────────────────────────────────────────
@Composable
internal fun FloatingHeaderPanel(
    email: Email,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    traceMail: String,
    modifier: Modifier = Modifier
) {
    val lastHeaderLayout = remember { mutableStateOf<String?>(null) }

    // Arrow rotation: 0° = collapsed (points up = "tap to expand"),
    // 180° = expanded (points down = "tap to collapse").
    val arrowRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "header_arrow_rotation"
    )

    // Offset the pill handle dynamically to ensure it is centered on the border when expanded
    // and correctly aligned below the TopAppBar when collapsed (preventing cut-off).
    val handleOffsetY by animateDpAsState(
        targetValue = if (isExpanded) (-20).dp else (-15).dp,
        animationSpec = tween(durationMillis = 250),
        label = "handle_offset_y"
    )

    val dateFormat = rememberDateFormat()
    val panelShape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .offset(y = (-5).dp) // Tucks the panel slightly under the TopAppBar to eliminate any gap
            .onGloballyPositioned { coordinates ->
                val position = coordinates.positionInRoot()
                val snapshot =
                    "x=${position.x.roundToInt()} y=${position.y.roundToInt()} " +
                        "width=${coordinates.size.width} height=${coordinates.size.height} " +
                        "expanded=$isExpanded"
                if (lastHeaderLayout.value != snapshot) {
                    lastHeaderLayout.value = snapshot
                    EmailRenderTrace.d(traceMail, "UI", "HEADER_LAYOUT", snapshot)
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Expandable panel ───────────────────────────────────
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(
                animationSpec = tween(durationMillis = 250),
                expandFrom = Alignment.Top
            ) + fadeIn(animationSpec = tween(durationMillis = 200)),
            exit = shrinkVertically(
                animationSpec = tween(durationMillis = 220),
                shrinkTowards = Alignment.Top
            ) + fadeOut(animationSpec = tween(durationMillis = 180))
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(),
                shape = panelShape,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                ) {
                    // Subject — prominent
                    Text(
                        text = email.subject.ifBlank { stringResource(R.string.detail_subject_missing) },
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.height(12.dp))

                    // Metadata rows
                    HeaderDetailRow(
                        icon = Icons.Outlined.Mail,
                        label = stringResource(R.string.detail_field_from_label),
                        value = email.from
                    )
                    if (email.to.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        HeaderDetailRow(
                            icon = Icons.Outlined.Person,
                            label = stringResource(R.string.detail_field_to_label),
                            value = email.to
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    HeaderDetailRow(
                        icon = Icons.Outlined.CalendarToday,
                        label = stringResource(R.string.detail_field_date_label),
                        value = dateFormat.format(Date(email.timestamp))
                    )
                }
            }
        }

        // ── Tab handle (always visible, centered, attached to bottom) ─────────────
        Surface(
            onClick = onToggle,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 4.dp,
            modifier = Modifier
                .zIndex(1f)
                .width(60.dp)
                .height(40.dp)
                .offset(y = handleOffsetY) // Dynamic offset to align handle perfectly
        ) {
            Box(
                contentAlignment = Alignment.BottomCenter,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 2.dp) // Centers the 16.dp icon perfectly within the visible bottom 20.dp semi-circle
            ) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowUp,
                    contentDescription = if (isExpanded) stringResource(R.string.detail_collapse_header) else stringResource(R.string.detail_expand_header),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(18.dp)
                        .rotate(arrowRotation)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// HeaderDetailRow
// A label + value row used inside FloatingHeaderPanel.
// Label has a fixed minimum width so all values align cleanly.
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun HeaderDetailRow(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Circle container for icon
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(32.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(44.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun rememberDateFormat(): SimpleDateFormat {
    val pattern = stringResource(R.string.date_pattern_long)
    return remember(pattern) {
        SimpleDateFormat(pattern, Locale.getDefault())
    }
}
