package com.david.mailapp.feature.compose

import com.david.mailapp.data.remote.provider.ReplyContext
import com.david.mailapp.domain.model.Email

/**
 * Internal contract isolating ComposeViewModel from the repository
 * for testability without changing production behavior.
 *
 * The production factory wraps [com.david.mailapp.data.repository.EmailRepository];
 * tests substitute a fake implementation.
 */
interface ComposeEmailSource {
    suspend fun getUserEmail(): String?
    suspend fun getEmailById(emailId: String): Email?
    suspend fun sendEmail(
        to: String, cc: String?, bcc: String?,
        subject: String, body: String,
        replyContext: ReplyContext? = null
    )
}
