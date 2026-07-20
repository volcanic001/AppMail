package com.david.mailapp.core.auth

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Result of attempting to launch the OAuth authorization flow. */
sealed interface OAuthLaunchResult {
    data object Launched : OAuthLaunchResult
    data object NoBrowserAvailable : OAuthLaunchResult
    data object Failed : OAuthLaunchResult
}

/** Result of processing an OAuth redirect URI. */
sealed interface OAuthRedirectResult {
    data object Success : OAuthRedirectResult
    data object NotOAuthRedirect : OAuthRedirectResult
    data object UserCancelled : OAuthRedirectResult
    data object InvalidSession : OAuthRedirectResult
    data object ExpiredSession : OAuthRedirectResult
    data object MissingAuthorizationCode : OAuthRedirectResult
    data object TokenExchangeFailed : OAuthRedirectResult
}

/**
 * OAuth2 authentication for Gmail via Chrome Custom Tabs + PKCE.
 *
 * Flow:
 * 1. [launchAuth] generates state + PKCE verifier, persists the session, opens Custom Tab
 * 2. User grants permission → Google redirects to com.david.mailapp:/oauth2redirect
 * 3. [handleOAuthRedirect] validates the redirect, consumes the session, exchanges the code
 * 4. Tokens are persisted via [AuthManager]
 */
