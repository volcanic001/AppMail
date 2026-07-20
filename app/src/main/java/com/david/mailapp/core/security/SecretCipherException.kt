package com.david.mailapp.core.security

/**
 * Exception thrown when a [SecretCipher] operation fails.
 *
 * Covers:
 * - Invalid format or version in the serialized ciphertext.
 * - Invalid IV (length, encoding).
 * - AEAD tag mismatch (altered ciphertext or wrong AAD).
 * - Android Keystore key not found or unusable.
 *
 * Never contains plaintext, keys, or full ciphertext in the message.
 */
class SecretCipherException(message: String, cause: Throwable? = null) : Exception(message, cause)
