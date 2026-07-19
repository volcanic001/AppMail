package com.david.mailapp.core.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "auth_tokens")

/**
 * OAuth2 token bundle stored in DataStore.
 * Persisted across app restarts; excluded from backup via data_extraction_rules.xml.
 */
data class OAuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Int = 3600
)

/**
 * Manages OAuth2 tokens via DataStore.
 *
 * Privacy: tokens are stored in app-private storage. Backup is disabled.
 * Encryption via Android Keystore is a future enhancement (security-crypto
 * is already in dependencies, ready to be plugged in).
 */
class AuthManager(private val context: Context) {

    private val store = context.dataStore

    companion object {
        private val KEY_ACCESS = stringPreferencesKey("access_token")
        private val KEY_REFRESH = stringPreferencesKey("refresh_token")
        private val KEY_EXPIRES = intPreferencesKey("expires_in")
    }

    suspend fun saveTokens(tokens: OAuthTokens) {
        store.edit { prefs ->
            prefs[KEY_ACCESS] = tokens.accessToken
            prefs[KEY_REFRESH] = tokens.refreshToken
            prefs[KEY_EXPIRES] = tokens.expiresIn
        }
    }

    suspend fun getTokens(): OAuthTokens? {
        val prefs = store.data.first()
        val access = prefs[KEY_ACCESS] ?: return null
        val refresh = prefs[KEY_REFRESH] ?: return null
        val expires = prefs[KEY_EXPIRES] ?: 3600
        return OAuthTokens(access, refresh, expires)
    }

    suspend fun getAccessToken(): String? = getTokens()?.accessToken

    suspend fun getRefreshToken(): String? = getTokens()?.refreshToken

    suspend fun isAuthenticated(): Boolean = getAccessToken() != null

    suspend fun clearTokens() {
        store.edit { it.clear() }
    }
}
