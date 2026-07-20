package com.david.mailapp.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.david.mailapp.ui.theme.ColorPalette

/**
 * Settings entry point — hosts the hierarchical navigation graph.
 *
 * Formerly an 18 KB monolithic screen; now delegates entirely to
 * [SettingsNavHost], which manages internal routing, transitions, and
 * Predictive Back via Navigation Compose.
 *
 * The public API is unchanged — callers in [MainScreen] require zero
 * modifications.
 */
@Composable
fun SettingsScreen(
    currentPalette: ColorPalette,
    isDarkMode: Boolean,
    useCustomFont: Boolean,
    isAmoled: Boolean = false,
    onPaletteChange: (ColorPalette) -> Unit,
    onDarkModeChange: (Boolean) -> Unit,
    onUseCustomFontChange: (Boolean) -> Unit,
    onAmoledChange: (Boolean) -> Unit = {},
    isSigningOut: Boolean = false,
    onSignOut: () -> Unit = {},
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    SettingsNavHost(
        currentPalette = currentPalette,
        isDarkMode = isDarkMode,
        useCustomFont = useCustomFont,
        isAmoled = isAmoled,
        onPaletteChange = onPaletteChange,
        onDarkModeChange = onDarkModeChange,
        onUseCustomFontChange = onUseCustomFontChange,
        onAmoledChange = onAmoledChange,
        isSigningOut = isSigningOut,
        onSignOut = onSignOut,
        onBack = onBack,
        modifier = modifier
    )
}
