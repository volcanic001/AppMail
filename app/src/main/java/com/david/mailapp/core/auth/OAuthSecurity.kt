package com.david.mailapp.core.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Pure JVM helpers for OAuth2 PKCE and CSRF protection.
 *
 * All methods are stateless and safe to call from any thread.
 */
internal object OAuthSecurity {

    /**
     * Generate a cryptographic random state parameter (32 bytes, Base64 URL-safe).
     * Used to bind the authorization request to the redirect and prevent CSRF.
     */
    fun generateState(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return base64UrlNoPadding(bytes)
    }

    /**
     * Generate a PKCE code_verifier (64 bytes, Base64 URL-safe).
     * RFC 7636 requires 43–128 characters; 64 bytes → 86 chars after Base64.
     */
    fun generateCodeVerifier(): String {
        val bytes = ByteArray(64)
        SecureRandom().nextBytes(bytes)
        return base64UrlNoPadding(bytes)
    }

    /**
     * Derive a PKCE code_challenge from [verifier] using SHA-256.
     * Returns Base64 URL-safe without padding, per RFC 7636 §4.2.
     */
    fun deriveCodeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray())
        return base64UrlNoPadding(digest)
    }

    /**
     * Constant-time comparison of two strings.
     * Uses [MessageDigest.isEqual] on the UTF-8 byte arrays to prevent
     * timing side-channel attacks on the state parameter.
     */
    fun constantTimeEquals(expected: String, received: String): Boolean {
        return MessageDigest.isEqual(expected.toByteArray(), received.toByteArray())
    }

    private fun base64UrlNoPadding(bytes: ByteArray): String {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
