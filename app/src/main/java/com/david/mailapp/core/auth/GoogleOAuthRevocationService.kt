package com.david.mailapp.core.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.parameters
import io.ktor.utils.io.discard

internal class GoogleOAuthRevocationService(
    private val httpClient: HttpClient = HttpClient(CIO)
) : OAuthRevocationService {

    override suspend fun revoke(refreshToken: String) {
        if (refreshToken.isBlank()) {
            throw IllegalArgumentException("Refresh token cannot be blank or empty")
        }

        val response = httpClient.submitForm(
            url = REVOCATION_ENDPOINT,
            formParameters = parameters {
                append("token", refreshToken)
            },
            encodeInQuery = false
        )
        response.bodyAsChannel().discard()
    }

    private companion object {
        private const val REVOCATION_ENDPOINT = "https://oauth2.googleapis.com/revoke"
    }
}
