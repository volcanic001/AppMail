package com.david.mailapp.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.toRoute
import com.david.mailapp.feature.compose.ComposeMode
import com.david.mailapp.feature.compose.ComposeScreen
import com.david.mailapp.feature.emaildetail.EmailDetailScreen
import com.david.mailapp.feature.inbox.InboxScreen
import com.david.mailapp.feature.search.SearchScreen
import com.david.mailapp.feature.settings.SettingsScreen
import com.david.mailapp.feature.trash.TrashScreen
import com.david.mailapp.ui.theme.MotionTokens
import com.david.mailapp.ui.theme.ColorPalette

@Composable
fun MainNavHost(
    navController: NavHostController,
    onMenuClick: () -> Unit,
    currentPalette: ColorPalette,
    isDarkMode: Boolean,
    useCustomFont: Boolean,
    isAmoled: Boolean,
    showEmailDividers: Boolean,
    isSigningOut: Boolean,
    onPaletteChange: (ColorPalette) -> Unit,
    onDarkModeChange: (Boolean) -> Unit,
    onUseCustomFontChange: (Boolean) -> Unit,
    onAmoledChange: (Boolean) -> Unit,
    onShowEmailDividersChange: (Boolean) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val closeCurrentOverlay: (() -> Unit)? = remember(currentBackStackEntry) {
        val entry = currentBackStackEntry ?: return@remember null
        when {
            entry.destination.hasRoute<MainRoute.EmailDetail>() -> {
                val emailId = entry.toRoute<MainRoute.EmailDetail>().emailId
                { navController.closeEmailDetail(entry, emailId) }
            }
            entry.destination.hasRoute<MainRoute.Compose>() -> {
                { navController.popBackStackFrom(entry) }
            }
            else -> null
        }
    }

    NavHost(
        navController = navController,
        startDestination = MainRoute.Inbox,
        modifier = modifier.fillMaxSize()
    ) {
        composable<MainRoute.Inbox>(
            enterTransition = { fadeIn(spring(dampingRatio = 0.65f, stiffness = 350f)) },
            exitTransition = { fadeOut(spring(dampingRatio = 0.65f, stiffness = 350f)) }
        ) { backStackEntry ->
            val inboxListState = rememberLazyListState()
            val highlightedEmailId by backStackEntry.savedStateHandle
                .getStateFlow<String?>(KEY_CLOSED_EMAIL_ID, null)
                .collectAsStateWithLifecycle()
            InboxScreen(
                listState = inboxListState,
                highlightedEmailId = highlightedEmailId,
                showEmailDividers = showEmailDividers,
                onClearHighlight = {
                    backStackEntry.savedStateHandle[KEY_CLOSED_EMAIL_ID] = null
                },
                onMenuClick = onMenuClick,
                onSearchClick = {
                    navController.navigateToOverlay(MainRoute.Search)
                },
                onEmailClick = { emailId ->
                    navController.navigateToOverlay(MainRoute.EmailDetail(emailId))
                }
            )
        }

        composable<MainRoute.Trash>(
            enterTransition = { fadeIn(spring(dampingRatio = 0.65f, stiffness = 350f)) },
            exitTransition = { fadeOut(spring(dampingRatio = 0.65f, stiffness = 350f)) }
        ) { backStackEntry ->
            val trashListState = rememberLazyListState()
            val highlightedEmailId by backStackEntry.savedStateHandle
                .getStateFlow<String?>(KEY_CLOSED_EMAIL_ID, null)
                .collectAsStateWithLifecycle()
            TrashScreen(
                listState = trashListState,
                highlightedEmailId = highlightedEmailId,
                showEmailDividers = showEmailDividers,
                onClearHighlight = {
                    backStackEntry.savedStateHandle[KEY_CLOSED_EMAIL_ID] = null
                },
                onMenuClick = onMenuClick,
                onEmailClick = { emailId ->
                    navController.navigateToOverlay(MainRoute.EmailDetail(emailId))
                }
            )
        }

        composable<MainRoute.Settings>(
            enterTransition = { fadeIn(spring(dampingRatio = 0.65f, stiffness = 350f)) },
            exitTransition = { fadeOut(spring(dampingRatio = 0.65f, stiffness = 350f)) }
        ) { backStackEntry ->
            SettingsScreen(
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
                onSignOut = onSignOut,
                onBack = { navController.popBackStackFrom(backStackEntry) }
            )
        }

        composable<MainRoute.Search>(
            enterTransition = {
                slideInVertically(
                    animationSpec = spring(dampingRatio = 0.55f, stiffness = 280f),
                    initialOffsetY = { -it / 4 }
                ) + fadeIn(MotionTokens.searchExpand)
            },
            exitTransition = {
                fadeOut(MotionTokens.tweenShort()) + scaleOut(
                    targetScale = 0.95f,
                    animationSpec = MotionTokens.tweenShort()
                )
            },
            popEnterTransition = {
                fadeIn(MotionTokens.tweenShort())
            },
            popExitTransition = {
                slideOutVertically(
                    animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
                    targetOffsetY = { -it / 4 }
                ) + fadeOut(MotionTokens.searchCollapse)
            }
        ) { backStackEntry ->
            val searchListState = rememberLazyListState()
            val entryKey = backStackEntry.id
            val highlightedEmailId by backStackEntry.savedStateHandle
                .getStateFlow<String?>(KEY_CLOSED_EMAIL_ID, null)
                .collectAsStateWithLifecycle()
            SearchScreen(
                listState = searchListState,
                entryKey = entryKey,
                highlightedEmailId = highlightedEmailId,
                showEmailDividers = showEmailDividers,
                onClearHighlight = {
                    backStackEntry.savedStateHandle[KEY_CLOSED_EMAIL_ID] = null
                },
                onBack = { navController.popBackStackFrom(backStackEntry) },
                onEmailClick = { emailId ->
                    navController.navigateToOverlay(MainRoute.EmailDetail(emailId))
                }
            )
        }

        composable<MainRoute.EmailDetail>(
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None }
        ) { backStackEntry ->
            val detailRoute: MainRoute.EmailDetail = backStackEntry.toRoute()
            val closeDetail: () -> Unit = {
                navController.closeEmailDetail(backStackEntry, detailRoute.emailId)
            }
            EmailDetailScreen(
                emailId = detailRoute.emailId,
                onBack = closeDetail,
                onReply = { emailId ->
                    navController.navigateToOverlay(MainRoute.Compose(ComposeMode.REPLY, emailId))
                },
                onForward = { emailId ->
                    navController.navigateToOverlay(MainRoute.Compose(ComposeMode.FORWARD, emailId))
                }
            )
        }

        composable<MainRoute.Compose>(
            enterTransition = { fadeIn(spring(dampingRatio = 0.65f, stiffness = 350f)) },
            exitTransition = { fadeOut(spring(dampingRatio = 0.65f, stiffness = 350f)) }
        ) { backStackEntry ->
            val composeRoute: MainRoute.Compose = backStackEntry.toRoute()
            val args = composeRoute.toComposeArgs()
            ComposeScreen(
                args = args,
                onClose = { navController.popBackStackFrom(backStackEntry) }
            )
        }
    }

    // Register after NavHost so overlay back handlers take precedence over
    // any generic pop callback inside NavHost. The captured entry reference
    // stays stable; a repeated dispatch on a removed entry is a no-op.
    BackHandler(enabled = closeCurrentOverlay != null) {
        closeCurrentOverlay?.invoke()
    }
}
