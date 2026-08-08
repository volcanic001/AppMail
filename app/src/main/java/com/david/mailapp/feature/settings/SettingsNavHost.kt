package com.david.mailapp.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.unit.IntOffset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.david.mailapp.R
import com.david.mailapp.core.di.AppContainer
import com.david.mailapp.ui.navigation.popBackStackFrom
import com.david.mailapp.ui.theme.ColorPalette

/**
 * Internal [NavHost] that manages the Settings hierarchy.
 *
 * Transitions use physical sheet mechanics (Android 15/16 Expressive style):
 * - Enter: top sheet slides in opaque from the right (`fullWidth`) over the underlying sheet.
 * - Exit:  underlying sheet parallax slides to the left (`-fullWidth / 4`).
 * - Pop:   top sheet slides out 100% solid/opaque to the right (`fullWidth`) without fading,
 *          preventing transparent overlap during predictive back gestures while revealing the bottom sheet.
 */
@Composable
fun SettingsNavHost(
    currentPalette: ColorPalette,
    isDarkMode: Boolean,
    useCustomFont: Boolean,
    isAmoled: Boolean = false,
    showEmailDividers: Boolean = true,
    onPaletteChange: (ColorPalette) -> Unit,
    onDarkModeChange: (Boolean) -> Unit,
    onUseCustomFontChange: (Boolean) -> Unit,
    onAmoledChange: (Boolean) -> Unit = {},
    onShowEmailDividersChange: (Boolean) -> Unit = {},
    isSigningOut: Boolean = false,
    onSignOut: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    var isSignedIn by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isSignedIn = AppContainer.authClient.isSignedIn()
    }

    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val closeCurrentSheet: (() -> Unit)? = remember(currentBackStackEntry) {
        currentBackStackEntry
            ?.takeIf { !it.destination.hasRoute<SettingsRoute.Hub>() }
            ?.let { sheetEntry -> { navController.popBackStackFrom(sheetEntry) } }
    }

    val slideSpring = spring<IntOffset>(dampingRatio = 0.85f, stiffness = 160f)

    NavHost(
        navController = navController,
        startDestination = SettingsRoute.Hub,
        modifier = modifier,
        enterTransition = {
            slideInHorizontally(
                initialOffsetX = { fullWidth: Int -> fullWidth },
                animationSpec = slideSpring
            )
        },
        exitTransition = {
            slideOutHorizontally(
                targetOffsetX = { fullWidth: Int -> -fullWidth / 4 },
                animationSpec = slideSpring
            )
        },
        popEnterTransition = {
            slideInHorizontally(
                initialOffsetX = { fullWidth: Int -> -fullWidth / 4 },
                animationSpec = slideSpring
            )
        },
        popExitTransition = {
            slideOutHorizontally(
                targetOffsetX = { fullWidth: Int -> fullWidth },
                animationSpec = slideSpring
            )
        }
    ) {
        composable<SettingsRoute.Hub> {
            SettingsHubScreen(
                currentPalette = currentPalette,
                isDarkMode = isDarkMode,
                isSignedIn = isSignedIn,
                onNavigateTo = { route -> navController.navigate(route) },
                onBack = onBack
            )
        }

        composable<SettingsRoute.Appearance> { backStackEntry ->
            AppearanceSettingsScreen(
                currentPalette = currentPalette,
                isDarkMode = isDarkMode,
                useCustomFont = useCustomFont,
                isAmoled = isAmoled,
                showEmailDividers = showEmailDividers,
                onPaletteChange = onPaletteChange,
                onDarkModeChange = onDarkModeChange,
                onUseCustomFontChange = onUseCustomFontChange,
                onAmoledChange = onAmoledChange,
                onShowEmailDividersChange = onShowEmailDividersChange,
                onBack = { navController.popBackStackFrom(backStackEntry) }
            )
        }

        composable<SettingsRoute.Account> { backStackEntry ->
            AccountSettingsScreen(
                isSigningOut = isSigningOut,
                onSignOut = onSignOut,
                onBack = { navController.popBackStackFrom(backStackEntry) }
            )
        }

        composable<SettingsRoute.Notifications> { backStackEntry ->
            PlaceholderSettingsScreen(
                titleResId = R.string.settings_notifications,
                onBack = { navController.popBackStackFrom(backStackEntry) }
            )
        }

        composable<SettingsRoute.Privacy> { backStackEntry ->
            PlaceholderSettingsScreen(
                titleResId = R.string.settings_privacy,
                onBack = { navController.popBackStackFrom(backStackEntry) }
            )
        }

        composable<SettingsRoute.Security> { backStackEntry ->
            PlaceholderSettingsScreen(
                titleResId = R.string.settings_security,
                onBack = { navController.popBackStackFrom(backStackEntry) }
            )
        }

        composable<SettingsRoute.About> { backStackEntry ->
            AboutSettingsScreen(
                onBack = { navController.popBackStackFrom(backStackEntry) },
                onChangelogClick = { navController.navigate(SettingsRoute.Changelog) }
            )
        }

        composable<SettingsRoute.Changelog> { backStackEntry ->
            ChangelogSettingsScreen(
                onBack = { navController.popBackStackFrom(backStackEntry) }
            )
        }
    }

    // Registered after NavHost so sheet handlers take precedence over any
    // generic pop callback inside NavHost. The captured entry reference
    // stays stable; a repeated dispatch on a removed entry is a no-op.
    BackHandler(enabled = closeCurrentSheet != null) {
        closeCurrentSheet?.invoke()
    }
}
