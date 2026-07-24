package com.david.mailapp.ui.theme

import android.util.Log
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * Utility for debugging theme and color palette changes in Android Studio Logcat.
 *
 * Filter in Android Studio Logcat using:
 *   tag:PaletteDebug
 * or:
 *   package:com.david.mailapp tag:PaletteDebug
 */
object ThemeDebug {
    const val TAG = "PaletteDebug"

    fun logPaletteSelection(oldPalette: ColorPalette, newPalette: ColorPalette) {
        Log.d(TAG, "--------------------------------------------------")
        Log.d(TAG, "🎨 [PALETTE CLICK] User selected palette: ${newPalette.name} (was: ${oldPalette.name})")
        Log.d(TAG, "   Seed Color: ${newPalette.seedColor.toHex()}")
        Log.d(TAG, "   Preview Colors -> Top: ${newPalette.previewTop.toHex()} | BL: ${newPalette.previewBottomLeft.toHex()} | BR: ${newPalette.previewBottomRight.toHex()}")
    }

    fun logThemeEvaluation(
        palette: ColorPalette,
        isDark: Boolean,
        isDynamic: Boolean,
        colorScheme: ColorScheme
    ) {
        Log.d(TAG, "--------------------------------------------------")
        Log.d(TAG, "⚡ [THEME EVALUATED] Palette: ${palette.name} | DarkMode: $isDark | DynamicMonet: $isDynamic")
        Log.d(TAG, "   [Active ColorScheme Hex Values]:")
        Log.d(TAG, "   • primary:            ${colorScheme.primary.toHex()}")
        Log.d(TAG, "   • onPrimary:          ${colorScheme.onPrimary.toHex()}")
        Log.d(TAG, "   • primaryContainer:   ${colorScheme.primaryContainer.toHex()}")
        Log.d(TAG, "   • onPrimaryContainer: ${colorScheme.onPrimaryContainer.toHex()}")
        Log.d(TAG, "   • secondary:          ${colorScheme.secondary.toHex()}")
        Log.d(TAG, "   • secondaryContainer: ${colorScheme.secondaryContainer.toHex()}")
        Log.d(TAG, "   • tertiary:           ${colorScheme.tertiary.toHex()}")
        Log.d(TAG, "   • tertiaryContainer:  ${colorScheme.tertiaryContainer.toHex()}")
        Log.d(TAG, "   • background:         ${colorScheme.background.toHex()}")
        Log.d(TAG, "   • surface:            ${colorScheme.surface.toHex()}")
        Log.d(TAG, "   • surfaceContainer:   ${colorScheme.surfaceContainer.toHex()}")
        
        // Check for tonal palette glitch (where primary changes but primaryContainer/secondary remain default purple)
        if (!isDynamic && palette != ColorPalette.Dynamic && palette != ColorPalette.Purple) {
            val defaultPurpleContainer = Color(0xFFE8DEF8).toHex()
            if (colorScheme.primaryContainer.toHex() == defaultPurpleContainer) {
                Log.w(TAG, "⚠️ [GLITCH DETECTED] primaryContainer is still Material 3 default purple ($defaultPurpleContainer) while primary is ${colorScheme.primary.toHex()}! Tonal palette scheme generation is required!")
            }
        }
        Log.d(TAG, "--------------------------------------------------")
    }

    fun logDarkModeToggle(isDark: Boolean) {
        Log.d(TAG, "🌙 [DARK MODE TOGGLED] New state -> isDark = $isDark")
    }

    private fun Color.toHex(): String {
        return if (this == Color.Unspecified) "Unspecified"
        else String.format("#%08X", this.toArgb())
    }
}
