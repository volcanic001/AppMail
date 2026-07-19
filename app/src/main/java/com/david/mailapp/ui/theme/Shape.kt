package com.david.mailapp.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Material Expressive shape system.
 *
 * More rounded than default Material3 — the pill nav bar and expressive
 * cards require fully rounded corners and larger radius values.
 */
object ShapeTokens {
    /** Fully rounded pill — nav bar, FAB (collapsed), chips */
    val Pill = RoundedCornerShape(50)

    /** Avatar circle — sender icons */
    val Avatar = CircleShape

    /** Email card / list item surface */
    val Card = RoundedCornerShape(16.dp)

    /** Dialog, bottom sheet */
    val Dialog = RoundedCornerShape(28.dp)

    /** Small containers: search bar, input fields */
    val Small = RoundedCornerShape(12.dp)

    /** Tiny: badges, indicators */
    val Tiny = RoundedCornerShape(6.dp)
}

/**
 * Material3 Shapes instance wired to our tokens.
 * Used as the default shape system in MaterialTheme.
 */
val MailAppShapes = Shapes(
    extraSmall = ShapeTokens.Tiny,
    small = ShapeTokens.Small,
    medium = ShapeTokens.Card,
    large = ShapeTokens.Dialog,
    extraLarge = ShapeTokens.Pill
)
