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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.david.mailapp.core.auth.OAuthLaunchResult
import com.david.mailapp.core.auth.OAuthRedirectResult
import com.david.mailapp.core.di.AppContainer
import com.david.mailapp.feature.auth.LoginScreen
import com.david.mailapp.ui.navigation.MainScreen
import com.david.mailapp.ui.theme.ColorPalette
import com.david.mailapp.ui.theme.MailAppTheme
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

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
            if (signedIn) {
                AppContainer.activateProvider()
            }
            isSignedInFlow.value = signedIn
        }

        handleOAuthRedirect(intent?.data)

        setContent {
            // ... (existing Compose content)
            val systemDark = isSystemInDarkTheme()
            val savedPalette by AppContainer.appSettingsManager.paletteFlow.collectAsState(initial = null)
            val savedDarkMode by AppContainer.appSettingsManager.isDarkModeFlow.collectAsState(initial = null)
            val savedUseCustomFont by AppContainer.appSettingsManager.useCustomFontFlow.collectAsState(initial = null)
            val savedIsAmoled by AppContainer.appSettingsManager.isAmoledFlow.collectAsState(initial = null)
            val isSignedIn by isSignedInFlow.collectAsState()
            val oauthUiState by oauthUiStateFlow.collectAsState()
            var isSigningOut by remember { mutableStateOf(false) }

            val scope = androidx.compose.runtime.rememberCoroutineScope()

            val palette = savedPalette ?: ColorPalette.Blue
            val isDark = savedDarkMode ?: systemDark
            val useCustomFont = savedUseCustomFont ?: false
            val isAmoled = savedIsAmoled ?: false

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
                    MainScreen(
                        currentPalette = palette,
                        isDarkMode = isDark,
                        useCustomFont = useCustomFont,
                        isAmoled = isAmoled,
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
                                        Toast.makeText(
                                            this@MainActivity,
                                            result.message,
                                            Toast.LENGTH_SHORT
                                        ).show()
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
            when (AppContainer.authClient.launchAuth(this@MainActivity)) {
                OAuthLaunchResult.Launched -> {
                    oauthUiStateFlow.value = OAuthUiState.AwaitingRedirect
                }
                OAuthLaunchResult.NoBrowserAvailable -> {
                    resetOAuthUiState()
                    Toast.makeText(
                        this@MainActivity,
                        "No se encontró un navegador compatible.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                OAuthLaunchResult.Failed -> {
                    resetOAuthUiState()
                    Toast.makeText(
                        this@MainActivity,
                        "No se pudo abrir el inicio de sesión.",
                        Toast.LENGTH_SHORT
                    ).show()
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
                Toast.makeText(
                    this@MainActivity,
                    "Inicio de sesión cancelado.",
                    Toast.LENGTH_SHORT
                ).show()
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
                when (result) {
                    OAuthRedirectResult.Success -> {
                        AppContainer.activateProvider()
                        isSignedInFlow.value = true
                    }
                    OAuthRedirectResult.UserCancelled -> {
                        Toast.makeText(
                            this@MainActivity,
                            "Inicio de sesión cancelado.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    OAuthRedirectResult.InvalidSession,
                    OAuthRedirectResult.ExpiredSession -> {
                        Toast.makeText(
                            this@MainActivity,
                            "La sesión de inicio de sesión no es válida. Inténtalo nuevamente.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    OAuthRedirectResult.MissingAuthorizationCode,
                    OAuthRedirectResult.TokenExchangeFailed -> {
                        Toast.makeText(
                            this@MainActivity,
                            "No se pudo iniciar sesión. Inténtalo nuevamente.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    OAuthRedirectResult.NotOAuthRedirect -> Unit
                }
            } finally {
                resetOAuthUiState()
            }
        }
    }
}
