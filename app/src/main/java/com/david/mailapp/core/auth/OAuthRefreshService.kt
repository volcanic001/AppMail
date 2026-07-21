package com.david.mailapp.core.auth

/**
 * Contract for OAuth2 token refresh.
 *
 * Implementations hit the token endpoint with a refresh token and classify
 * the response into one of three sealed outcomes. The caller
 * ([OAuthTokenManager]) decides what to do based on the result.
 */
interface OAuthRefreshService {

    /**
     * Attempt to refresh the access token.
     *
     * @param refreshToken The stored refresh token (never empty at this point).
     * @return A classified result. [CancellationException] is always rethrown.
     */
    suspend fun refresh(refreshToken: String): OAuthRefreshResult
}

/** Classified result of an OAuth token refresh attempt. */
sealed interface OAuthRefreshResult {

    /**
     * The endpoint returned a valid new access token.
     *
     * @param accessToken  The new access token (non-empty).
     * @param expiresInSeconds  Seconds until the new token expires (> 0).
     */
    data class Success(
        val accessToken: String,
        val expiresInSeconds: Int
    ) : OAuthRefreshResult

    /**
     * A transient error occurred (network, server error, malformed response).
     * The caller may retry later.
     */
    data object TransientFailure : OAuthRefreshResult

    /**
     * The refresh token is invalid or revoked. The user must re-authenticate.
     */
    data object ReauthenticationRequired : OAuthRefreshResult
}
