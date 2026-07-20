package com.david.mailapp.core.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * Unit tests for [OAuthSecurity] — pure JVM logic, no Android dependencies.
 *
 * Covers the RFC 7636 PKCE vector, random generation properties,
 * and constant-time comparison.
 */
class OAuthSecurityTest {

    // ── State generation ───────────────────────────────────────

    @Test
    fun `consecutive states are different`() {
        val state1 = OAuthSecurity.generateState()
        val state2 = OAuthSecurity.generateState()
        assertNotEquals("Each call must produce a unique state", state1, state2)
    }

    @Test
    fun `state contains only Base64 URL-safe characters`() {
        val state = OAuthSecurity.generateState()
        // Base64 URL-safe charset: A-Z, a-z, 0-9, '-', '_' (no padding, no newlines)
        assertTrue("State must be non-empty", state.isNotEmpty())
        assertTrue(
            "State must only contain URL-safe Base64 characters",
            state.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '-' || it == '_' }
        )
    }

    // ── Code verifier ──────────────────────────────────────────

    @Test
    fun `code verifier meets PKCE length requirements`() {
        val verifier = OAuthSecurity.generateCodeVerifier()
        // RFC 7636 §4.1: code_verifier must be 43–128 characters
        assertTrue(
            "Verifier length must be at least 43 chars, got ${verifier.length}",
            verifier.length >= 43
        )
        assertTrue(
            "Verifier length must be at most 128 chars, got ${verifier.length}",
            verifier.length <= 128
        )
    }

    // ── Code challenge (RFC 7636 test vector) ──────────────────

    @Test
    fun `challenge matches RFC 7636 test vector`() {
        // From RFC 7636 Appendix B:
        // code_verifier  = dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk
        // code_challenge = E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        val expectedChallenge = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"
        val actualChallenge = OAuthSecurity.deriveCodeChallenge(verifier)
        assertEquals("RFC 7636 test vector mismatch", expectedChallenge, actualChallenge)
    }

    // ── Constant-time comparison ───────────────────────────────

    @Test
    fun `constantTimeEquals returns true for identical strings`() {
        assertTrue(OAuthSecurity.constantTimeEquals("abc123", "abc123"))
    }

    @Test
    fun `constantTimeEquals returns false for different strings`() {
        assertFalse(OAuthSecurity.constantTimeEquals("abc123", "abc124"))
    }

    @Test
    fun `constantTimeEquals returns false for different lengths`() {
        assertFalse(OAuthSecurity.constantTimeEquals("abc", "abcd"))
    }

    // ── Edge cases ─────────────────────────────────────────────

    @Test
    fun `state can be decoded from Base64`() {
        val state = OAuthSecurity.generateState()
        // 32 bytes → 43 chars in Base64 URL-safe (no padding)
        val decoded = Base64.getUrlDecoder().decode(state)
        assertEquals("State must decode to 32 bytes", 32, decoded.size)
    }
}
