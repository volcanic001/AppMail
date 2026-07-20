package com.david.mailapp.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * [SecretCipher] backed by Android Keystore with AES-256-GCM.
 *
 * Configuration (per specification):
 * - Provider: AndroidKeyStore.
 * - Alias: `mailapp_oauth_secrets_v1` (versioned for future key rotation).
 * - Algorithm: AES/GCM/NoPadding.
 * - Key size: 256 bits.
 * - IV: 12 random bytes, fresh per encryption.
 * - Authentication tag: 128 bits.
 * - AAD: required, caller-provided (two distinct constants per spec).
 * - Randomized encryption required: true.
 * - User authentication: not required (no PIN/biometric prompt).
 * - StrongBox: not required.
 *
 * Serialized format: `v1:<iv-base64>:<ciphertext-tag-base64>`
 *
 * Thread-safe: each call creates a fresh [Cipher] instance. The underlying
 * Keystore key is read-only after generation.
 */
class AndroidKeystoreSecretCipher : SecretCipher {

    private companion object {
        const val KEY_ALIAS = "mailapp_oauth_secrets_v1"
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"

        const val FORMAT_VERSION = "v1"
        const val IV_SIZE_BYTES = 12
        const val TAG_LENGTH_BITS = 128

        const val AES_GCM_NO_PADDING = "AES/GCM/NoPadding"
    }

    override fun encrypt(plaintext: ByteArray, aad: ByteArray): String {
        try {
            val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
            // Android Keystore must generate the IV when randomized encryption is required.
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            cipher.updateAAD(aad)
            val ciphertextWithTag = cipher.doFinal(plaintext)
            val iv = cipher.iv
            if (iv == null || iv.size != IV_SIZE_BYTES) {
                throw SecretCipherException("Keystore returned an invalid GCM IV")
            }
            return formatCiphertext(iv, ciphertextWithTag)
        } catch (e: SecretCipherException) {
            throw e
        } catch (e: GeneralSecurityException) {
            throw SecretCipherException("Encryption failed", e)
        }
    }

    override fun decrypt(encrypted: String, aad: ByteArray): ByteArray {
        val (iv, ciphertextWithTag) = parseCiphertext(encrypted)
        try {
            val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
            val spec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, getKey(), spec)
            cipher.updateAAD(aad)
            return cipher.doFinal(ciphertextWithTag)
        } catch (e: SecretCipherException) {
            throw e
        } catch (e: GeneralSecurityException) {
            throw SecretCipherException("Decryption failed", e)
        }
    }

    // ── Private helpers ────────────────────────────────────────

    @Synchronized
    private fun getOrCreateKey(): SecretKey {
        try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            if (keyStore.containsAlias(KEY_ALIAS)) {
                return keyStore.getKey(KEY_ALIAS, null) as? SecretKey
                    ?: throw SecretCipherException("Keystore key has an invalid type")
            }

            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                KEYSTORE_PROVIDER
            )
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(false)
                .build()

            keyGenerator.init(spec)
            return keyGenerator.generateKey()
        } catch (e: SecretCipherException) {
            throw e
        } catch (e: GeneralSecurityException) {
            throw SecretCipherException("Unable to access Android Keystore", e)
        }
    }

    private fun getKey(): SecretKey {
        try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            return keyStore.getKey(KEY_ALIAS, null) as? SecretKey
                ?: throw SecretCipherException("Keystore key is unavailable")
        } catch (e: SecretCipherException) {
            throw e
        } catch (e: GeneralSecurityException) {
            throw SecretCipherException("Unable to access Android Keystore", e)
        }
    }

    private fun formatCiphertext(iv: ByteArray, ciphertextWithTag: ByteArray): String {
        val ivB64 = Base64.getEncoder().encodeToString(iv)
        val ctB64 = Base64.getEncoder().encodeToString(ciphertextWithTag)
        return "$FORMAT_VERSION:$ivB64:$ctB64"
    }

    /**
     * Parse a `v1:<iv-base64>:<ct-base64>` string.
     * @throws SecretCipherException if the format, version, or IV length is invalid.
     */
    private fun parseCiphertext(encrypted: String): Pair<ByteArray, ByteArray> {
        val parts = encrypted.split(":", limit = 3)
        if (parts.size != 3) {
            throw SecretCipherException("Invalid ciphertext format: expected 3 colon-delimited parts, got ${parts.size}")
        }
        if (parts[0] != FORMAT_VERSION) {
            throw SecretCipherException("Unsupported version '${parts[0]}'; expected '$FORMAT_VERSION'")
        }

        val iv = try {
            Base64.getDecoder().decode(parts[1])
        } catch (e: IllegalArgumentException) {
            throw SecretCipherException("Invalid IV Base64 encoding", e)
        }
        if (iv.size != IV_SIZE_BYTES) {
            throw SecretCipherException(
                "Invalid IV length: expected $IV_SIZE_BYTES bytes, got ${iv.size}"
            )
        }

        val ciphertextWithTag = try {
            Base64.getDecoder().decode(parts[2])
        } catch (e: IllegalArgumentException) {
            throw SecretCipherException("Invalid ciphertext Base64 encoding", e)
        }
        if (ciphertextWithTag.size < TAG_LENGTH_BITS / 8) {
            throw SecretCipherException("Ciphertext is shorter than the GCM authentication tag")
        }

        return Pair(iv, ciphertextWithTag)
    }
}
