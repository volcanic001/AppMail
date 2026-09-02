package com.david.mailapp.core.network

import com.david.mailapp.BuildConfig
import com.david.mailapp.core.auth.OAuthTokenManager
import com.david.mailapp.core.auth.OAuthTokenResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.authProvider
import io.ktor.client.plugins.auth.providers.BearerAuthProvider
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.URLProtocol
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/** Thrown to abort a request before it reaches the network when the session is invalid. */
class OAuthSessionExpiredException(message: String) : IllegalStateException(message)

object HttpClientFactory {

    private const val GMAIL_HOST = "gmail.googleapis.com"

    /**
     * Returns true only for requests that are both HTTPS and targeting the exact
     * Gmail API host. Used as the single security predicate across the freshness
     * plugin, [sendWithoutRequest], and [refreshTokens].
     */
    private fun isTrustedGmailRequest(host: String, protocol: URLProtocol): Boolean =
        host == GMAIL_HOST && protocol == URLProtocol.HTTPS

    fun createGmailClient(
        tokenManager: OAuthTokenManager,
        engine: HttpClientEngine = CIO.create(),
        networkLogger: Logger? = null
    ): HttpClient {

        val freshnessPlugin = createClientPlugin("OAuthFreshnessPlugin") {
            val mutex = Mutex()
            onRequest { request, _ ->
                if (isTrustedGmailRequest(request.url.host, request.url.protocol)) {
                    mutex.withLock {
                        when (val r = tokenManager.ensureFreshToken()) {
                            is OAuthTokenResult.Available -> {
                                if (r.refreshed) {
                                    this@createClientPlugin.client
                                        .authProvider<BearerAuthProvider>()
                                        ?.clearToken()
                                    tokenManager.traceLifecycle(
                                        "bearer_cache_cleared trigger=proactive"
                                    )
                                    tokenManager.traceLifecycle(
                                        "gmail_request_continues_after_refresh trigger=proactive"
                                    )
                                }
                            }
                            is OAuthTokenResult.TemporarilyUnavailable,
                            is OAuthTokenResult.ReauthenticationRequired,
                            is OAuthTokenResult.NoSession ->
                                throw OAuthSessionExpiredException("Cannot proceed: ${r::class.simpleName}")
                        }
                    }
                }
            }
        }

        return HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    prettyPrint = false
                })
            }

            install(io.ktor.client.plugins.compression.ContentEncoding) {
                gzip()
            }

            defaultRequest {
                url("https://$GMAIL_HOST/gmail/v1/")
                contentType(ContentType.Application.Json)
                header(HttpHeaders.UserAgent, "MailApp-Android/${BuildConfig.VERSION_NAME} (gzip)")
            }

            install(freshnessPlugin)

            install(Auth) {
                bearer {
                    sendWithoutRequest { req ->
                        isTrustedGmailRequest(req.url.host, req.url.protocol)
                    }

                    loadTokens {
                        tokenManager.loadTokens()
                            ?.let { BearerTokens(it.accessToken, it.refreshToken) }
                    }

                    refreshTokens {
                        val req = response.call.request
                        if (isTrustedGmailRequest(req.url.host, req.url.protocol)) {
                            val r = tokenManager.forceRefresh(oldTokens?.accessToken)
                            if (r is OAuthTokenResult.Available) {
                                tokenManager.traceLifecycle(
                                    "gmail_retry_authorized trigger=http_401 " +
                                        "token_source=${if (r.refreshed) "renewed" else "concurrent_refresh"}"
                                )
                                BearerTokens(r.tokens.accessToken, r.tokens.refreshToken)
                            } else null
                        } else null
                    }
                }
            }

            install(Logging) {
                if (networkLogger != null) {
                    logger = networkLogger
                }
                level = if (BuildConfig.DEBUG) LogLevel.HEADERS else LogLevel.NONE
                sanitizeHeader { header -> header == HttpHeaders.Authorization }
            }
        }
    }
}
