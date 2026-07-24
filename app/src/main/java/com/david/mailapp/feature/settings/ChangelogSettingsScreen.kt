package com.david.mailapp.feature.settings

import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.david.mailapp.R
import com.david.mailapp.feature.settings.components.SettingsCard
import com.david.mailapp.feature.settings.components.SettingsCardPosition

/**
 * Entrada del changelog con referencias a recursos Android.
 *
 * [versionResId] apunta al string de la versión (non_translatable).
 * [changesResId] apunta al array de cambios.
 */
internal data class ChangelogEntry(
    @StringRes val versionResId: Int,
    @ArrayRes val changesResId: Int
)

/**
 * Tabla explícita e inmutable con las 15 versiones del changelog.
 * Mantiene el orden actual, de la más reciente a la más antigua.
 * Sin getIdentifier, reflexión ni nombres dinámicos.
 */
internal val changelogTable = listOf(
    ChangelogEntry(R.string.changelog_version_1_7_0, R.array.changelog_1_7_0_changes),
    ChangelogEntry(R.string.changelog_version_1_5_0, R.array.changelog_1_5_0_changes),
    ChangelogEntry(R.string.changelog_version_1_3_0, R.array.changelog_1_3_0_changes),
    ChangelogEntry(R.string.changelog_version_1_0_0, R.array.changelog_1_0_0_changes),
    ChangelogEntry(R.string.changelog_version_0_9_6, R.array.changelog_0_9_6_changes),
    ChangelogEntry(R.string.changelog_version_0_9_0, R.array.changelog_0_9_0_changes),
    ChangelogEntry(R.string.changelog_version_0_8_0, R.array.changelog_0_8_0_changes),
    ChangelogEntry(R.string.changelog_version_0_7_0, R.array.changelog_0_7_0_changes),
    ChangelogEntry(R.string.changelog_version_0_6_0, R.array.changelog_0_6_0_changes),
    ChangelogEntry(R.string.changelog_version_0_5_0, R.array.changelog_0_5_0_changes),
    ChangelogEntry(R.string.changelog_version_0_4_1, R.array.changelog_0_4_1_changes),
    ChangelogEntry(R.string.changelog_version_0_4_0, R.array.changelog_0_4_0_changes),
    ChangelogEntry(R.string.changelog_version_0_3_0, R.array.changelog_0_3_0_changes),
    ChangelogEntry(R.string.changelog_version_0_2_0, R.array.changelog_0_2_0_changes),
    ChangelogEntry(R.string.changelog_version_0_1_0, R.array.changelog_0_1_0_changes)
)

/**
 * Dedicated screen for displaying the app's version history (Changelog).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangelogSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_changelog_title), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            changelogTable.forEach { entry ->
                item(key = "header_${entry.versionResId}") {
                    val versionText = stringResource(entry.versionResId)
                    Text(
                        text = versionText,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(start = 8.dp, bottom = 8.dp, top = 16.dp)
                    )
                }

                item(key = "changes_${entry.versionResId}") {
                    val changes = stringArrayResource(entry.changesResId).toList()
                    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        changes.forEachIndexed { index, change ->
                            val position = when {
                                changes.size == 1 -> SettingsCardPosition.Single
                                index == 0 -> SettingsCardPosition.First
                                index == changes.lastIndex -> SettingsCardPosition.Last
                                else -> SettingsCardPosition.Middle
                            }

                            SettingsCard(position = position) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                ) {
                                    Text(
                                        text = change,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
