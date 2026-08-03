package com.david.mailapp.core.auth

import kotlinx.coroutines.CancellationException

internal suspend fun runOAuthTokenExchange(
    exchange: suspend () -> Unit
): OAuthRedirectResult {
    return try {
        exchange()
        OAuthRedirectResult.Success
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        OAuthRedirectResult.TokenExchangeFailed
    }
}
