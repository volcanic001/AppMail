package com.david.mailapp.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.dp
import com.david.mailapp.feature.settings.components.SettingsCard
import com.david.mailapp.feature.settings.components.SettingsCardPosition
import com.david.mailapp.feature.settings.components.SettingsListItem
import com.david.mailapp.ui.theme.ColorPalette

/**
 * Settings hub — hierarchical navigation entry point.
 *
 * Each row is a [SettingsCard] wrapping a [SettingsListItem].
 * Tapping a card dispatches [onNavigateTo] with the corresponding
 * [SettingsRoute] so the parent [SettingsNavHost] handles the transition.
 *
 * @param currentPalette  Active palette, used in the Appearance card subtitle.
 * @param isDarkMode      Whether dark theme is active, used in the Appearance subtitle.
 * @param isSignedIn      Whether a mail account is connected, shown in Account subtitle.
 * @param onNavigateTo    Called with the target route when a card is tapped.
 * @param onBack          Navigates back to the previous screen (Inbox).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHubScreen(
    currentPalette: ColorPalette,
    isDarkMode: Boolean,
    isSignedIn: Boolean,
    onNavigateTo: (SettingsRoute) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val appearanceSummary = buildAppearanceSummary(currentPalette, isDarkMode)
    val accountSummary = if (isSignedIn) "Conectada" else "Sin conectar"

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .windowInsetsPadding(TopAppBarDefaults.windowInsets)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                }
                Text(
                    text = "Ajustes",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 16.dp)
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // ── Appearance ──────────────────────────────────
            item(key = "appearance") {
                SettingsCard(
                    onClick = { onNavigateTo(SettingsRoute.Appearance) },
                    position = SettingsCardPosition.First
                ) {
                    SettingsListItem(
                        headline = "Apariencia",
                        supporting = appearanceSummary,
                        icon = Icons.Default.Palette
                    )
                }
            }

            // ── Account ─────────────────────────────────────
            item(key = "account") {
                SettingsCard(
                    onClick = { onNavigateTo(SettingsRoute.Account) },
                    position = SettingsCardPosition.Middle
                ) {
                    SettingsListItem(
                        headline = "Cuenta",
                        supporting = accountSummary,
                        icon = Icons.AutoMirrored.Filled.Logout
                    )
                }
            }

            // ── Notifications ───────────────────────────────
            item(key = "notifications") {
                SettingsCard(
                    onClick = { onNavigateTo(SettingsRoute.Notifications) },
                    position = SettingsCardPosition.Middle
                ) {
                    SettingsListItem(
                        headline = "Notificaciones",
                        supporting = "Próximamente",
                        icon = Icons.Default.Notifications
                    )
                }
            }

            // ── Privacy ─────────────────────────────────────
            item(key = "privacy") {
                SettingsCard(
                    onClick = { onNavigateTo(SettingsRoute.Privacy) },
                    position = SettingsCardPosition.Middle
                ) {
                    SettingsListItem(
                        headline = "Privacidad",
                        supporting = "Próximamente",
                        icon = Icons.Default.Lock
                    )
                }
            }

            // ── Security ────────────────────────────────────
            item(key = "security") {
                SettingsCard(
                    onClick = { onNavigateTo(SettingsRoute.Security) },
                    position = SettingsCardPosition.Middle
                ) {
                    SettingsListItem(
                        headline = "Seguridad",
                        supporting = "Próximamente",
                        icon = Icons.Default.Security
                    )
                }
            }

            // ── About ───────────────────────────────────────
            item(key = "about") {
                SettingsCard(
                    onClick = { onNavigateTo(SettingsRoute.About) },
                    position = SettingsCardPosition.Last
                ) {
                    SettingsListItem(
                        headline = "Acerca de",
                        supporting = "MailApp v1.0",
                        icon = Icons.Default.Info
                    )
                }
            }
        }
    }
}

private fun buildAppearanceSummary(palette: ColorPalette, isDark: Boolean): String {
    val themeName = when {
        isDark -> "Oscuro"
        else -> "Claro"
    }
    val paletteName = if (palette == ColorPalette.Dynamic) {
        "Color dinámico"
    } else {
        palette.displayName
    }
    return "$paletteName · Tema $themeName"
}
