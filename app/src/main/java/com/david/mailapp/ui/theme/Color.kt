package com.david.mailapp.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Color palette definitions for the manual palette picker (Settings screen).
 *
 * Each palette has a seed color. When dynamic color (Monet) is unavailable
 * or the user explicitly picks a palette, we generate light + dark schemes
 * from the seed using Material3's tonal palette algorithm.
 *
 * Dynamic = use the device wallpaper colors (Android 12+).
 */
enum class ColorPalette(
    val displayName: String,
    val seedColor: Color,
    val previewTop: Color,
    val previewBottomLeft: Color,
    val previewBottomRight: Color
) {
    Dynamic("Dinámico", Color.Unspecified,
        Color(0xFFD3E3FD), Color(0xFF6750A4), Color(0xFF386A20)),
    Blue("Azul", Color(0xFF1A73E8),
        Color(0xFFD3E3FD), Color(0xFF004583), Color(0xFF1A73E8)),
    Green("Verde", Color(0xFF386A20),
        Color(0xFFC2E7C9), Color(0xFF0F5223), Color(0xFF386A20)),
    Purple("Morado", Color(0xFF6750A4),
        Color(0xFFE8DEF8), Color(0xFF4A4458), Color(0xFF6750A4)),
    Orange("Naranja", Color(0xFF9C4145),
        Color(0xFFFFDAD9), Color(0xFF7E5260), Color(0xFF9C4145)),
    Pink("Rosa", Color(0xFFB3261E),
        Color(0xFFFFDAD6), Color(0xFF855355), Color(0xFFB3261E)),
    Teal("Cian", Color(0xFF006874),
        Color(0xFF97F0FF), Color(0xFF004F58), Color(0xFF006874)),
    Yellow("Amarillo", Color(0xFF695F00),
        Color(0xFFEBE295), Color(0xFF4E4400), Color(0xFF695F00)),
    Monochrome("Neutro", Color(0xFF5E5E5E),
        Color(0xFFE2E2E2), Color(0xFF474747), Color(0xFF5E5E5E));
}

/**
 * Fallback color schemes when dynamic color is unavailable and no palette is selected.
 * These are used only as a last resort — in practice the palette picker always has a value.
 */
internal val DefaultLightColors = lightColorScheme(
    primary = Color(0xFF1A73E8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3E3FD),
    onPrimaryContainer = Color(0xFF001D36),
    secondary = Color(0xFF565F71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDAE2F9),
    onSecondaryContainer = Color(0xFF131C2B),
    tertiary = Color(0xFF705575),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFAD8FD),
    onTertiaryContainer = Color(0xFF28132F),
    background = Color(0xFFF8F9FF),
    onBackground = Color(0xFF191C20),
    surface = Color(0xFFF8F9FF),
    onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFE0E2EC),
    onSurfaceVariant = Color(0xFF43474E),
    outline = Color(0xFF73777F),
    outlineVariant = Color(0xFFC3C6CF),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
)

internal val DefaultDarkColors = darkColorScheme(
    primary = Color(0xFFA8C7FA),
    onPrimary = Color(0xFF002F5C),
    primaryContainer = Color(0xFF004583),
    onPrimaryContainer = Color(0xFFD3E3FD),
    secondary = Color(0xFFBEC6DC),
    onSecondary = Color(0xFF283041),
    secondaryContainer = Color(0xFF3E4759),
    onSecondaryContainer = Color(0xFFDAE2F9),
    tertiary = Color(0xFFDDBCE1),
    onTertiary = Color(0xFF3F2845),
    tertiaryContainer = Color(0xFF573E5C),
    onTertiaryContainer = Color(0xFFFAD8FD),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF43474E),
    onSurfaceVariant = Color(0xFFC3C6CF),
    outline = Color(0xFF8D9199),
    outlineVariant = Color(0xFF43474E),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)
