package com.david.mailapp.core.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Production implementation of [OAuthRefreshService] against the Google
 * OAuth2 token endpoint.
 *
 * Classification rules (closed set):
 * - HTTP 200 + non-empty access_token + expires_in > 0 → [OAuthRefreshResult.Success]
 * - HTTP 400 with error = "invalid_grant" → [OAuthRefreshResult.ReauthenticationRequired]
 * - Timeout, IOException, malformed response, other 4xx, 408, 429, 5xx → [OAuthRefreshResult.TransientFailure]
 * - [CancellationException] → always rethrown
 *
 * Never logs: URL parameters, refresh token, access token, or request body.
 */
class GoogleOAuthTokenService(
    private val client: HttpClient = HttpClient(CIO),
    private val json: Json = Json { ignoreUnknownKeys = true }
) : OAuthRefreshService {

    companion object {
        private const val TOKEN_URI = "https://oauth2.googleapis.com/token"
        private const val CLIENT_ID = "757677552095-mre7hbbkiaaq5utvatmpctbli9bcre91.apps.googleusercontent.com"
    }

    override suspend fun refresh(refreshToken: String): OAuthRefreshResult {
        return try {
            val response = client.submitForm(
                url = TOKEN_URI,
                formParameters = parameters {
                    append("client_id", CLIENT_ID)
                    append("refresh_token", refreshToken)
                    append("grant_type", "refresh_token")
                }
            )

            val statusCode = response.status.value
            val bodyText = response.bodyAsText()

            when {
                // HTTP 200 — possible success
                statusCode == 200 -> handleSuccess(bodyText)

                // HTTP 400 with invalid_grant → reauthentication required
                statusCode == 400 -> handleBadRequest(bodyText)

                // 408 (Request Timeout) and 429 (Too Many Requests) → transient
                statusCode == 408 || statusCode == 429 ->
                    OAuthRefreshResult.TransientFailure

                // Other 4xx → transient
                statusCode in 400..499 ->
                    OAuthRefreshResult.TransientFailure

                // 5xx → transient
                statusCode >= 500 ->
                    OAuthRefreshResult.TransientFailure

                // Unexpected
                else -> OAuthRefreshResult.TransientFailure
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // IOException, timeout, etc.
            OAuthRefreshResult.TransientFailure
        }
    }

    private fun handleSuccess(bodyText: String): OAuthRefreshResult {
        return try {
            val body = json.decodeFromString<RefreshTokenResponse>(bodyText)
            if (body.access_token.isNullOrEmpty() || (body.expires_in ?: 0) <= 0) {
                return OAuthRefreshResult.TransientFailure
            }
            OAuthRefreshResult.Success(
                accessToken = body.access_token!!,
                expiresInSeconds = body.expires_in!!
            )
        } catch (_: Exception) {
            // Malformed JSON
            OAuthRefreshResult.TransientFailure
        }
    }

    private fun handleBadRequest(bodyText: String): OAuthRefreshResult {
        return try {
            val body = json.decodeFromString<RefreshErrorResponse>(bodyText)
            if (body.error == "invalid_grant") {
                OAuthRefreshResult.ReauthenticationRequired
            } else {
                OAuthRefreshResult.TransientFailure
            }
        } catch (_: Exception) {
            OAuthRefreshResult.TransientFailure
        }
    }
}

@Serializable
private data class RefreshTokenResponse(
    val access_token: String? = null,
    val expires_in: Int? = null,
    val scope: String? = null,
    val token_type: String? = null
)

@Serializable
private data class RefreshErrorResponse(
    val error: String? = null,
    val error_description: String? = null
)
