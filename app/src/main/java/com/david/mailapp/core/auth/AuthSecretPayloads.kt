package com.david.mailapp.core.auth

import kotlinx.serialization.Serializable

/**
 * Serializable DTO for OAuth2 tokens, persisted in DataStore as encrypted payload.
 */
@Serializable
internal data class OAuthTokensPayload(
    val schemaVersion: Int = 1,
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Int
)

/**
 * Serializable DTO for a pending OAuth PKCE session, persisted in DataStore
 * as encrypted payload.
 */
@Serializable
internal data class PendingOAuthSessionPayload(
    val schemaVersion: Int = 1,
    val state: String,
    val codeVerifier: String,
    val createdAtEpochMillis: Long
)
