package com.david.mailapp.feature.settings.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * A Material 3 [ListItem] tailored for the Settings hub, designed to live
 * inside a [SettingsCard].
 *
 * The trailing slot renders a navigation arrow by default. Pass
 * [trailingSwitch] = true for an inline [Switch] toggle instead.
 *
 * @param headline   Primary line of text.
 * @param supporting Optional secondary line shown below the headline.
 * @param icon       Leading icon — tinted with the current theme.
 * @param trailingSwitch  When true, renders a Switch (use [checked] + [onCheckedChange]).
 * @param checked    Switch state — only used when [trailingSwitch] is true.
 * @param onCheckedChange  Switch callback — only used when [trailingSwitch] is true.
 */
@Composable
fun SettingsListItem(
    headline: String,
    supporting: String? = null,
    icon: ImageVector? = null,
    trailingSwitch: Boolean = false,
    checked: Boolean = false,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = {
            Text(
                text = headline,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        supportingContent = supporting?.let { text ->
            {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        leadingContent = icon?.let { vector ->
            {
                Icon(
                    imageVector = vector,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        trailingContent = if (trailingSwitch) {
            {
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange
                )
            }
        } else {
            null
        },
        modifier = modifier
    )
}
