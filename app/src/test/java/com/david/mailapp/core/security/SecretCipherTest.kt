package com.david.mailapp.core.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Unit tests for the [SecretCipher] interface contract.
 *
 * Uses a real AES/GCM implementation (with an in-memory key, not AndroidKeyStore)
 * to validate the format, IV handling, and error cases specified in 1B.1.
 *
 * All tests respect the no-print rule: no keys, plaintext, or full ciphertext
 * is written to stdout/stderr.
 */
class SecretCipherTest {

    private lateinit var cipher: SecretCipher

    private val aadTokens = "mailapp.oauth.tokens.v1".toByteArray()
    private val aadSession = "mailapp.oauth.pending-session.v1".toByteArray()

    @Before
    fun setUp() {
        cipher = createTestSecretCipher()
    }

    // ── Round-trip ─────────────────────────────────────────────

    @Test
    fun `encrypt then decrypt returns original plaintext`() {
        val plaintext = "my-access-token-12345".toByteArray()

        val encrypted = cipher.encrypt(plaintext, aadTokens)
        val decrypted = cipher.decrypt(encrypted, aadTokens)

        assertArrayEquals("Round-trip must preserve plaintext", plaintext, decrypted)
    }

    @Test
    fun `round-trip with empty plaintext`() {
        val plaintext = ByteArray(0)

        val encrypted = cipher.encrypt(plaintext, aadTokens)
        val decrypted = cipher.decrypt(encrypted, aadTokens)

        assertArrayEquals("Empty plaintext round-trip must succeed", plaintext, decrypted)
    }

    @Test
    fun `round-trip with large plaintext`() {
        val plaintext = ByteArray(4096).apply { SecureRandom().nextBytes(this) }

        val encrypted = cipher.encrypt(plaintext, aadTokens)
        val decrypted = cipher.decrypt(encrypted, aadTokens)

        assertArrayEquals("Large plaintext round-trip must succeed", plaintext, decrypted)
    }

    @Test
    fun `round-trip with session AAD`() {
        val state = "abc123state".toByteArray()
        val codeVerifier = "def456verifier".toByteArray()

        val encrypted = cipher.encrypt(state, aadSession)
        val decrypted = cipher.decrypt(encrypted, aadSession)

        assertArrayEquals("State round-trip must preserve plaintext", state, decrypted)

        val encryptedVf = cipher.encrypt(codeVerifier, aadSession)
        val decryptedVf = cipher.decrypt(encryptedVf, aadSession)

        assertArrayEquals("Code verifier round-trip must preserve plaintext", codeVerifier, decryptedVf)
    }

    // ── Different IV per encryption ────────────────────────────

    @Test
    fun `same plaintext produces different ciphertext each time`() {
        val plaintext = "same-data".toByteArray()

        val encrypted1 = cipher.encrypt(plaintext, aadTokens)
        val encrypted2 = cipher.encrypt(plaintext, aadTokens)

        assertNotEquals(
            "Each encryption must produce a unique output due to random IV",
            encrypted1, encrypted2
        )
    }

    @Test
    fun `same plaintext with same AAD produces different ciphertext`() {
        val plaintext = "constant-payload".toByteArray()

        val results = (1..5).map { cipher.encrypt(plaintext, aadTokens) }

        // All 5 outputs must be unique
        results.forEach { first ->
            val count = results.count { it == first }
            assertTrue("All five encryptions of the same data must differ", count == 1)
        }
    }

    // ── Tampered ciphertext ────────────────────────────────────

    @Test(expected = SecretCipherException::class)
    fun `altered IV causes decrypt failure`() {
        val plaintext = "secret-token".toByteArray()
        val encrypted = cipher.encrypt(plaintext, aadTokens)

        // Flip a byte in the IV portion (between first and second colon)
        val parts = encrypted.split(":", limit = 3)
        val ivBytes = Base64.getDecoder().decode(parts[1])
        ivBytes[0] = (ivBytes[0].toInt() xor 0x01).toByte()
        val tamperedIv = Base64.getEncoder().encodeToString(ivBytes)
        val tampered = "${parts[0]}:$tamperedIv:${parts[2]}"

        cipher.decrypt(tampered, aadTokens)
    }

