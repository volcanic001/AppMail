package com.david.mailapp.core.security

import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

/**
 * A [SecretCipher] backed by an in-memory AES-256 key (not AndroidKeyStore),
 * suitable for JVM unit tests.
 *
 * Uses the same algorithm and serialization format as [AndroidKeystoreSecretCipher]:
 * - AES/GCM/NoPadding, 256-bit key
 * - 12-byte random IV per encryption
 * - 128-bit GCM authentication tag
 * - AAD required
 * - Format: "v1:<iv-base64>:<ciphertext-tag-base64>"
 *
 * Not suitable for production (key lives only in memory, no secure key storage).
 */
class FakeSecretCipher : SecretCipher {

    private val secretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    override fun encrypt(plaintext: ByteArray, aad: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(12).apply { SecureRandom().nextBytes(this) }
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
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

/** Test double for fail-closed storage paths. */
class FailingSecretCipher : SecretCipher {
    override fun encrypt(plaintext: ByteArray, aad: ByteArray): String {
        throw SecretCipherException("Simulated encryption failure")
    }

    override fun decrypt(encrypted: String, aad: ByteArray): ByteArray {
        throw SecretCipherException("Simulated decryption failure")
    }
}
