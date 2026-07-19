package com.david.mailapp.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Material 3 Expressive Loading Indicator (#1 in M3 specs)
 * An organic 7-vertex rounded polygon / blob that morphs and rotates continuously.
 */
@Composable
fun ExpressiveLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    progress: Float? = null
) {
    val transition = rememberInfiniteTransition(label = "ExpressiveIndicator")
    
    val animatedRotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Rotation"
    )

    val animatedMorph by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Morph"
    )

    val currentRotation = if (progress != null) (progress * 360f * 1.5f) else animatedRotation
    val currentMorph = if (progress != null) (progress * PI.toFloat() * 2f) else animatedMorph

    Canvas(modifier = modifier.size(size)) {
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val maxRadius = this.size.minDimension / 2f * 0.92f
        val rotationRad = Math.toRadians(currentRotation.toDouble()).toFloat()

        val path = Path()
        val steps = 72
        val n = 7 // 7-vertex rounded star/blob

        for (i in 0..steps) {
            val t = (i * 2 * PI / steps).toFloat()
            // Organic morphing amplitude and shape
            val amp = 0.15f + 0.06f * sin(currentMorph)
            val r = maxRadius * (0.80f + amp * cos(n * t + currentMorph * 0.5f))
            
            val x = center.x + r * cos(t + rotationRad)
            val y = center.y + r * sin(t + rotationRad)

            if (i == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        path.close()

        drawPath(
            path = path,
            color = color
        )
    }
}

/**
 * Material 3 Expressive Contained Loading Indicator (#2 in M3 specs)
 * The organic morphing polygon placed inside a circular container.
 */
@Composable
fun ContainedLoadingIndicator(
    modifier: Modifier = Modifier,
    containerSize: Dp = 48.dp,
    indicatorSize: Dp = 32.dp,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    indicatorColor: Color = MaterialTheme.colorScheme.primary,
    progress: Float? = null
) {
    Box(
        modifier = modifier
            .size(containerSize)
            .clip(CircleShape)
            .background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        ExpressiveLoadingIndicator(
            size = indicatorSize,
            color = indicatorColor,
            progress = progress
        )
    }
}
