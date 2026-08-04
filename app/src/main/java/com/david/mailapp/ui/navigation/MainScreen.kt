package com.david.mailapp.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.david.mailapp.feature.compose.ComposeMode
import com.david.mailapp.ui.components.ComposeFab
import com.david.mailapp.ui.theme.ColorPalette
import com.david.mailapp.ui.theme.MotionTokens
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    currentPalette: ColorPalette = ColorPalette.Blue,
    isDarkMode: Boolean = false,
    useCustomFont: Boolean = false,
    isAmoled: Boolean = false,
    showEmailDividers: Boolean = true,
    isSigningOut: Boolean = false,
    onPaletteChange: (ColorPalette) -> Unit = {},
    onDarkModeChange: (Boolean) -> Unit = {},
    onUseCustomFontChange: (Boolean) -> Unit = {},
    onAmoledChange: (Boolean) -> Unit = {},
    onShowEmailDividersChange: (Boolean) -> Unit = {},
    onSignOut: () -> Unit = {},
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()

    // Safely extract the current route object using hasRoute & toRoute
    val currentRoute = remember(navBackStackEntry) {
        val entry = navBackStackEntry ?: return@remember null
        val destination = entry.destination
        when {
            destination.hasRoute<MainRoute.Inbox>() -> MainRoute.Inbox
            destination.hasRoute<MainRoute.Trash>() -> MainRoute.Trash
            destination.hasRoute<MainRoute.Settings>() -> MainRoute.Settings
            destination.hasRoute<MainRoute.Search>() -> MainRoute.Search
            destination.hasRoute<MainRoute.EmailDetail>() -> entry.toRoute<MainRoute.EmailDetail>()
            destination.hasRoute<MainRoute.Compose>() -> entry.toRoute<MainRoute.Compose>()
            else -> null
        }
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val onMenuClick: () -> Unit = {
        scope.launch { drawerState.open() }
    }

    val gesturesEnabled = currentRoute is MainRoute.Inbox || currentRoute is MainRoute.Trash || currentRoute is MainRoute.Settings

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = gesturesEnabled,
        drawerContent = {
            DrawerContent(
                currentRoute = currentRoute ?: MainRoute.Inbox,
                onRouteSelected = { route ->
                    scope.launch { drawerState.close() }

                    if (currentRoute != route) {
                        navController.navigateToTopLevel(route)
                    }
                }
            )
        }
    ) {
        // Intercept back button to close drawer first if it's open
        BackHandler(enabled = drawerState.isOpen) {
            scope.launch { drawerState.close() }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // ── Content area ─────────────────────────────────
            MainNavHost(
                navController = navController,
                onMenuClick = onMenuClick,
                currentPalette = currentPalette,
                isDarkMode = isDarkMode,
                useCustomFont = useCustomFont,
                isAmoled = isAmoled,
                showEmailDividers = showEmailDividers,
                isSigningOut = isSigningOut,
                onPaletteChange = onPaletteChange,
                onDarkModeChange = onDarkModeChange,
                onUseCustomFontChange = onUseCustomFontChange,
                onAmoledChange = onAmoledChange,
                onShowEmailDividersChange = onShowEmailDividersChange,
                onSignOut = onSignOut
            )

            // ── FAB "Redactar" (hidden on Search & Settings & Compose & Detail) ──────
            val isFabVisible = currentRoute is MainRoute.Inbox || currentRoute is MainRoute.Trash
            AnimatedVisibility(
                visible = isFabVisible,
                enter = fadeIn(MotionTokens.tweenShort()) + scaleIn(),
                exit = fadeOut(MotionTokens.tweenShort()) + scaleOut(animationSpec = MotionTokens.tweenShort(), targetScale = 0.8f),
                modifier = Modifier.align(Alignment.BottomEnd)
            ) {
                ComposeFab(
                    onClick = {
                        navController.navigateToOverlay(MainRoute.Compose(ComposeMode.WRITE))
                    },
                    modifier = Modifier
                        .testTag("fab_compose")
                        .padding(end = 20.dp, bottom = 24.dp)
                )
            }
        }
    }
}
