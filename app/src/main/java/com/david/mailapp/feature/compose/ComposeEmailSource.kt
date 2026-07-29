package com.david.mailapp.feature.compose

/**
 * Internal contract isolating ComposeViewModel from the repository
 * for testability without changing production behavior.
 *
 * The production factory wraps [com.david.mailapp.data.repository.EmailRepository];
 * tests substitute a fake implementation.
 */
interface ComposeEmailSource {
    suspend fun getUserEmail(): String?
    suspend fun sendEmail(
        to: String, cc: String?, bcc: String?,
        subject: String, body: String,
        inReplyToId: String? = null,
        references: String? = null
    )
}
