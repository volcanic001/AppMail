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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
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
    inboxListState: LazyListState,
    trashListState: LazyListState,
    searchListState: LazyListState,
    highlightedEmailId: String?,
    onClearHighlight: () -> Unit,
    onCloseDetail: (String) -> Unit,
    onMenuClick: () -> Unit,
    currentPalette: ColorPalette,
    isDarkMode: Boolean,
    useCustomFont: Boolean,
    isAmoled: Boolean,
    isSigningOut: Boolean,
    onPaletteChange: (ColorPalette) -> Unit,
    onDarkModeChange: (Boolean) -> Unit,
    onUseCustomFontChange: (Boolean) -> Unit,
    onAmoledChange: (Boolean) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = MainRoute.Inbox,
        modifier = modifier.fillMaxSize()
    ) {
        composable<MainRoute.Inbox>(
            enterTransition = { fadeIn(spring(dampingRatio = 0.65f, stiffness = 350f)) },
            exitTransition = { fadeOut(spring(dampingRatio = 0.65f, stiffness = 350f)) }
        ) {
            InboxScreen(
                listState = inboxListState,
                highlightedEmailId = highlightedEmailId,
                onClearHighlight = onClearHighlight,
                onMenuClick = onMenuClick,
                onSearchClick = {
                    navController.navigate(MainRoute.Search)
                },
                onEmailClick = { emailId ->
                    navController.navigate(MainRoute.EmailDetail(emailId))
                }
            )
        }

        composable<MainRoute.Trash>(
            enterTransition = { fadeIn(spring(dampingRatio = 0.65f, stiffness = 350f)) },
            exitTransition = { fadeOut(spring(dampingRatio = 0.65f, stiffness = 350f)) }
        ) {
            TrashScreen(
                listState = trashListState,
                highlightedEmailId = highlightedEmailId,
                onClearHighlight = onClearHighlight,
                onMenuClick = onMenuClick,
                onEmailClick = { emailId ->
                    navController.navigate(MainRoute.EmailDetail(emailId))
                }
            )
        }

        composable<MainRoute.Settings>(
            enterTransition = { fadeIn(spring(dampingRatio = 0.65f, stiffness = 350f)) },
            exitTransition = { fadeOut(spring(dampingRatio = 0.65f, stiffness = 350f)) }
        ) {
            SettingsScreen(
                currentPalette = currentPalette,
                isDarkMode = isDarkMode,
                useCustomFont = useCustomFont,
                isAmoled = isAmoled,
                isSigningOut = isSigningOut,
                onPaletteChange = onPaletteChange,
                onDarkModeChange = onDarkModeChange,
                onUseCustomFontChange = onUseCustomFontChange,
                onAmoledChange = onAmoledChange,
                onSignOut = onSignOut,
                onBack = { navController.popBackStack() }
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
            val entryKey = backStackEntry.id
            SearchScreen(
                listState = searchListState,
                entryKey = entryKey,
                highlightedEmailId = highlightedEmailId,
                onClearHighlight = onClearHighlight,
                onBack = { navController.popBackStack() },
                onEmailClick = { emailId ->
                    navController.navigate(MainRoute.EmailDetail(emailId))
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
            BackHandler {
                onCloseDetail(detailRoute.emailId)
            }
            EmailDetailScreen(
                emailId = detailRoute.emailId,
                onBack = { onCloseDetail(detailRoute.emailId) },
                onReply = { emailId ->
                    navController.navigate(MainRoute.Compose(ComposeMode.REPLY, emailId))
                },
                onForward = { emailId ->
                    navController.navigate(MainRoute.Compose(ComposeMode.FORWARD, emailId))
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
                onClose = { navController.popBackStack() }
            )
        }
    }
}
