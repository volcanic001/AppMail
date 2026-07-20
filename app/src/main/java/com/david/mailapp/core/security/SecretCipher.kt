package com.david.mailapp.core.security

/**
 * Cryptographic interface for encrypting and decrypting OAuth secrets at rest.
 *
 * All secrets are encrypted with AES-256-GCM via Android Keystore before
 * being written to DataStore. The serialized format is:
 *
 *     v1:<iv-base64>:<ciphertext-y-tag-base64>
 *
 * Implementations must ensure:
 * - A new random 12-byte IV per encryption.
 * - 128-bit GCM authentication tag.
 * - Associated data (AAD) is bound to the ciphertext and verified on decrypt.
 * - Decrypt never returns partial data on failure.
 */
interface SecretCipher {

    /**
     * Encrypt [plaintext] with [aad] as associated authenticated data.
     * @return Persistent string in the format "v1:<iv-base64>:<ciphertext-tag-base64>".
     * @throws SecretCipherException if encryption fails.
     */
    fun encrypt(plaintext: ByteArray, aad: ByteArray): String

    /**
     * Decrypt an [encrypted] string previously produced by [encrypt].
     * @param encrypted String in the format "v1:<iv-base64>:<ciphertext-tag-base64>".
     * @param aad The same associated data used during encryption.
     * @return The original plaintext bytes.
     * @throws SecretCipherException if the format, version, IV, ciphertext, tag,
     *   or AAD are invalid.
     */
    fun decrypt(encrypted: String, aad: ByteArray): ByteArray
}
