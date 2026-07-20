package com.david.mailapp.core.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Base64

/**
 * Instrumented test for [AndroidKeystoreSecretCipher] on a real Android device.
 *
 * Validates that AES-256-GCM via Android Keystore works correctly with:
 * - Round-trip encryption/decryption
 * - Random IV (same data → different output)
 * - Tampered ciphertext → [SecretCipherException]
 * - Wrong AAD → [SecretCipherException]
 *
 * These tests require a device or emulator (AndroidKeyStore is not available on JVM).
 * Run with: ./gradlew connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class AndroidKeystoreSecretCipherInstrumentedTest {

    private lateinit var cipher: SecretCipher
    private val aadTokens = "mailapp.oauth.tokens.v1".toByteArray()
    private val aadSession = "mailapp.oauth.pending-session.v1".toByteArray()

    @Before
    fun setUp() {
        cipher = AndroidKeystoreSecretCipher()
    }

    // ── Round-trip ─────────────────────────────────────────────

    @Test
    fun encryptThenDecryptReturnsOriginal() {
        val plaintext = "my-access-token-12345".toByteArray()

        val encrypted = cipher.encrypt(plaintext, aadTokens)
        assertNotNull("Encrypted output must not be null", encrypted)
        assertTrue("Encrypted output must start with v1:", encrypted.startsWith("v1:"))

        val decrypted = cipher.decrypt(encrypted, aadTokens)
        assertArrayEquals("Round-trip must preserve plaintext", plaintext, decrypted)
    }

    @Test
    fun roundTripWithEmptyPlaintext() {
        val plaintext = ByteArray(0)

        val encrypted = cipher.encrypt(plaintext, aadTokens)
        val decrypted = cipher.decrypt(encrypted, aadTokens)

        assertArrayEquals("Empty plaintext round-trip must succeed", plaintext, decrypted)
    }

    @Test
    fun roundTripWithSessionAad() {
        val state = "abc123state".toByteArray()

        val encrypted = cipher.encrypt(state, aadSession)
        val decrypted = cipher.decrypt(encrypted, aadSession)

        assertArrayEquals("Session AAD round-trip must work", state, decrypted)
    }

    // ── Different IV per encryption ────────────────────────────

    @Test
    fun samePlaintextProducesDifferentCiphertext() {
        val plaintext = "same-data".toByteArray()

        val encrypted1 = cipher.encrypt(plaintext, aadTokens)
        val encrypted2 = cipher.encrypt(plaintext, aadTokens)

        assertNotEquals(
            "Each encryption must produce unique output due to random IV",
            encrypted1, encrypted2
        )
    }

    // ── Tampered ciphertext ────────────────────────────────────

    @Test(expected = SecretCipherException::class)
    fun alteredIvCausesDecryptFailure() {
        val plaintext = "secret-token".toByteArray()
        val encrypted = cipher.encrypt(plaintext, aadTokens)

        val parts = encrypted.split(":", limit = 3)
        val ivBytes = Base64.getDecoder().decode(parts[1])
        ivBytes[0] = (ivBytes[0].toInt() xor 0x01).toByte()
        val tamperedIv = Base64.getEncoder().encodeToString(ivBytes)
        val tampered = "${parts[0]}:$tamperedIv:${parts[2]}"

        cipher.decrypt(tampered, aadTokens)
    }

    @Test(expected = SecretCipherException::class)
    fun alteredCiphertextBodyCausesDecryptFailure() {
        val plaintext = "secret-token".toByteArray()
        val encrypted = cipher.encrypt(plaintext, aadTokens)

        val parts = encrypted.split(":", limit = 3)
        val ctBytes = Base64.getDecoder().decode(parts[2])
        ctBytes[0] = (ctBytes[0].toInt() xor 0x01).toByte()
        val tamperedCt = Base64.getEncoder().encodeToString(ctBytes)
        val tampered = "${parts[0]}:${parts[1]}:$tamperedCt"

        cipher.decrypt(tampered, aadTokens)
    }

    // ── Wrong AAD ──────────────────────────────────────────────

    @Test(expected = SecretCipherException::class)
    fun differentAadCausesDecryptFailure() {
        val plaintext = "token-data".toByteArray()

        val encrypted = cipher.encrypt(plaintext, aadTokens)
        cipher.decrypt(encrypted, aadSession)
    }

    @Test(expected = SecretCipherException::class)
    fun emptyAadOnDecryptFails() {
        val plaintext = "token-data".toByteArray()

        val encrypted = cipher.encrypt(plaintext, aadTokens)
        cipher.decrypt(encrypted, ByteArray(0))
    }

    // ── Invalid format ─────────────────────────────────────────

    @Test(expected = SecretCipherException::class)
    fun wrongVersionPrefixFails() {
        val plaintext = "data".toByteArray()
        val encrypted = cipher.encrypt(plaintext, aadTokens)

        val tampered = encrypted.replaceFirst("v1", "v0")
        cipher.decrypt(tampered, aadTokens)
    }

    @Test(expected = SecretCipherException::class)
    fun emptyStringFails() {
        cipher.decrypt("", aadTokens)
    }
}
