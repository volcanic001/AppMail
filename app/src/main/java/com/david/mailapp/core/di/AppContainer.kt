package com.david.mailapp.core.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.david.mailapp.core.auth.AuthManager
import com.david.mailapp.core.auth.GmailAuthClient
import com.david.mailapp.core.network.HttpClientFactory
import com.david.mailapp.data.local.MailDatabase
import com.david.mailapp.data.pdf.PdfCacheManager
import com.david.mailapp.data.remote.provider.EmailProvider
import com.david.mailapp.data.repository.EmailRepository
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

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

    val googleOAuthTokenService: com.david.mailapp.core.auth.GoogleOAuthTokenService by lazy {
        com.david.mailapp.core.auth.GoogleOAuthTokenService()
    }

    val oauthTokenManager: com.david.mailapp.core.auth.OAuthTokenManager by lazy {
        com.david.mailapp.core.auth.OAuthTokenManager(authManager, googleOAuthTokenService)
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

    // ── Sign-out ──────────────────────────────────────────────

    /** Resultado de [signOut]. */
    sealed interface SignOutResult {
        /** Todo se eliminó correctamente — puede cambiar a login. */
        data object Success : SignOutResult

        /**
         * Falló la limpieza antes de borrar tokens.
         * El usuario sigue autenticado y puede reintentar.
         */
        data class Failed(val message: String) : SignOutResult
    }

    private val isSigningOut = AtomicBoolean(false)

    /**
     * Cierre de sesión completo con orden estricto:
     *
     * 1. Rechazar si ya hay un logout en curso (AtomicBoolean CAS).
     * 2. [deactivateProvider] — cerrar HTTP y detener Gmail.
     * 3. [database.clearAllTables] — limpiar Room (IO).
     * 4. [pdfCacheManager.clearAll] — eliminar caché PDF temporal.
     * 5. [searchHistoryStore.edit] { it.clear() } — historial de búsqueda.
     * 6. [authClient.signOut] — **último**: borrar tokens cifrados + PKCE.
     * 7. Retornar [SignOutResult.Success].
     *
     * Si falla antes del paso 6, se reactiva el provider y se retorna
     * [SignOutResult.Failed] para que el usuario pueda reintentar.
     */
    suspend fun signOut(): SignOutResult {
        if (!isSigningOut.compareAndSet(false, true)) {
            return SignOutResult.Failed("Ya hay un cierre de sesión en curso.")
        }

        val hadProvider = _provider != null

        try {
            // 2. Cerrar HTTP y detener operaciones Gmail
            deactivateProvider()

            // 3. Limpiar Room (fuera del hilo principal)
            withContext(Dispatchers.IO) {
                database.clearAllTables()
            }

            // 4. Limpiar caché PDF temporal (no lanza excepción)
            pdfCacheManager.clearAll()

            // 5. Limpiar historial de búsqueda
            searchHistoryStore.edit { it.clear() }

            // 6. Borrar tokens y PKCE (COMMIT — último paso)
            authClient.signOut()

            return SignOutResult.Success
        } catch (e: Exception) {
            // Rollback: reactivar provider si estaba activo
            if (hadProvider) {
                activateProvider()
            }
            return SignOutResult.Failed("No se pudo cerrar sesión. Inténtalo nuevamente.")
        } finally {
            isSigningOut.set(false)
        }
    }
}
