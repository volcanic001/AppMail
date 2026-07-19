package com.david.mailapp.core.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.david.mailapp.core.auth.AuthManager
import com.david.mailapp.core.auth.GmailAuthClient
import com.david.mailapp.core.network.HttpClientFactory
import com.david.mailapp.data.local.MailDatabase
import com.david.mailapp.data.pdf.PdfCacheManager
import com.david.mailapp.data.remote.provider.EmailProvider
import com.david.mailapp.data.repository.EmailRepository
import io.ktor.client.HttpClient

/** DataStore for search history — singleton scoped to this file. */
private val Context.searchHistoryStore: DataStore<Preferences> by preferencesDataStore(name = "search_history")

/**
 * Manual dependency injection container.
 *
 * Lifecycle:
 * 1. App starts → [init] called → DB + AuthManager ready
 * 2. User signs in → [activateProvider] → GmailProvider created
 * 3. User signs out → [deactivateProvider] → provider destroyed
 */
object AppContainer {

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    // ── Always-available singletons ─────────────────────────────

    val database: MailDatabase by lazy {
        MailDatabase.create(appContext)
    }

    val authManager: AuthManager by lazy {
        AuthManager(appContext)
    }

    val appSettingsManager: com.david.mailapp.core.settings.AppSettingsManager by lazy {
        com.david.mailapp.core.settings.AppSettingsManager(appContext)
    }

    val authClient: GmailAuthClient by lazy {
        GmailAuthClient(authManager)
    }

    /** DataStore for search history (last 5 queries). */
    val searchHistoryStore: DataStore<Preferences> by lazy {
        appContext.searchHistoryStore
    }

    // ── PDF cache ───────────────────────────────────────────────

    val pdfCacheManager: PdfCacheManager by lazy {
        PdfCacheManager(appContext.cacheDir)
    }

    // ── Provider (lazy, activated after sign-in) ────────────────

    private var _provider: EmailProvider? = null
    val provider: EmailProvider?
        get() = _provider

    private var _httpClient: HttpClient? = null

    /** Call after successful OAuth2 sign-in to activate the email provider. */
    fun activateProvider() {
        _httpClient = HttpClientFactory.createGmailClient(authManager, authClient)
        _provider = com.david.mailapp.data.remote.provider.gmail.GmailProvider(_httpClient!!)
    }

    /** Call on sign-out to tear down the provider and HTTP client. */
    fun deactivateProvider() {
        _provider = null
        _httpClient?.close()
        _httpClient = null
    }

    // ── Repository ──────────────────────────────────────────────

    val emailRepository: EmailRepository by lazy {
        EmailRepository(database, { provider }, pdfCacheManager)
    }
}
