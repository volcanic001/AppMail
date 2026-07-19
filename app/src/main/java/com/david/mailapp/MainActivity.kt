package com.david.mailapp

import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.david.mailapp.core.di.AppContainer
import com.david.mailapp.feature.auth.LoginScreen
import com.david.mailapp.ui.navigation.MainScreen
import com.david.mailapp.ui.theme.ColorPalette
import com.david.mailapp.ui.theme.MailAppTheme
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val isSignedInFlow = MutableStateFlow(false)

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

        handleOAuthRedirect(intent)

        setContent {
            val systemDark = isSystemInDarkTheme()
            val savedPalette by AppContainer.appSettingsManager.paletteFlow.collectAsState(initial = null)
            val savedDarkMode by AppContainer.appSettingsManager.isDarkModeFlow.collectAsState(initial = null)
            val savedUseCustomFont by AppContainer.appSettingsManager.useCustomFontFlow.collectAsState(initial = null)
            val savedIsAmoled by AppContainer.appSettingsManager.isAmoledFlow.collectAsState(initial = null)
            val isSignedIn by isSignedInFlow.collectAsState()
            
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
                        onSignOut = { isSignedInFlow.value = false }
                    )
                } else {
                    LoginScreen()
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleOAuthRedirect(intent)
    }

    private fun handleOAuthRedirect(intent: android.content.Intent?) {
        val uriString = intent?.data?.toString() ?: return
        if (!uriString.startsWith("com.david.mailapp:")) return

        val normalizedUri = if (uriString.contains("://")) Uri.parse(uriString) else Uri.parse(uriString.replaceFirst(":", "://"))

        val code = normalizedUri.getQueryParameter("code") ?: return
        val error = normalizedUri.getQueryParameter("error")

        if (error != null) {
            Log.e("MailApp", "OAuth error: $error — ${normalizedUri.getQueryParameter("error_description")}")
            Toast.makeText(this, "Sign in failed: $error", Toast.LENGTH_LONG).show()
            return
        }

        Log.d("MailApp", "OAuth code received, exchanging for tokens…")

        lifecycleScope.launch {
            try {
                val authClient = AppContainer.authClient
                val tokens = authClient.exchangeCodeForTokens(code)
                Log.d("MailApp", "Tokens received — access=${tokens.accessToken.take(10)}…, refresh=${tokens.refreshToken.take(10)}…")
                AppContainer.activateProvider()
                isSignedInFlow.value = true
                Log.d("MailApp", "Provider activated, switching to MainScreen")
            } catch (e: Exception) {
                Log.e("MailApp", "Token exchange failed", e)
                Toast.makeText(this@MainActivity, "Sign in failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
