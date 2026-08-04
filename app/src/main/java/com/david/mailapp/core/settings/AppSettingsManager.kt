package com.david.mailapp.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.david.mailapp.ui.theme.ColorPalette
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

class AppSettingsManager(private val context: Context) {

    private val store = context.settingsDataStore

    companion object {
        private val PALETTE_KEY = stringPreferencesKey("color_palette")
        private val IS_DARK_MODE_KEY = booleanPreferencesKey("is_dark_mode")
        private val USE_CUSTOM_FONT_KEY = booleanPreferencesKey("use_custom_font")
        private val IS_AMOLED_KEY = booleanPreferencesKey("is_amoled")
        private val SHOW_EMAIL_DIVIDERS_KEY = booleanPreferencesKey("show_email_dividers")
    }

    val paletteFlow: Flow<ColorPalette?> = store.data.map { prefs ->
        val name = prefs[PALETTE_KEY] ?: return@map null
        try {
            ColorPalette.valueOf(name)
        } catch (e: Exception) {
            null
        }
    }

    val isDarkModeFlow: Flow<Boolean?> = store.data.map { prefs ->
        prefs[IS_DARK_MODE_KEY]
    }

    val useCustomFontFlow: Flow<Boolean?> = store.data.map { prefs ->
        prefs[USE_CUSTOM_FONT_KEY]
    }

    val isAmoledFlow: Flow<Boolean?> = store.data.map { prefs ->
        prefs[IS_AMOLED_KEY]
    }

    val showEmailDividersFlow: Flow<Boolean?> = store.data.map { prefs ->
        prefs[SHOW_EMAIL_DIVIDERS_KEY]
    }

    suspend fun setPalette(palette: ColorPalette) {
        store.edit { prefs ->
            prefs[PALETTE_KEY] = palette.name
        }
    }

    suspend fun setDarkMode(isDark: Boolean) {
        store.edit { prefs ->
            prefs[IS_DARK_MODE_KEY] = isDark
        }
    }

    suspend fun setUseCustomFont(useCustomFont: Boolean) {
        store.edit { prefs ->
            prefs[USE_CUSTOM_FONT_KEY] = useCustomFont
        }
    }

    suspend fun setAmoled(isAmoled: Boolean) {
        store.edit { prefs ->
            prefs[IS_AMOLED_KEY] = isAmoled
        }
    }

    suspend fun setShowEmailDividers(show: Boolean) {
        store.edit { prefs ->
            prefs[SHOW_EMAIL_DIVIDERS_KEY] = show
        }
    }
}
