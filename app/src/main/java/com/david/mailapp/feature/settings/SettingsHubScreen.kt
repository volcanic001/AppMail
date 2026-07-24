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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.david.mailapp.BuildConfig
import com.david.mailapp.R
import com.david.mailapp.feature.settings.components.SettingsCard
import com.david.mailapp.feature.settings.components.SettingsCardPosition
import com.david.mailapp.feature.settings.components.SettingsListItem
import com.david.mailapp.ui.theme.ColorPalette

/**
 * Settings hub — hierarchical navigation entry point.
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
    val accountSummary = if (isSignedIn)
        stringResource(R.string.settings_connected)
    else
        stringResource(R.string.settings_disconnected)

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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
                Text(
                    text = stringResource(R.string.nav_settings),
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
                        headline = stringResource(R.string.settings_appearance),
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
                        headline = stringResource(R.string.settings_account),
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
                        headline = stringResource(R.string.settings_notifications),
                        supporting = stringResource(R.string.settings_coming_soon),
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
                        headline = stringResource(R.string.settings_privacy),
                        supporting = stringResource(R.string.settings_coming_soon),
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
                        headline = stringResource(R.string.settings_security),
                        supporting = stringResource(R.string.settings_coming_soon),
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
                        headline = stringResource(R.string.settings_about),
                        supporting = stringResource(R.string.about_version_format, BuildConfig.VERSION_NAME),
                        icon = Icons.Default.Info
                    )
                }
            }
        }
    }
}

@Composable
private fun buildAppearanceSummary(palette: ColorPalette, isDark: Boolean): String {
    val themeName = if (isDark)
        stringResource(R.string.theme_dark)
    else
        stringResource(R.string.theme_light)
    val paletteName = if (palette == ColorPalette.Dynamic) {
        stringResource(R.string.color_dynamic_title)
    } else {
        stringResource(palette.labelResId)
    }
    return stringResource(R.string.settings_appearance_summary, paletteName, themeName)
}