    @Test(expected = SecretCipherException::class)
    fun `altered ciphertext body causes decrypt failure`() {
        val plaintext = "secret-token".toByteArray()
        val encrypted = cipher.encrypt(plaintext, aadTokens)

        // Flip a byte in the ciphertext portion
        val parts = encrypted.split(":", limit = 3)
        val ctBytes = Base64.getDecoder().decode(parts[2])
        ctBytes[0] = (ctBytes[0].toInt() xor 0x01).toByte()
        val tamperedCt = Base64.getEncoder().encodeToString(ctBytes)
        val tampered = "${parts[0]}:${parts[1]}:$tamperedCt"

        cipher.decrypt(tampered, aadTokens)
    }

    // ── Wrong AAD ──────────────────────────────────────────────

    @Test(expected = SecretCipherException::class)
    fun `different AAD causes decrypt failure`() {
        val plaintext = "token-data".toByteArray()

        val encrypted = cipher.encrypt(plaintext, aadTokens)

        // Decrypt with session AAD instead of tokens AAD
        cipher.decrypt(encrypted, aadSession)
    }

    @Test(expected = SecretCipherException::class)
    fun `empty AAD on decrypt when AAD was used on encrypt`() {
        val plaintext = "token-data".toByteArray()

        val encrypted = cipher.encrypt(plaintext, aadTokens)

        cipher.decrypt(encrypted, ByteArray(0))
    }

    // ── Invalid format ─────────────────────────────────────────

    @Test(expected = SecretCipherException::class)
    fun `wrong version prefix fails`() {
        val plaintext = "data".toByteArray()
        val encrypted = cipher.encrypt(plaintext, aadTokens)

        val tampered = encrypted.replaceFirst("v1", "v0")
        cipher.decrypt(tampered, aadTokens)
    }

    @Test(expected = SecretCipherException::class)
    fun `missing colons fails`() {
        cipher.decrypt("v1:invalid-no-colons", aadTokens)
    }

    @Test(expected = SecretCipherException::class)
    fun `empty string fails`() {
        cipher.decrypt("", aadTokens)
    }

    // ── Format compliance ──────────────────────────────────────

    @Test
    fun `encrypted output starts with v1 prefix`() {
        val encrypted = cipher.encrypt("data".toByteArray(), aadTokens)

        assertTrue("Output must start with 'v1:'", encrypted.startsWith("v1:"))
    }

    @Test
    fun `encrypted output has exactly two colons`() {
        val encrypted = cipher.encrypt("data".toByteArray(), aadTokens)

        val colonCount = encrypted.count { it == ':' }
        assertTrue("Output must have exactly 2 colons, got $colonCount", colonCount == 2)
    }

    // ── Test double ────────────────────────────────────────────

    /**
     * Creates a [SecretCipher] backed by a randomly-generated AES-256 key
     * in memory (not AndroidKeyStore), suitable for JVM unit tests.
     *
     * Uses the same format and algorithm as [AndroidKeystoreSecretCipher]:
     * AES/GCM/NoPadding, 256-bit key, 12-byte IV, 128-bit tag.
     */
    private fun createTestSecretCipher(): SecretCipher {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        val secretKey = keyGen.generateKey()

        return object : SecretCipher {
            override fun encrypt(plaintext: ByteArray, aad: ByteArray): String {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val iv = ByteArray(12).apply { SecureRandom().nextBytes(this) }
                val spec = GCMParameterSpec(128, iv)

                cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)
                cipher.updateAAD(aad)
                val ct = cipher.doFinal(plaintext)

                return "v1:${b64(iv)}:${b64(ct)}"
            }

            override fun decrypt(encrypted: String, aad: ByteArray): ByteArray {
                val parts = encrypted.split(":", limit = 3)
                if (parts.size != 3 || parts[0] != "v1") {
                    throw SecretCipherException("Invalid format")
                }

                val iv = unB64(parts[1])
                if (iv.size != 12) {
                    throw SecretCipherException("Invalid IV size")
                }

                val ct = unB64(parts[2])

                try {
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
                    cipher.updateAAD(aad)
                    return cipher.doFinal(ct)
                } catch (e: GeneralSecurityException) {
                    throw SecretCipherException("Decryption failed", e)
                }
            }

            private fun b64(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)
            private fun unB64(s: String) = Base64.getDecoder().decode(s)
        }
    }
}
