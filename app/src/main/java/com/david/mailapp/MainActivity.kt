package com.david.mailapp

import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.david.mailapp.core.auth.OAuthLaunchPreflightResult
import com.david.mailapp.core.auth.OAuthLaunchResult
import com.david.mailapp.core.auth.OAuthRedirectResult
import com.david.mailapp.core.auth.runOAuthLaunchPreflight
import com.david.mailapp.core.di.AppContainer
import com.david.mailapp.core.localization.UiErrorReason
import com.david.mailapp.core.localization.UiText
import com.david.mailapp.core.localization.resolve
import com.david.mailapp.core.localization.toUiText
import com.david.mailapp.feature.auth.LoginScreen
import com.david.mailapp.core.webview.WebViewStartupAfterFirstSessionFrame
import com.david.mailapp.feature.auth.toUiTextOrNull
import com.david.mailapp.ui.navigation.MainScreen
import com.david.mailapp.ui.theme.ColorPalette
import com.david.mailapp.ui.theme.MailAppTheme
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class OAuthUiState {
    Idle,
    Launching,
    AwaitingRedirect,
    ProcessingRedirect,
    Cancelling
}

class MainActivity : ComponentActivity() {

    private val isSignedInFlow = MutableStateFlow(false)
    private val oauthUiStateFlow = MutableStateFlow(OAuthUiState.Idle)
    private var oauthBrowserWasLeft = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        // Check auth state on launch
        lifecycleScope.launch {
            val signedIn = AppContainer.authClient.isSignedIn()
            if (signedIn && !AppContainer.oauthTokenManager.isReauthenticationPending) {
                AppContainer.activateProvider()
            }
            isSignedInFlow.value = signedIn && !AppContainer.oauthTokenManager.isReauthenticationPending
        }

        lifecycleScope.launch {
            AppContainer.sessionExpiredSignal.collect { expired ->
                if (expired) {
                    viewModelStore.clear()
                    isSignedInFlow.value = false
                    showToast(UiErrorReason.SESSION_EXPIRED.toUiText(), Toast.LENGTH_LONG)
                    AppContainer.sessionExpiredSignal.value = false
                }
            }
        }

        handleOAuthRedirect(intent?.data)

