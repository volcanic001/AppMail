package com.david.mailapp.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.david.mailapp.feature.inbox.InboxScreen
import com.david.mailapp.feature.compose.ComposeArgs
import com.david.mailapp.feature.compose.ComposeScreen
import com.david.mailapp.feature.emaildetail.EmailDetailScreen
import com.david.mailapp.feature.search.SearchScreen
import com.david.mailapp.feature.settings.SettingsScreen
import com.david.mailapp.feature.trash.TrashScreen
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
    isSigningOut: Boolean = false,
    onPaletteChange: (ColorPalette) -> Unit = {},
    onDarkModeChange: (Boolean) -> Unit = {},
    onUseCustomFontChange: (Boolean) -> Unit = {},
    onAmoledChange: (Boolean) -> Unit = {},
    onSignOut: () -> Unit = {}
) {
    val navigator = rememberNavigator()
    val selectedScreen = navigator.current
    var searchEntryKey by remember { mutableStateOf(0L) }
    // These screens are removed from AnimatedContent after navigating to a
    // message. Keep their scroll state at this longer-lived navigation level
    // so returning from EmailDetail restores the exact list position.
    val inboxListState = rememberLazyListState()
    val trashListState = rememberLazyListState()
    val searchListState = rememberLazyListState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val onMenuClick: () -> Unit = {
        scope.launch { drawerState.open() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = selectedScreen !is Screen.EmailDetail,
        drawerContent = {
            DrawerContent(
                selectedScreen = selectedScreen,
                onScreenSelected = { screen ->
                    navigator.switchTab(screen)
                    scope.launch { drawerState.close() }
                }
            )
        }
    ) {
        BackHandler(enabled = navigator.canPop) {
            navigator.pop()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // ── Content area ─────────────────────────────────
            if (selectedScreen is Screen.EmailDetail) {
                // EmailDetail renders directly without AnimatedContent,
                // so no fade/slide/scale/alpha transform wraps the WebView.
                EmailDetailScreen(
                    emailId = (selectedScreen as Screen.EmailDetail).emailId,
                    onBack = { navigator.pop() },
                    onReply = { email -> navigator.openCompose(ComposeArgs.Reply(email)) },
                    onForward = { email -> navigator.openCompose(ComposeArgs.Forward(email)) }
                )
            } else {
                AnimatedContent(
                    targetState = selectedScreen,
                    transitionSpec = {
                        when {
                            targetState == Screen.Search -> {
                                // Inbox → Search: Search slides up from below + fade in
                                (slideInVertically(
                                    animationSpec = spring(
                                        dampingRatio = 0.55f,
                                        stiffness = 280f
                                    ),
                                    initialOffsetY = { -it / 4 }
                                ) + fadeIn(MotionTokens.searchExpand)).togetherWith(
                                    fadeOut(MotionTokens.tweenShort()) + scaleOut(
                                        targetScale = 0.95f,
                                        animationSpec = MotionTokens.tweenShort()
                                    )
                                )
                            }
                            initialState == Screen.Search -> {
                                // Search → Inbox: Search slides down + fade out
                                (fadeIn(MotionTokens.tweenShort())).togetherWith(
                                    slideOutVertically(
                                        animationSpec = spring(
                                            dampingRatio = 0.7f,
                                            stiffness = 400f
                                        ),
                                        targetOffsetY = { -it / 4 }
                                    ) + fadeOut(MotionTokens.searchCollapse)
                                )
                            }
                            else -> {
                                // Default: cross-fade
                                fadeIn(spring(dampingRatio = 0.65f, stiffness = 350f)) togetherWith
                                    fadeOut(spring(dampingRatio = 0.65f, stiffness = 350f))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    label = "screenTransition"
                ) { screen ->
                    when (screen) {
                        Screen.Inbox -> InboxScreen(
                            listState = inboxListState,
                            highlightedEmailId = navigator.highlightedEmailId,
                            onClearHighlight = { navigator.clearHighlightedEmail() },
                            onMenuClick = onMenuClick,
                            onSearchClick = {
                                searchEntryKey = System.currentTimeMillis()
                                navigator.push(Screen.Search)
                            },
                            onEmailClick = { emailId ->
                                navigator.push(Screen.EmailDetail(emailId))
                            }
                        )
                        Screen.Trash -> TrashScreen(
                            listState = trashListState,
                            highlightedEmailId = navigator.highlightedEmailId,
                            onClearHighlight = { navigator.clearHighlightedEmail() },
                            onMenuClick = onMenuClick,
                            onEmailClick = { emailId ->
                                navigator.push(Screen.EmailDetail(emailId))
                            }
                        )
                        Screen.Settings -> SettingsScreen(
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
                            onBack = { navigator.pop() }
                        )
                        Screen.Search -> SearchScreen(
                            listState = searchListState,
                            entryKey = searchEntryKey,
                            highlightedEmailId = navigator.highlightedEmailId,
                            onClearHighlight = { navigator.clearHighlightedEmail() },
                            onBack = { navigator.pop() },
                            onEmailClick = { emailId ->
                                navigator.push(Screen.EmailDetail(emailId))
                            }
                        )
                        is Screen.EmailDetail -> {
                            // Handled outside AnimatedContent — unreachable
                        }
                        is Screen.Compose -> ComposeScreen(
                            args = screen.args,
                            onClose = { navigator.pop() }
                        )
                    }
                }
            }

            // ── FAB "Redactar" (hidden on Search & Settings) ──────
            AnimatedVisibility(
                visible = selectedScreen == Screen.Inbox || selectedScreen == Screen.Trash,
                enter = fadeIn(MotionTokens.tweenShort()) + scaleIn(),
                exit = fadeOut(MotionTokens.tweenShort()) + scaleOut(animationSpec = MotionTokens.tweenShort(), targetScale = 0.8f),
                modifier = Modifier.align(Alignment.BottomEnd)
            ) {
                ComposeFab(
                    onClick = { navigator.openCompose(ComposeArgs.Write) },
                    modifier = Modifier
                        .padding(end = 20.dp, bottom = 24.dp)
                )
            }
        }
    }
}
