package com.david.mailapp.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
    val isIndeterminate = progress == null

    Crossfade(
        targetState = isIndeterminate,
        modifier = modifier.size(size),
        animationSpec = tween(durationMillis = 150),
        label = "ExpressiveIndicatorMode"
    ) { indeterminate ->
        if (indeterminate) {
            IndeterminateExpressiveLoadingIndicator(color = color)
        } else {
            ExpressiveLoadingIndicatorShape(
                color = color,
                progress = (progress ?: 1f).coerceIn(0f, 1f)
            )
        }
    }
}

@Composable
private fun IndeterminateExpressiveLoadingIndicator(color: Color) {
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

    ExpressiveLoadingIndicatorShape(
        color = color,
        rotationDegrees = animatedRotation,
        morphPhase = animatedMorph
    )
}

@Composable
private fun ExpressiveLoadingIndicatorShape(
    color: Color,
    progress: Float
) {
    ExpressiveLoadingIndicatorShape(
        color = color,
        rotationDegrees = progress * 360f,
        morphPhase = progress * EXPRESSIVE_MORPH_CYCLE
    )
}

@Composable
private fun ExpressiveLoadingIndicatorShape(
    color: Color,
    rotationDegrees: Float,
    morphPhase: Float
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val maxRadius = this.size.minDimension / 2f * 0.92f
        val rotationRad = Math.toRadians(rotationDegrees.toDouble()).toFloat()

        val path = Path()
        val steps = 72

        for (i in 0..steps) {
            val t = (i * 2 * PI / steps).toFloat()
            val r = maxRadius * expressiveRadiusFactor(t, morphPhase)

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

internal const val EXPRESSIVE_MORPH_CYCLE = (2 * PI).toFloat()

/**
 * Normalized radius of the organic seven-vertex shape.
 *
 * Both terms complete a full period at [EXPRESSIVE_MORPH_CYCLE], so the last
 * frame of an animation cycle has exactly the same geometry as its first frame.
 */
internal fun expressiveRadiusFactor(angle: Float, morphPhase: Float): Float {
    val amplitude = 0.15f + 0.06f * sin(morphPhase)
    return 0.80f + amplitude * cos(7 * angle + morphPhase)
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