        setContent {
            // ... (existing Compose content)
            val systemDark = isSystemInDarkTheme()
            val savedPalette by AppContainer.appSettingsManager.paletteFlow.collectAsStateWithLifecycle(initialValue = null)
            val savedDarkMode by AppContainer.appSettingsManager.isDarkModeFlow.collectAsStateWithLifecycle(initialValue = null)
            val savedUseCustomFont by AppContainer.appSettingsManager.useCustomFontFlow.collectAsStateWithLifecycle(initialValue = null)
            val savedIsAmoled by AppContainer.appSettingsManager.isAmoledFlow.collectAsStateWithLifecycle(initialValue = null)
            val savedShowEmailDividers by AppContainer.appSettingsManager.showEmailDividersFlow.collectAsStateWithLifecycle(initialValue = null)
            val isSignedIn by isSignedInFlow.collectAsStateWithLifecycle()
            val oauthUiState by oauthUiStateFlow.collectAsStateWithLifecycle()
            var isSigningOut by remember { mutableStateOf(false) }

            val scope = androidx.compose.runtime.rememberCoroutineScope()

            val palette = savedPalette ?: ColorPalette.Blue
            val isDark = savedDarkMode ?: systemDark
            val useCustomFont = savedUseCustomFont ?: false
            val isAmoled = savedIsAmoled ?: false
            val showEmailDividers = savedShowEmailDividers ?: true

            // Transparent edge-to-edge — updates dynamically with dark/light mode
            LaunchedEffect(isDark) {
                val barStyle = if (isDark) {
                    SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                } else {
                    SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                }
                enableEdgeToEdge(
                    statusBarStyle = barStyle,
                    navigationBarStyle = barStyle
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = false
                }
            }

            MailAppTheme(darkTheme = isDark, palette = palette, useCustomFont = useCustomFont, isAmoled = isAmoled) {
                if (isSignedIn) {
                    WebViewStartupAfterFirstSessionFrame()
                    MainScreen(
                        currentPalette = palette,
                        isDarkMode = isDark,
                        useCustomFont = useCustomFont,
                        isAmoled = isAmoled,
                        showEmailDividers = showEmailDividers,
                        isSigningOut = isSigningOut,
                        onPaletteChange = { newPalette ->
                            scope.launch { AppContainer.appSettingsManager.setPalette(newPalette) }
                        },
                        onDarkModeChange = { newDark ->
                            scope.launch { AppContainer.appSettingsManager.setDarkMode(newDark) }
                        },
                        onUseCustomFontChange = { newUseCustomFont ->
                            scope.launch { AppContainer.appSettingsManager.setUseCustomFont(newUseCustomFont) }
                        },
                        onAmoledChange = { newAmoled ->
                            scope.launch { AppContainer.appSettingsManager.setAmoled(newAmoled) }
                        },
                        onShowEmailDividersChange = { newShow ->
                            scope.launch { AppContainer.appSettingsManager.setShowEmailDividers(newShow) }
                        },
                        onSignOut = {
                            scope.launch {
                                isSigningOut = true
                                when (val result = AppContainer.signOut()) {
                                    is AppContainer.SignOutResult.Success -> {
                                        // Feature ViewModels are Activity-scoped. Clear session data
                                        // so a subsequent login creates fresh Inbox/Trash state.
                                        viewModelStore.clear()
                                        isSigningOut = false
                                        isSignedInFlow.value = false
                                    }
                                    is AppContainer.SignOutResult.Failed -> {
                                        isSigningOut = false
                                        showToast(result.reason.toUiText())
                                    }
                                }
                            }
                        }
                    )
                } else {
                    LoginScreen(
                        isLaunching = oauthUiState != OAuthUiState.Idle,
                        onSignInClick = ::launchOAuth
                    )
                }
            }
        }
    }

    override fun onPause() {
        if (
            oauthUiStateFlow.value == OAuthUiState.Launching ||
            oauthUiStateFlow.value == OAuthUiState.AwaitingRedirect
        ) {
            oauthBrowserWasLeft = true
        }
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (
            oauthUiStateFlow.value == OAuthUiState.AwaitingRedirect &&
            oauthBrowserWasLeft
        ) {
            cancelOAuthWithoutRedirect()
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleOAuthRedirect(intent.data)
    }

    private fun launchOAuth() {
        if (!oauthUiStateFlow.compareAndSet(OAuthUiState.Idle, OAuthUiState.Launching)) {
            return
        }
        oauthBrowserWasLeft = false

        lifecycleScope.launch {
            val preflight = runOAuthLaunchPreflight(
                isPendingPdfCleanup = { AppContainer.authManager.isPendingPdfCleanup() },
                clearPdfCache = { withContext(Dispatchers.IO) { AppContainer.pdfCacheManager.clearAll() } },
                markPdfCleanupCompleted = { AppContainer.authManager.setPendingPdfCleanup(false) }
            )
            when (preflight) {
                is OAuthLaunchPreflightResult.Failed -> {
                    resetOAuthUiState()
                    showToast(preflight.reason.toUiText())
                    return@launch
                }
                is OAuthLaunchPreflightResult.Ready -> {
                    // continue to OAuth launch below
                }
            }

            when (val result = AppContainer.authClient.launchAuth(this@MainActivity)) {
                OAuthLaunchResult.Launched -> {
                    oauthUiStateFlow.value = OAuthUiState.AwaitingRedirect
                }
                else -> {
                    resetOAuthUiState()
                    val uiText = result.toUiTextOrNull()
                    if (uiText != null) {
                        showToast(uiText)
                    }
                }
            }
        }
    }

    private fun cancelOAuthWithoutRedirect() {
        oauthUiStateFlow.value = OAuthUiState.Cancelling

        lifecycleScope.launch {
            try {
                AppContainer.authClient.cancelPendingAuth()
            } finally {
                resetOAuthUiState()
                showToast(UiText.Resource(com.david.mailapp.R.string.session_auth_cancelled))
            }
        }
    }

    private fun resetOAuthUiState() {
        oauthBrowserWasLeft = false
        oauthUiStateFlow.value = OAuthUiState.Idle
    }

    /**
     * Extract the redirect URI from [intentData], delegate validation and token exchange
     * to [GmailAuthClient.handleOAuthRedirect], and update UI state accordingly.
     * Clears [intentData] after capture to prevent reprocessing on recreation.
     */
    private fun handleOAuthRedirect(intentData: Uri?) {
        val uri = intentData ?: return
        // Clear immediately to prevent reprocessing on activity recreation
        intent?.data = null
        oauthBrowserWasLeft = false
        oauthUiStateFlow.value = OAuthUiState.ProcessingRedirect

        lifecycleScope.launch {
            try {
                val result = AppContainer.authClient.handleOAuthRedirect(uri)
                val uiText = result.toUiTextOrNull()
                when (result) {
                    OAuthRedirectResult.Success -> {
                        AppContainer.activateProvider()
                        isSignedInFlow.value = true
                    }
                    else -> {
                        if (uiText != null) {
                            showToast(uiText)
                        }
                    }
                }
            } finally {
                resetOAuthUiState()
            }
        }
    }

    /**
     * Helper para mostrar un [Toast] a partir de un [UiText].
     * Resuelve el texto mediante [AppContainer.stringProvider].
     */
    private fun showToast(text: UiText, duration: Int = Toast.LENGTH_SHORT) {
        val message = text.resolve(AppContainer.stringProvider)
        Toast.makeText(this, message, duration).show()
    }
}
