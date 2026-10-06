package com.david.mailapp.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext

/**
 * Holds the current theme configuration — consumed by screens that need
 * to know which palette is active (e.g. Settings palette picker).
 */
@Immutable
data class ThemeConfig(
    val darkTheme: Boolean,
    val palette: ColorPalette,
    val isDynamic: Boolean
)

val LocalThemeConfig = staticCompositionLocalOf {
    ThemeConfig(darkTheme = false, palette = ColorPalette.Blue, isDynamic = false)
}

/**
 * MailApp root theme.
 *
 * 1. If [useDynamicColor] is true AND the device supports it (Android 12+),
 *    use Material You dynamic colors from the wallpaper.
 * 2. Otherwise, generate light/dark schemes from [palette]'s seed color.
 * 3. Falls back to [DefaultLightColors]/[DefaultDarkColors] if the palette
 *    is [ColorPalette.Dynamic] but dynamic colors are unavailable.
 *
 * @param darkTheme  Whether to use dark theme. Default follows system.
 * @param palette    Which color palette to use. Default is Blue (Gmail blue).
 * @param useDynamicColor  Whether to try dynamic colors first. Default true.
 */
@Composable
fun MailAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    palette: ColorPalette = ColorPalette.Blue,
    useDynamicColor: Boolean = true,
    useCustomFont: Boolean = false,
    isAmoled: Boolean = false,
    content: @Composable () -> Unit
) {
    val isDynamic = palette == ColorPalette.Dynamic && useDynamicColor && supportsDynamicColor()

    val baseColorScheme = when {
        // 1. Dynamic colors (Android 12+ Monet) — only when user explicitly picks "Dynamic"
        isDynamic -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        // 2. Manual palette via seed color
        palette != ColorPalette.Dynamic -> {
            generateScheme(palette, darkTheme)
        }
        // 3. Fallback (palette is Dynamic but device doesn't support it)
        darkTheme -> DefaultDarkColors
        else -> DefaultLightColors
    }

    // AMOLED override: pure black background/surface in dark mode to save
    // battery on OLED panels. Has no effect in light mode.
    val colorScheme = if (isAmoled && darkTheme) {
        baseColorScheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceVariant = Color(0xFF0A0A0A),
            surfaceContainer = Color(0xFF0D0D0D),
            surfaceContainerLow = Color(0xFF080808),
            surfaceContainerHigh = Color(0xFF141414),
            surfaceContainerLowest = Color.Black,
            surfaceContainerHighest = Color(0xFF1A1A1A),
        )
    } else {
        baseColorScheme
    }

    val config = ThemeConfig(
        darkTheme = darkTheme,
        palette = palette,
        isDynamic = isDynamic
    )

    LaunchedEffect(palette, darkTheme, isDynamic, colorScheme) {
        ThemeDebug.logThemeEvaluation(
            palette = palette,
            isDark = darkTheme,
            isDynamic = isDynamic,
            colorScheme = colorScheme
        )
    }

    CompositionLocalProvider(LocalThemeConfig provides config) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = getMailAppTypography(useCustomFont),
            shapes = MailAppShapes,
            content = content
        )
    }
}

private fun supportsDynamicColor(): Boolean = Build.VERSION.SDK_INT >= 31

