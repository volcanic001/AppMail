package com.david.mailapp.core.auth

/**
 * Ephemeral OAuth2 session stored in DataStore during the authorization flow.
 * Survives process death so PKCE can complete even if Android kills the app
 * while Chrome Custom Tabs is open.
 *
 * [state] — cryptographic random, validated on redirect to prevent CSRF.
 * [codeVerifier] — PKCE verifier, used to derive [deriveCodeChallenge] and
 *   later exchanged for the authorization code.
 * [createdAtEpochMillis] — wall clock at generation; checked against a 10-minute
 *   TTL inside [AuthManager.consumePendingOAuthSession].
 */
internal data class PendingOAuthSession(
    val state: String,
    val codeVerifier: String,
    val createdAtEpochMillis: Long
)

/**
 * Atomic result of consuming a [PendingOAuthSession].
 *
 * - [Valid]: state matched, session consumed — proceed with code exchange.
 * - [Missing]: no session existed in the store (never started or already consumed).
 * - [MissingState]: reserved for when the redirect URI lacks a state parameter
 *   (detected before calling consume).
 * - [StateMismatch]: session existed but the received state does not match.
 *   The legitimate session is preserved (not consumed) so a replay with the
 *   correct state can still succeed.
 * - [Expired]: session existed and state matched, but the 10-minute TTL has
 *   elapsed (or the timestamp was in the future). Session is consumed (cleared).
 */
internal sealed interface PendingOAuthSessionResult {
    data class Valid(val session: PendingOAuthSession) : PendingOAuthSessionResult
    data object Missing : PendingOAuthSessionResult
    data object MissingState : PendingOAuthSessionResult
    data object StateMismatch : PendingOAuthSessionResult
    data object Expired : PendingOAuthSessionResult
}
