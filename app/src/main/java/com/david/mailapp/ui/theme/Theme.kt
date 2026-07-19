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
    return when (palette) {
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
            darkColorScheme(
                primary = Color(0xFF9CD592),
                onPrimary = Color(0xFF0F3905),
                primaryContainer = Color(0xFF1D520B),
                onPrimaryContainer = Color(0xFFC2E7C9),
                secondary = Color(0xFF8FD99C),
                onSecondary = Color(0xFF003914),
                secondaryContainer = Color(0xFF005322),
                onSecondaryContainer = Color(0xFFD7F0DB),
                background = Color(0xFF0E150F),
                onBackground = Color(0xFFE2E2E9),
                surface = Color(0xFF0E150F),
                onSurface = Color(0xFFE2E2E9),
                surfaceVariant = Color(0xFF212722),
                onSurfaceVariant = Color(0xFFC3C6CF),
                surfaceContainer = Color(0xFF1B221B),
                surfaceContainerLow = Color(0xFF131A13),
                surfaceContainerHigh = Color(0xFF252B24),
                outline = Color(0xFF8D9199)
            )
        } else {
            lightColorScheme(
                primary = Color(0xFF386A20),
                onPrimary = Color(0xFFFFFFFF),
                primaryContainer = Color(0xFFC2E7C9),
                onPrimaryContainer = Color(0xFF002204),
                secondary = Color(0xFF1E8E3E),
                onSecondary = Color(0xFFFFFFFF),
                secondaryContainer = Color(0xFFD7F0DB),
                onSecondaryContainer = Color(0xFF002208),
                background = Color(0xFFF6FBF7),
                onBackground = Color(0xFF191C20),
                surface = Color(0xFFF6FBF7),
                onSurface = Color(0xFF191C20),
                surfaceVariant = Color(0xFFE0E2EC),
                onSurfaceVariant = Color(0xFF43474E),
                surfaceContainer = Color(0xFFEEF5EF),
                surfaceContainerLow = Color(0xFFF6FAF6),
                surfaceContainerHigh = Color(0xFFE7ECE7),
                outline = Color(0xFF73777F)
            )
        }
        
        ColorPalette.Purple -> if (dark) {
            darkColorScheme(
                primary = Color(0xFFD0BCFF),
                onPrimary = Color(0xFF381E72),
                primaryContainer = Color(0xFF4F378B),
                onPrimaryContainer = Color(0xFFE8DEF8),
                secondary = Color(0xFFCCC2DC),
                onSecondary = Color(0xFF332D41),
                secondaryContainer = Color(0xFF4A4458),
                onSecondaryContainer = Color(0xFFE8DEF8),
                background = Color(0xFF141218),
                onBackground = Color(0xFFE6E1E9),
                surface = Color(0xFF141218),
                onSurface = Color(0xFFE6E1E9),
                surfaceVariant = Color(0xFF49454F),
                onSurfaceVariant = Color(0xFFCAC4D0),
                surfaceContainer = Color(0xFF211F26),
                surfaceContainerLow = Color(0xFF1D1B22),
                surfaceContainerHigh = Color(0xFF2B2930),
                outline = Color(0xFF938F99)
            )
        } else {
            lightColorScheme(
                primary = Color(0xFF6750A4),
                onPrimary = Color(0xFFFFFFFF),
                primaryContainer = Color(0xFFE8DEF8),
                onPrimaryContainer = Color(0xFF21005D),
                secondary = Color(0xFF625B71),
                onSecondary = Color(0xFFFFFFFF),
                secondaryContainer = Color(0xFFE8DEF8),
                onSecondaryContainer = Color(0xFF1D192B),
                background = Color(0xFFFEF7FF),
                onBackground = Color(0xFF1D1B20),
                surface = Color(0xFFFEF7FF),
                onSurface = Color(0xFF1D1B20),
                surfaceVariant = Color(0xFFE7E0EC),
                onSurfaceVariant = Color(0xFF49454F),
                surfaceContainer = Color(0xFFF3EDF7),
                surfaceContainerLow = Color(0xFFF7F2FA),
                surfaceContainerHigh = Color(0xFFECE6F0),
                outline = Color(0xFF79747E)
            )
        }
        
        ColorPalette.Orange -> if (dark) {
            darkColorScheme(
                primary = Color(0xFFFFB3B4),
                onPrimary = Color(0xFF5F121B),
                primaryContainer = Color(0xFF7E2A2E),
                onPrimaryContainer = Color(0xFFFFDAD9),
                secondary = Color(0xFFFFB4A9),
                onSecondary = Color(0xFF581E15),
                secondaryContainer = Color(0xFF871F13),
                onSecondaryContainer = Color(0xFFFFDAD5),
                background = Color(0xFF1C1112),
                onBackground = Color(0xFFF1DFDF),
                surface = Color(0xFF1C1112),
                onSurface = Color(0xFFF1DFDF),
                surfaceVariant = Color(0xFF2A1C1D),
                onSurfaceVariant = Color(0xFFCAC4D0),
                surfaceContainer = Color(0xFF26191A),
                surfaceContainerLow = Color(0xFF211516),
                surfaceContainerHigh = Color(0xFF322324),
                outline = Color(0xFF938F99)
            )
        } else {
            lightColorScheme(
                primary = Color(0xFF9C4145),
                onPrimary = Color(0xFFFFFFFF),
                primaryContainer = Color(0xFFFFDAD9),
                onPrimaryContainer = Color(0xFF41000A),
                secondary = Color(0xFFA83828),
                onSecondary = Color(0xFFFFFFFF),
                secondaryContainer = Color(0xFFFFDAD5),
                onSecondaryContainer = Color(0xFF410002),
                background = Color(0xFFFFF8F8),
                onBackground = Color(0xFF251A1A),
                surface = Color(0xFFFFF8F8),
                onSurface = Color(0xFF251A1A),
                surfaceVariant = Color(0xFFF4DDDD),
                onSurfaceVariant = Color(0xFF524343),
                surfaceContainer = Color(0xFFFCEAE9),
                surfaceContainerLow = Color(0xFFFFF1F0),
                surfaceContainerHigh = Color(0xFFF6E4E3),
                outline = Color(0xFF857373)
            )
        }
        
        ColorPalette.Pink -> if (dark) {
            darkColorScheme(
                primary = Color(0xFFF2B8B5),
                onPrimary = Color(0xFF601410),
                primaryContainer = Color(0xFF8C1D18),
                onPrimaryContainer = Color(0xFFF9DEDC),
                secondary = Color(0xFFFFB3B5),
                onSecondary = Color(0xFF5F1520),
                secondaryContainer = Color(0xFF682529),
                onSecondaryContainer = Color(0xFFFFDAD6),
                background = Color(0xFF1C1111),
                onBackground = Color(0xFFF2DFDE),
                surface = Color(0xFF1C1111),
                onSurface = Color(0xFFF2DFDE),
                surfaceVariant = Color(0xFF291C1C),
                onSurfaceVariant = Color(0xFFCAC4D0),
                surfaceContainer = Color(0xFF251919),
                surfaceContainerLow = Color(0xFF201515),
                surfaceContainerHigh = Color(0xFF312222),
                outline = Color(0xFF938F99)
            )
        } else {
            lightColorScheme(
                primary = Color(0xFFB3261E),
                onPrimary = Color(0xFFFFFFFF),
                primaryContainer = Color(0xFFF9DEDC),
                onPrimaryContainer = Color(0xFF410E0B),
                secondary = Color(0xFF855355),
                onSecondary = Color(0xFFFFFFFF),
                secondaryContainer = Color(0xFFFFDAD6),
                onSecondaryContainer = Color(0xFF41000B),
                background = Color(0xFFFFF8F7),
                onBackground = Color(0xFF261A19),
                surface = Color(0xFFFFF8F7),
                onSurface = Color(0xFF261A19),
                surfaceVariant = Color(0xFFF5DDDB),
                onSurfaceVariant = Color(0xFF534341),
                surfaceContainer = Color(0xFFFCEAE8),
                surfaceContainerLow = Color(0xFFFFF1F0),
                surfaceContainerHigh = Color(0xFFF6E4E2),
                outline = Color(0xFF857370)
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
}
