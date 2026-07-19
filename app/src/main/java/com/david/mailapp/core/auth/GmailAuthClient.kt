package com.david.mailapp.core.auth

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.browser.customtabs.CustomTabsIntent
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * OAuth2 authentication for Gmail via Chrome Custom Tabs + PKCE.
 *
 * Flow:
 * 1. Generate PKCE code_verifier + code_challenge
 * 2. Open Custom Tab with Google's OAuth2 authorize URL
 * 3. User grants permission → Google redirects to com.david.mailapp://oauth?code=...
 * 4. Activity receives the code → call [exchangeCodeForTokens]
 * 5. Tokens stored via [AuthManager]
 *
 * PKCE (Proof Key for Code Exchange) protects against authorization
 * code interception — no client_secret needed.
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
    }

    // ── PKCE state (held in memory for the auth session) ────────

    private var pendingCodeVerifier: String? = null

    /** Build the authorization URL and open it in Chrome Custom Tab. */
    fun launchAuth(context: Context) {
        val codeVerifier = generateCodeVerifier()
        pendingCodeVerifier = codeVerifier
        val codeChallenge = deriveCodeChallenge(codeVerifier)

        val authUrl = Uri.parse(AUTH_URI).buildUpon()
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", SCOPE)
            .appendQueryParameter("code_challenge", codeChallenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("access_type", "offline")     // get refresh_token
            .appendQueryParameter("prompt", "consent")          // force consent screen
            .build()

        CustomTabsIntent.Builder()
            .build()
            .launchUrl(context, authUrl)
    }

    /**
     * Exchange the authorization code for access + refresh tokens.
     * Called from Activity when it receives the redirect.
     */
    suspend fun exchangeCodeForTokens(authCode: String): OAuthTokens {
        val verifier = pendingCodeVerifier
            ?: throw IllegalStateException("No pending PKCE verifier — call launchAuth() first")
        pendingCodeVerifier = null

        val client = HttpClient(CIO)
        return try {
            val response: HttpResponse = client.submitForm(
                url = TOKEN_URI,
                formParameters = parameters {
                    append("client_id", CLIENT_ID)
                    append("code", authCode)
                    append("grant_type", "authorization_code")
                    append("redirect_uri", REDIRECT_URI)
                    append("code_verifier", verifier)
                }
            )
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

    /**
     * Refresh the access token using the stored refresh token.
     * Called automatically by the Ktor auth plugin on 401.
     */
    suspend fun refreshAccessToken(): String? {
        val refreshToken = authManager.getRefreshToken() ?: return null

        val client = HttpClient(CIO)
        return try {
            val response: HttpResponse = client.submitForm(
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

    suspend fun signOut() = authManager.clearTokens()

    // ── PKCE helpers ────────────────────────────────────────────

    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(64)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun deriveCodeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray())
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }
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