private fun generateScheme(palette: ColorPalette, dark: Boolean): ColorScheme {
    val base = when (palette) {
        ColorPalette.Blue -> if (dark) {
            darkColorScheme(
                primary = Color(0xFFA8C7FA),
                onPrimary = Color(0xFF002F5C),
                primaryContainer = Color(0xFF004B87),
                onPrimaryContainer = Color(0xFFD3E3FD),
                secondary = Color(0xFF7FCFFF),
                onSecondary = Color(0xFF003454),
                secondaryContainer = Color(0xFF004C74),
                onSecondaryContainer = Color(0xFFC2E7FF),
                background = Color(0xFF0F141C),
                onBackground = Color(0xFFE2E2E9),
                surface = Color(0xFF0F141C),
                onSurface = Color(0xFFE2E2E9),
                surfaceVariant = Color(0xFF22262E),
                onSurfaceVariant = Color(0xFFC3C6CF),
                surfaceContainer = Color(0xFF1B2028),
                surfaceContainerLow = Color(0xFF141920),
                surfaceContainerHigh = Color(0xFF252A32),
                outline = Color(0xFF8D9199)
            )
        } else {
            lightColorScheme(
                primary = Color(0xFF1A73E8),
                onPrimary = Color(0xFFFFFFFF),
                primaryContainer = Color(0xFFD3E3FD),
                onPrimaryContainer = Color(0xFF001C38),
                secondary = Color(0xFF00639B),
                onSecondary = Color(0xFFFFFFFF),
                secondaryContainer = Color(0xFFC2E7FF),
                onSecondaryContainer = Color(0xFF001E33),
                background = Color(0xFFF8F9FF),
                onBackground = Color(0xFF191C20),
                surface = Color(0xFFF8F9FF),
                onSurface = Color(0xFF191C20),
                surfaceVariant = Color(0xFFE0E2EC),
                onSurfaceVariant = Color(0xFF43474E),
                surfaceContainer = Color(0xFFF0F4FA),
                surfaceContainerLow = Color(0xFFF7FAFE),
                surfaceContainerHigh = Color(0xFFE9EEF5),
                outline = Color(0xFF73777F)
            )
        }
        
        ColorPalette.Green -> if (dark) {
            // Emerald/jade green — a cooler, more vivid green than the previous olive tone.
            darkColorScheme(
                primary = Color(0xFF5FDCA0),
                onPrimary = Color(0xFF003822),
                primaryContainer = Color(0xFF005134),
                onPrimaryContainer = Color(0xFF7BF9BB),
                secondary = Color(0xFFB3CCBB),
                onSecondary = Color(0xFF1E3528),
                secondaryContainer = Color(0xFF34493D),
                onSecondaryContainer = Color(0xFFCFE9D7),
                background = Color(0xFF0E150F),
                onBackground = Color(0xFFDFE4DE),
                surface = Color(0xFF0E150F),
                onSurface = Color(0xFFDFE4DE),
                surfaceVariant = Color(0xFF212722),
                onSurfaceVariant = Color(0xFFC0C9C0),
                surfaceContainer = Color(0xFF1A211B),
                surfaceContainerLow = Color(0xFF121911),
                surfaceContainerHigh = Color(0xFF252B24),
                outline = Color(0xFF8A938B)
            )
        } else {
            lightColorScheme(
                primary = Color(0xFF00875A),
                onPrimary = Color(0xFFFFFFFF),
                primaryContainer = Color(0xFFA4F4C9),
                onPrimaryContainer = Color(0xFF002115),
                secondary = Color(0xFF406653),
                onSecondary = Color(0xFFFFFFFF),
                secondaryContainer = Color(0xFFC2ECD2),
                onSecondaryContainer = Color(0xFF002115),
                background = Color(0xFFF5FBF7),
                onBackground = Color(0xFF171D19),
                surface = Color(0xFFF5FBF7),
                onSurface = Color(0xFF171D19),
                surfaceVariant = Color(0xFFDCE5DD),
                onSurfaceVariant = Color(0xFF404943),
                surfaceContainer = Color(0xFFE9F0EA),
                surfaceContainerLow = Color(0xFFEFF6F0),
                surfaceContainerHigh = Color(0xFFE3EAE4),
                outline = Color(0xFF707973)
            )
        }
        
        ColorPalette.Sepia -> if (dark) {
            darkColorScheme(
                primary = Color(0xFFE8C08A),
                onPrimary = Color(0xFF4A2800),
                primaryContainer = Color(0xFF693C04),
                onPrimaryContainer = Color(0xFFFFDDB8),
                secondary = Color(0xFFDCC2A4),
                onSecondary = Color(0xFF3E2D16),
                secondaryContainer = Color(0xFF57432B),
                onSecondaryContainer = Color(0xFFF9DEBE),
                background = Color(0xFF19120C),
                onBackground = Color(0xFFECE0D5),
                surface = Color(0xFF19120C),
                onSurface = Color(0xFFECE0D5),
                surfaceVariant = Color(0xFF2A2119),
                onSurfaceVariant = Color(0xFFD4C4B5),
                surfaceContainer = Color(0xFF241C15),
                surfaceContainerLow = Color(0xFF1F1710),
                surfaceContainerHigh = Color(0xFF2F261F),
                outline = Color(0xFF9C8E80)
            )
        } else {
            lightColorScheme(
                primary = Color(0xFF8C531B),
                onPrimary = Color(0xFFFFFFFF),
                primaryContainer = Color(0xFFF4ECD8),
                onPrimaryContainer = Color(0xFF2C1600),
                secondary = Color(0xFF705B41),
                onSecondary = Color(0xFFFFFFFF),
                secondaryContainer = Color(0xFFFCDEBC),
                onSecondaryContainer = Color(0xFF271904),
                background = Color(0xFFFBF7EE),
                onBackground = Color(0xFF201B14),
                surface = Color(0xFFFBF7EE),
                onSurface = Color(0xFF201B14),
                surfaceVariant = Color(0xFFEFE4D2),
                onSurfaceVariant = Color(0xFF4F4539),
                surfaceContainer = Color(0xFFF3EBDD),
                surfaceContainerLow = Color(0xFFF9F3E8),
                surfaceContainerHigh = Color(0xFFEDE3D3),
                outline = Color(0xFF817567)
            )
        }

        ColorPalette.Nord -> if (dark) {
            darkColorScheme(
                primary = Color(0xFF88C0D0),
                onPrimary = Color(0xFF2E3440),
                primaryContainer = Color(0xFF434C5E),
                onPrimaryContainer = Color(0xFFECEFF4),
                secondary = Color(0xFF81A1C1),
                onSecondary = Color(0xFF2E3440),
                secondaryContainer = Color(0xFF3B4252),
                onSecondaryContainer = Color(0xFFE5E9F0),
                background = Color(0xFF2E3440),
                onBackground = Color(0xFFECEFF4),
                surface = Color(0xFF2E3440),
                onSurface = Color(0xFFECEFF4),
                surfaceVariant = Color(0xFF3B4252),
                onSurfaceVariant = Color(0xFFD8DEE9),
                surfaceContainer = Color(0xFF3B4252),
                surfaceContainerLow = Color(0xFF353B49),
                surfaceContainerHigh = Color(0xFF434C5E),
                outline = Color(0xFFD8DEE9)
            )
        } else {
            lightColorScheme(
                primary = Color(0xFF5E81AC),
                onPrimary = Color(0xFFFFFFFF),
                primaryContainer = Color(0xFFD8DEE9),
                onPrimaryContainer = Color(0xFF2E3440),
                secondary = Color(0xFF81A1C1),
                onSecondary = Color(0xFFFFFFFF),
                secondaryContainer = Color(0xFFE5E9F0),
                onSecondaryContainer = Color(0xFF3B4252),
                background = Color(0xFFECEFF4),
                onBackground = Color(0xFF2E3440),
                surface = Color(0xFFECEFF4),
                onSurface = Color(0xFF2E3440),
                surfaceVariant = Color(0xFFE5E9F0),
                onSurfaceVariant = Color(0xFF434C5E),
                surfaceContainer = Color(0xFFE5E9F0),
                surfaceContainerLow = Color(0xFFEAEFF5),
                surfaceContainerHigh = Color(0xFFD8DEE9),
                outline = Color(0xFF4C566A)
            )
        }

        // Catppuccin — Macchiato flavor in dark mode, Latte flavor in light mode.
        // Accent is Mauve (Catppuccin's signature accent); the favourite star
        // uses colorScheme.primary, so it renders in Mauve to match the theme.
        ColorPalette.Macchiato -> if (dark) {
            darkColorScheme(
                primary = Color(0xFFC6A0F6),          // Mauve
                onPrimary = Color(0xFF1E2030),         // Mantle
                primaryContainer = Color(0xFF494D64),  // Surface1
                onPrimaryContainer = Color(0xFFCAD3F5),// Text
                secondary = Color(0xFFB7BDF8),         // Lavender
                onSecondary = Color(0xFF1E2030),       // Mantle
                secondaryContainer = Color(0xFF363A4F),// Surface0
                onSecondaryContainer = Color(0xFFCAD3F5),
                background = Color(0xFF24273A),         // Base
                onBackground = Color(0xFFCAD3F5),       // Text
                surface = Color(0xFF24273A),            // Base
                onSurface = Color(0xFFCAD3F5),          // Text
                surfaceVariant = Color(0xFF363A4F),     // Surface0
                onSurfaceVariant = Color(0xFFB8C0E0),   // Subtext1
                surfaceContainerLowest = Color(0xFF181926), // Crust
                surfaceContainer = Color(0xFF363A4F),   // Surface0
                surfaceContainerLow = Color(0xFF1E2030),// Mantle
                surfaceContainerHigh = Color(0xFF494D64),// Surface1
                outline = Color(0xFF8087A2),            // Overlay1
                error = Color(0xFFED8796),              // Red
                onError = Color(0xFF181926)
            )
        } else {
            lightColorScheme(
                primary = Color(0xFF8839EF),           // Latte Mauve
                onPrimary = Color(0xFFFFFFFF),
                primaryContainer = Color(0xFFCCD0DA),  // Surface0
                onPrimaryContainer = Color(0xFF4C4F69),// Text
                secondary = Color(0xFF7287FD),         // Latte Lavender
                onSecondary = Color(0xFFFFFFFF),
                secondaryContainer = Color(0xFFE6E9EF),// Mantle
                onSecondaryContainer = Color(0xFF4C4F69),
                background = Color(0xFFEFF1F5),         // Base
                onBackground = Color(0xFF4C4F69),       // Text
                surface = Color(0xFFEFF1F5),            // Base
                onSurface = Color(0xFF4C4F69),          // Text
                surfaceVariant = Color(0xFFCCD0DA),     // Surface0
                onSurfaceVariant = Color(0xFF5C5F77),   // Subtext1
                surfaceContainerLowest = Color(0xFFFFFFFF),
                surfaceContainer = Color(0xFFDCE0E8),   // Crust
                surfaceContainerLow = Color(0xFFE6E9EF),// Mantle
                surfaceContainerHigh = Color(0xFFCCD0DA),// Surface0
                outline = Color(0xFF8C8FA1),            // Overlay1
                error = Color(0xFFD20F39),              // Latte Red
                onError = Color(0xFFFFFFFF)
            )
        }
        
        ColorPalette.Teal -> if (dark) {
            darkColorScheme(
                primary = Color(0xFF4FD8EB),
                onPrimary = Color(0xFF00363D),
                primaryContainer = Color(0xFF004F58),
                onPrimaryContainer = Color(0xFF97F0FF),
                secondary = Color(0xFF83D3E3),
                onSecondary = Color(0xFF00363F),
                secondaryContainer = Color(0xFF004F5A),
                onSecondaryContainer = Color(0xFFA6EEFF),
                background = Color(0xFF0E1415),
                onBackground = Color(0xFFE0E3E3),
                surface = Color(0xFF0E1415),
                onSurface = Color(0xFFE0E3E3),
                surfaceVariant = Color(0xFF202728),
                onSurfaceVariant = Color(0xFFC3C6CF),
                surfaceContainer = Color(0xFF1B2122),
                surfaceContainerLow = Color(0xFF13191A),
                surfaceContainerHigh = Color(0xFF252B2D),
                outline = Color(0xFF8D9199)
            )
        } else {
            lightColorScheme(
                primary = Color(0xFF006874),
                onPrimary = Color(0xFFFFFFFF),
                primaryContainer = Color(0xFF97F0FF),
                onPrimaryContainer = Color(0xFF002024),
                secondary = Color(0xFF006876),
                onSecondary = Color(0xFFFFFFFF),
                secondaryContainer = Color(0xFFA6EEFF),
                onSecondaryContainer = Color(0xFF001F25),
                background = Color(0xFFFAFDFD),
                onBackground = Color(0xFF191C20),
                surface = Color(0xFFFAFDFD),
                onSurface = Color(0xFF191C20),
                surfaceVariant = Color(0xFFE0E2EC),
                onSurfaceVariant = Color(0xFF43474E),
                surfaceContainer = Color(0xFFEFF5F6),
                surfaceContainerLow = Color(0xFFF7FAFA),
                surfaceContainerHigh = Color(0xFFE7ECEE),
                outline = Color(0xFF73777F)
            )
        }
        
        ColorPalette.Yellow -> if (dark) {
            darkColorScheme(
                primary = Color(0xFFD9C600),
                onPrimary = Color(0xFF373100),
                primaryContainer = Color(0xFF4F4700),
                onPrimaryContainer = Color(0xFFF9E75E),
                secondary = Color(0xFFFABD00),
                onSecondary = Color(0xFF3F2E00),
                secondaryContainer = Color(0xFF594100),
                onSecondaryContainer = Color(0xFFFFDF9E),
                background = Color(0xFF15140E),
                onBackground = Color(0xFFE5E2D9),
                surface = Color(0xFF15140E),
                onSurface = Color(0xFFE5E2D9),
                surfaceVariant = Color(0xFF25241D),
                onSurfaceVariant = Color(0xFFC7C6B5),
                surfaceContainer = Color(0xFF21201A),
                surfaceContainerLow = Color(0xFF181813),
                surfaceContainerHigh = Color(0xFF2C2A23),
                outline = Color(0xFF919181)
            )
        } else {
            lightColorScheme(
                primary = Color(0xFF695F00),
                onPrimary = Color(0xFFFFFFFF),
                primaryContainer = Color(0xFFF9E75E),
                onPrimaryContainer = Color(0xFF201C00),
                secondary = Color(0xFF785A00),
                onSecondary = Color(0xFFFFFFFF),
                secondaryContainer = Color(0xFFFFDF9E),
                onSecondaryContainer = Color(0xFF261A00),
                background = Color(0xFFFDFDF5),
                onBackground = Color(0xFF1C1C17),
                surface = Color(0xFFFDFDF5),
                onSurface = Color(0xFF1C1C17),
                surfaceVariant = Color(0xFFE5E2D5),
                onSurfaceVariant = Color(0xFF48473E),
                surfaceContainer = Color(0xFFF3F3E8),
                surfaceContainerLow = Color(0xFFFAFBF0),
                surfaceContainerHigh = Color(0xFFEBECE0),
                outline = Color(0xFF78786B)
            )
        }
        
        ColorPalette.Monochrome -> if (dark) {
            darkColorScheme(
                primary = Color(0xFFC6C6C6),
                onPrimary = Color(0xFF303030),
                primaryContainer = Color(0xFF474747),
                onPrimaryContainer = Color(0xFFE2E2E2),
                secondary = Color(0xFFC9C5CA),
                onSecondary = Color(0xFF313033),
                secondaryContainer = Color(0xFF484649),
                onSecondaryContainer = Color(0xFFE6E1E5),
                background = Color(0xFF131313),
                onBackground = Color(0xFFE2E2E2),
                surface = Color(0xFF131313),
                onSurface = Color(0xFFE2E2E2),
                surfaceVariant = Color(0xFF222222),
                onSurfaceVariant = Color(0xFFC6C6C6),
                surfaceContainer = Color(0xFF1E1E1E),
                surfaceContainerLow = Color(0xFF181818),
                surfaceContainerHigh = Color(0xFF282828),
                outline = Color(0xFF8D8D8D)
            )
        } else {
            lightColorScheme(
                primary = Color(0xFF5E5E5E),
                onPrimary = Color(0xFFFFFFFF),
                primaryContainer = Color(0xFFE2E2E2),
                onPrimaryContainer = Color(0xFF1B1B1B),
                secondary = Color(0xFF605D62),
                onSecondary = Color(0xFFFFFFFF),
                secondaryContainer = Color(0xFFE6E1E5),
                onSecondaryContainer = Color(0xFF1D1B1E),
                background = Color(0xFFF9F9F9),
                onBackground = Color(0xFF1B1B1B),
                surface = Color(0xFFF9F9F9),
                onSurface = Color(0xFF1B1B1B),
                surfaceVariant = Color(0xFFE2E2E2),
                onSurfaceVariant = Color(0xFF474747),
                surfaceContainer = Color(0xFFF0F0F0),
                surfaceContainerLow = Color(0xFFF7F7F7),
                surfaceContainerHigh = Color(0xFFEAEAEA),
                outline = Color(0xFF777777)
            )
        }
        
        ColorPalette.Dynamic -> if (dark) DefaultDarkColors else DefaultLightColors
    }

    // The manual palettes above don't define outlineVariant (dividers) or
    // surfaceContainerHighest, so they would fall back to the Material 3 default
    // neutral — a purple-tinted grey that clashes with the warm palettes. Derive
    // both from each palette's own neutrals so they always match the active theme.
    return base.copy(
        outlineVariant = lerp(base.surfaceVariant, base.outline, 0.35f),
        surfaceContainerHighest = lerp(base.surfaceContainerHigh, base.onSurface, 0.05f),
    )
}
