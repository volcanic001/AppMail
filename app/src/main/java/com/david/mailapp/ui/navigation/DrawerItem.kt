package com.david.mailapp.ui.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import com.david.mailapp.ui.theme.MotionTokens
import kotlinx.coroutines.launch

/**
 * Individual drawer item with spring press animation and haptic feedback.
 */
@Composable
fun DrawerItem(
    screen: Screen,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val scale = remember { Animatable(1f) }
    val label = stringResource(screen.labelResId)

    NavigationDrawerItem(
        icon = {
            Icon(
                imageVector = if (isSelected) screen.filledIcon else screen.outlinedIcon,
                contentDescription = null
            )
        },
        label = { Text(label) },
        selected = isSelected,
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            scope.launch {
                scale.snapTo(MotionTokens.pressScale)
                scale.animateTo(1.02f, MotionTokens.iconTap)
                scale.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 500f))
            }
            onClick()
        },
        modifier = modifier.scale(scale.value),
        colors = NavigationDrawerItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
            selectedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}
