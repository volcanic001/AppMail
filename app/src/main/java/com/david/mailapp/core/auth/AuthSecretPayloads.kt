package com.david.mailapp.core.auth

import kotlinx.serialization.Serializable

/**
 * Serializable DTO for OAuth2 tokens — schema v1 (legacy, uses [expiresIn]).
 * Migrated to [OAuthTokensPayloadV2] on first read.
 */
@Serializable
internal data class OAuthTokensPayloadV1(
    val schemaVersion: Int = 1,
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Int
)

/**
 * Serializable DTO for OAuth2 tokens — schema v2 (current, uses [expiresAtEpochMillis]).
 */
@Serializable
internal data class OAuthTokensPayloadV2(
    val schemaVersion: Int = 2,
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochMillis: Long
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
