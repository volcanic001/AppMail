package com.david.mailapp.core.auth

internal fun interface OAuthRevocationService {
    suspend fun revoke(refreshToken: String)
}
