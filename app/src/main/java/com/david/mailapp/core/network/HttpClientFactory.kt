package com.david.mailapp.core.network

import com.david.mailapp.core.auth.AuthManager
import com.david.mailapp.core.auth.GmailAuthClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object HttpClientFactory {

    /**
     * Gmail API client with automatic Bearer token handling.
     *
     * - [loadTokens]: reads stored access + refresh tokens from [AuthManager]
     * - [refreshTokens]: calls [GmailAuthClient.refreshAccessToken] on 401,
     *   which hits oauth2.googleapis.com/token with the refresh_token.
     */
    fun createGmailClient(
        authManager: AuthManager,
        authClient: GmailAuthClient
    ): HttpClient {
        return HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    prettyPrint = false
                })
            }

            install(Auth) {
                bearer {
                    loadTokens {
                        val tokens = authManager.getTokens()
                        if (tokens != null) {
                            BearerTokens(tokens.accessToken, tokens.refreshToken)
                        } else null
                    }

                    refreshTokens {
                        val newAccess = authClient.refreshAccessToken()
                        if (newAccess != null) {
                            val tokens = authManager.getTokens()
                            BearerTokens(newAccess, tokens?.refreshToken ?: "")
                        } else null
                    }
                }
            }

            install(Logging) {
                level = LogLevel.HEADERS
            }

            defaultRequest {
                url("https://gmail.googleapis.com/gmail/v1/")
                contentType(ContentType.Application.Json)
            }
        }
    }
}
