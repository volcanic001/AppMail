package com.david.mailapp.feature.settings.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp

enum class SettingsCardPosition {
    First,
    Middle,
    Last,
    Single
}

/**
 * Expressive MD3 card for the Settings hub — supports asymmetric corner radii
 * when grouped as a composite block, with a spring-driven press micro-animation.
 *
 * When [onClick] is provided the entire card is clickable with native
 * M3 ripple; press scales down to 0.985× and springs back on release.
 * Without [onClick] the card acts as a pure visual container.
 */
@Composable
fun SettingsCard(
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    position: SettingsCardPosition = SettingsCardPosition.Single,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.985f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "settingsCardPress"
    )

    val shape = remember(position) {
        when (position) {
            SettingsCardPosition.First -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
            SettingsCardPosition.Middle -> RoundedCornerShape(4.dp)
            SettingsCardPosition.Last -> RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
            SettingsCardPosition.Single -> RoundedCornerShape(24.dp)
        }
    }

    Card(
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = ripple(),
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            )
    ) {
        content()
    }
}

