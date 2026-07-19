package com.david.mailapp.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.david.mailapp.feature.settings.components.PalettePreview
import com.david.mailapp.feature.settings.components.SettingsCard
import com.david.mailapp.feature.settings.components.SettingsCardPosition
import com.david.mailapp.feature.settings.components.SettingsListItem
import com.david.mailapp.feature.settings.components.ThemeChip
import com.david.mailapp.ui.theme.ColorPalette
import com.david.mailapp.ui.theme.ThemeDebug

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettingsScreen(
    currentPalette: ColorPalette,
    isDarkMode: Boolean,
    useCustomFont: Boolean,
    isAmoled: Boolean = false,
    onPaletteChange: (ColorPalette) -> Unit,
    onDarkModeChange: (Boolean) -> Unit,
    onUseCustomFontChange: (Boolean) -> Unit,
    onAmoledChange: (Boolean) -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDynamic = currentPalette == ColorPalette.Dynamic

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Apariencia", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
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
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // ── Theme ─────────────────────────────────────────
            item(key = "theme") {
                SettingsCard(position = SettingsCardPosition.First) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Tema",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ThemeChip(
                                label = "Sistema",
                                icon = Icons.Default.PhoneAndroid,
                                selected = false,
                                onClick = {
                                    ThemeDebug.logDarkModeToggle(false)
                                    onDarkModeChange(false)
                                },
                                modifier = Modifier.weight(1f)
                            )
                            ThemeChip(
                                label = "Claro",
                                icon = Icons.Default.LightMode,
                                selected = !isDarkMode,
                                onClick = {
                                    ThemeDebug.logDarkModeToggle(false)
                                    onDarkModeChange(false)
                                },
                                modifier = Modifier.weight(1f)
                            )
                            ThemeChip(
                                label = "Oscuro",
                                icon = Icons.Default.DarkMode,
                                selected = isDarkMode,
                                onClick = {
                                    ThemeDebug.logDarkModeToggle(true)
                                    onDarkModeChange(true)
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // ── AMOLED ────────────────────────────────────────
            item(key = "amoled") {
                SettingsCard(position = SettingsCardPosition.Middle) {
                    SettingsListItem(
                        headline = "Modo AMOLED",
                        supporting = if (isDarkMode)
                            "Fondo negro puro para pantallas OLED"
                        else
                            "Activa el modo oscuro para usar esta opción",
                        trailingSwitch = true,
                        checked = isAmoled && isDarkMode,
                        onCheckedChange = { checked ->
                            if (isDarkMode) onAmoledChange(checked)
                        }
                    )
                }
            }

            // ── Typography ────────────────────────────────────
            item(key = "typography") {
                SettingsCard(position = SettingsCardPosition.Middle) {
                    SettingsListItem(
                        headline = "Usar Google Sans",
                        supporting = "Aplica la fuente en la aplicación",
                        trailingSwitch = true,
                        checked = useCustomFont,
                        onCheckedChange = { checked ->
                            onUseCustomFontChange(checked)
                        }
                    )
                }
            }

            // ── Dynamic Colors ────────────────────────────────
            item(key = "dynamic_colors") {
                SettingsCard(position = SettingsCardPosition.Middle) {
                    SettingsListItem(
                        headline = "Color dinámico",
                        supporting = "Adapta el tema a tu fondo (Android 12+)",
                        trailingSwitch = true,
                        checked = isDynamic,
                        onCheckedChange = { checked ->
                            val newPalette = if (checked) ColorPalette.Dynamic else ColorPalette.Blue
                            ThemeDebug.logPaletteSelection(
                                oldPalette = currentPalette,
                                newPalette = newPalette
                            )
                            onPaletteChange(newPalette)
                        }
                    )
                }
            }

            // ── Basic Colors ──────────────────────────────────
            item(key = "basic_colors") {
                SettingsCard(position = SettingsCardPosition.Last) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Colores básicos",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Elige un tono sólido de Material Design 3",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))

                        val basicPalettes = remember {
                            ColorPalette.values().filter { it != ColorPalette.Dynamic }
                        }

                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            items(basicPalettes) { palette ->
                                val selected = palette == currentPalette
                                PalettePreview(
                                    palette = palette,
                                    isSelected = selected,
                                    onClick = {
                                        ThemeDebug.logPaletteSelection(
                                            oldPalette = currentPalette,
                                            newPalette = palette
                                        )
                                        onPaletteChange(palette)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