class GmailAuthClient(
    private val authManager: AuthManager
) {
    private val json = Json { ignoreUnknownKeys = true }

    // ── OAuth2 config ──────────────────────────────────────────

    companion object {
        const val CLIENT_ID = "757677552095-mre7hbbkiaaq5utvatmpctbli9bcre91.apps.googleusercontent.com"
        const val REDIRECT_URI = "com.david.mailapp:/oauth2redirect"
        const val AUTH_URI = "https://accounts.google.com/o/oauth2/auth"
        const val TOKEN_URI = "https://oauth2.googleapis.com/token"
        const val SCOPE = "https://mail.google.com/"
        private const val TAG = "GmailAuthClient"
    }

    // ── Launch authorization flow ──────────────────────────────

    /**
     * Generate state + PKCE verifier, persist the session, and open Chrome Custom Tab.
     * If the tab cannot be opened, the pending session is cleaned up.
     */
    suspend fun launchAuth(context: Context): OAuthLaunchResult {
        val state = OAuthSecurity.generateState()
        val codeVerifier = OAuthSecurity.generateCodeVerifier()
        val codeChallenge = OAuthSecurity.deriveCodeChallenge(codeVerifier)

        val session = PendingOAuthSession(
            state = state,
            codeVerifier = codeVerifier,
            createdAtEpochMillis = System.currentTimeMillis()
        )
        try {
            authManager.savePendingOAuthSession(session)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Unable to persist pending OAuth session: ${e::class.simpleName}")
            return OAuthLaunchResult.Failed
        }

        val authUrl = Uri.parse(AUTH_URI).buildUpon()
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", SCOPE)
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge", codeChallenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("access_type", "offline")
            .appendQueryParameter("prompt", "consent")
            .build()

        return try {
            CustomTabsIntent.Builder()
                .build()
                .launchUrl(context, authUrl)
            OAuthLaunchResult.Launched
        } catch (e: Exception) {
            Log.w(TAG, "OAuth Custom Tab launch failed: ${e::class.simpleName}")
            authManager.clearPendingOAuthSession()
            when {
                e.message?.contains("NoActivityResolvedException", ignoreCase = true) == true ||
                    e.message?.contains("ActivityNotFoundException", ignoreCase = true) == true ->
                    OAuthLaunchResult.NoBrowserAvailable
                else -> OAuthLaunchResult.Failed
            }
        }
    }

    // ── Handle redirect ────────────────────────────────────────

    /**
     * Validate the redirect URI, consume the pending session, and exchange
     * the authorization code for tokens.
     *
     * Strict validation order:
     * 1. Hierarchical URI
     * 2. Scheme "com.david.mailapp"
     * 3. Path "/oauth2redirect"
     * 4. Exactly one "state" parameter
     * 5. Consume the session (atomic read + delete)
     * 6. If state is valid, check for OAuth error
     * 7. Exactly one non-empty "code" parameter
     * 8. Exchange code → persist tokens → Success
     */
    suspend fun handleOAuthRedirect(
        uri: Uri,
        nowEpochMillis: Long = System.currentTimeMillis()
    ): OAuthRedirectResult {
        // 1. Scheme — works on both hierarchical and opaque URIs
        if (uri.scheme != "com.david.mailapp") {
            return OAuthRedirectResult.NotOAuthRedirect
        }

        // 2. Path — works on opaque URIs (e.g. "com.david.mailapp:/oauth2redirect")
        if (uri.path != "/oauth2redirect") {
            return OAuthRedirectResult.NotOAuthRedirect
        }

        // 3. Exactly one state parameter (parameter pollution protection)
        val stateParams = uri.getQueryParameters("state")
        if (stateParams.size != 1) {
            return OAuthRedirectResult.NotOAuthRedirect
        }

        // 4. Consume session atomically
        val sessionResult = authManager.consumePendingOAuthSession(stateParams[0], nowEpochMillis)

        return when (sessionResult) {
            is PendingOAuthSessionResult.Missing -> {
                OAuthRedirectResult.InvalidSession
            }
            is PendingOAuthSessionResult.MissingState -> {
                OAuthRedirectResult.InvalidSession
            }
            is PendingOAuthSessionResult.StateMismatch -> {
                OAuthRedirectResult.InvalidSession
            }
            is PendingOAuthSessionResult.Expired -> {
                OAuthRedirectResult.ExpiredSession
            }
            is PendingOAuthSessionResult.Valid -> {
                // 5. Check for OAuth error
                val errorParam = uri.getQueryParameter("error")
                if (errorParam != null) {
                    return when (errorParam) {
                        "access_denied" -> OAuthRedirectResult.UserCancelled
                        else -> OAuthRedirectResult.TokenExchangeFailed
                    }
                }

                // 6. Exactly one non-empty code
                val codeParams = uri.getQueryParameters("code")
                if (codeParams.size != 1 || codeParams[0].isEmpty()) {
                    return OAuthRedirectResult.MissingAuthorizationCode
                }

                // 7. Exchange code for tokens
                return try {
                    exchangeCodeForTokens(authCode = codeParams[0], codeVerifier = sessionResult.session.codeVerifier)
                    OAuthRedirectResult.Success
                } catch (e: Exception) {
                    Log.w(TAG, "token exchange failed: ${e::class.simpleName}")
                    OAuthRedirectResult.TokenExchangeFailed
                }
            }
        }
    }

    // ── Token exchange (private) ───────────────────────────────

    /**
     * Exchange authorization code for access + refresh tokens via PKCE.
     * Verifies [HttpStatusCode.OK] before deserializing.
     */
    private suspend fun exchangeCodeForTokens(authCode: String, codeVerifier: String): OAuthTokens {
        val client = HttpClient(CIO)
        return try {
            val response = client.submitForm(
                url = TOKEN_URI,
                formParameters = parameters {
                    append("client_id", CLIENT_ID)
                    append("code", authCode)
                    append("grant_type", "authorization_code")
                    append("redirect_uri", REDIRECT_URI)
                    append("code_verifier", codeVerifier)
                }
            )
            if (response.status != HttpStatusCode.OK) {
                throw IllegalStateException("Token exchange returned HTTP ${response.status}")
            }
            val bodyText = response.bodyAsText()
            val body = json.decodeFromString<TokenResponse>(bodyText)
            val tokens = OAuthTokens(
                accessToken = body.access_token,
                refreshToken = body.refresh_token ?: "",
                expiresIn = body.expires_in ?: 3600
            )
            authManager.saveTokens(tokens)
            tokens
        } finally {
            client.close()
        }
    }

    // ── Token lifecycle ────────────────────────────────────────

    /**
     * Refresh the access token using the stored refresh token.
     */
    suspend fun refreshAccessToken(): String? {
        val refreshToken = authManager.getRefreshToken() ?: return null

        val client = HttpClient(CIO)
        return try {
            val response = client.submitForm(
                url = TOKEN_URI,
                formParameters = parameters {
                    append("client_id", CLIENT_ID)
                    append("refresh_token", refreshToken)
                    append("grant_type", "refresh_token")
                }
            )
            if (response.status == HttpStatusCode.OK) {
                val bodyText = response.bodyAsText()
                val body = json.decodeFromString<TokenResponse>(bodyText)
                val newTokens = authManager.getTokens()?.copy(
                    accessToken = body.access_token,
                    expiresIn = body.expires_in ?: 3600
                )
                if (newTokens != null) {
                    authManager.saveTokens(newTokens)
                }
                body.access_token
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            client.close()
        }
    }

    suspend fun isSignedIn(): Boolean = authManager.isAuthenticated()

    suspend fun cancelPendingAuth() = authManager.clearPendingOAuthSession()

    suspend fun signOut() = authManager.clearTokens()
}

// ── Serialization DTOs ──────────────────────────────────────────

@Serializable
data class TokenResponse(
    val access_token: String,
    val expires_in: Int? = null,
    val refresh_token: String? = null,
    val scope: String? = null,
    val token_type: String? = null
)
