package com.david.mailapp.core.di

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.david.mailapp.BuildConfig
import com.david.mailapp.core.auth.AuthManager
import com.david.mailapp.core.auth.GmailAuthClient
import com.david.mailapp.core.auth.GoogleOAuthRevocationService
import com.david.mailapp.core.auth.OAuthRevocationService
import com.david.mailapp.core.localization.AndroidStringProvider
import com.david.mailapp.core.localization.StringProvider
import com.david.mailapp.core.network.HttpClientFactory
import com.david.mailapp.data.local.MailDatabase
import com.david.mailapp.data.pdf.PdfCacheManager
import com.david.mailapp.core.session.SessionWriteGuard
import com.david.mailapp.core.session.SessionWriteGuardImpl
import com.david.mailapp.data.remote.provider.EmailProvider
import com.david.mailapp.data.repository.EmailRepository
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

/** DataStore for search history — singleton scoped to this file. */
private val Context.searchHistoryStore: DataStore<Preferences> by preferencesDataStore(name = "search_history")

object AppContainer {

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    // ── Application-level scope (outlives any Activity) ────────────
    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** True when the UI must show the session-expired message and return to Login. */
    val sessionExpiredSignal: MutableStateFlow<Boolean> = MutableStateFlow(false)

    // ── Always-available singletons ──────────────────────────────────

    val database: MailDatabase by lazy {
        MailDatabase.create(appContext)
    }

    val authManager: AuthManager by lazy {
        AuthManager(appContext)
    }

    internal val googleOAuthRevocationService: OAuthRevocationService by lazy {
        GoogleOAuthRevocationService()
    }

    val googleOAuthTokenService: com.david.mailapp.core.auth.GoogleOAuthTokenService by lazy {
        com.david.mailapp.core.auth.GoogleOAuthTokenService()
    }

    val oauthTokenManager: com.david.mailapp.core.auth.OAuthTokenManager by lazy {
        com.david.mailapp.core.auth.OAuthTokenManager(
            authManager = authManager,
            refreshService = googleOAuthTokenService,
            lifecycleLogger = { event ->
                if (BuildConfig.DEBUG) {
                    Log.d("OAuthLifecycle", event)
                }
            }
        )
    }

    val appSettingsManager: com.david.mailapp.core.settings.AppSettingsManager by lazy {
        com.david.mailapp.core.settings.AppSettingsManager(appContext)
    }

    val authClient: GmailAuthClient by lazy {
        GmailAuthClient(authManager, oauthTokenManager)
    }

    /** DataStore for search history (last 5 queries). */
    val searchHistoryStore: DataStore<Preferences> by lazy {
        appContext.searchHistoryStore
    }

    // ── PDF cache ──────────────────────────────────────────────────

    val pdfCacheManager: PdfCacheManager by lazy {
        PdfCacheManager(appContext.cacheDir)
    }

    // ── Session Guard ──────────────────────────────────────────────

    val sessionWriteGuard: SessionWriteGuard by lazy {
        SessionWriteGuardImpl()
    }

    // ── Localization ──────────────────────────────────────────

    val stringProvider: StringProvider by lazy {
        AndroidStringProvider(appContext)
    }

    // ── Provider (activated after sign-in, coordinated lifecycle) ──────

    internal val providerCoordinator: ProviderLifecycleCoordinator<com.david.mailapp.data.remote.provider.EmailProvider> by lazy {
        ProviderLifecycleCoordinator(
            isReauthPending = { oauthTokenManager.isReauthenticationPending },
            createClient = { HttpClientFactory.createGmailClient(oauthTokenManager) },
            createProvider = { client ->
                com.david.mailapp.data.remote.provider.gmail.GmailProvider(client)
            }
        )
    }

    val provider: EmailProvider?
        get() = providerCoordinator.provider

    /** Call after successful OAuth2 sign-in to activate the email provider. */
    suspend fun activateProvider() {
        providerCoordinator.activateProvider()
        if (providerCoordinator.provider != null) {
            sessionWriteGuard.activate()
        }
    }

    /** Call on sign-out to tear down the provider and HTTP client. */
    suspend fun deactivateProvider() {
        providerCoordinator.deactivateProvider()
    }

    // ── Repository ────────────────────────────────────────────────

    val emailRepository: EmailRepository by lazy {
        EmailRepository(database, { provider }, pdfCacheManager, sessionWriteGuard)
    }

    // ── Session termination coordinator ────────────────────────────

    private val sessionCoordinator: SessionCoordinator by lazy {
        SessionCoordinator(
            clearProvider = ::deactivateProvider,
            clearDatabase = { withContext(Dispatchers.IO) { database.clearAllTables() } },
            clearPdfCache = { pdfCacheManager.clearAll() },
            clearSearchHistory = { searchHistoryStore.edit { it.clear() } },
            clearCredentials = { authClient.signOut() },
            isAuthenticated = { authManager.isAuthenticated() },
            reactivateProvider = ::activateProvider,
            writeGuard = sessionWriteGuard,
            setPendingPdfCleanup = { pending -> authManager.setPendingPdfCleanup(pending) },
            readRefreshToken = { authManager.getRefreshToken() },
            revocationService = googleOAuthRevocationService
        )
    }

    /** Resultado de [signOut]. */
    sealed interface SignOutResult {
        data object Success : SignOutResult
        data class Failed(val message: String) : SignOutResult
    }

    /**
     * Manual logout: ordered cleanup with rollback on pre-commit failure.
     */
    suspend fun signOut(): SignOutResult {
        return when (val r = sessionCoordinator.signOut()) {
            is SessionCoordinator.SignOutResult.Success -> SignOutResult.Success
            is SessionCoordinator.SignOutResult.Failed -> SignOutResult.Failed(r.message)
        }
    }

    /**
     * Automatic invalidation after invalid_grant. Fail-closed; never reactivates provider.
     *
     * Returns [SessionCoordinator.InvalidationResult.Completed] when the cleanup ran, or
     * [SessionCoordinator.InvalidationResult.AlreadySignedOut] when the manual logout had
     * already cleared the session (UI must not show the expiry message in that case).
     */
    suspend fun invalidateExpiredSession(): SessionCoordinator.InvalidationResult {
        return sessionCoordinator.invalidateExpiredSession()
    }
}
