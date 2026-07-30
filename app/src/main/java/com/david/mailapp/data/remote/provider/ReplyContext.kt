package com.david.mailapp.data.remote.provider

import com.david.mailapp.domain.model.Email

/**
 * RFC threading context carried through the send pipeline.
 */
data class ReplyContext(
    val threadId: String,
    val inReplyTo: String?,
    val references: String?
) {
    companion object {
        /**
         * Always returns a [ReplyContext]. Never null.
         *
         * - Always preserves [Email.threadId].
         * - If [Email.rfcMessageId] is present: builds In-Reply-To and References.
         * - Without Message-ID: keeps thread, both RFC headers are null.
         * - Never uses [Email.id].
         */
        fun from(email: Email): ReplyContext {
            val messageId = email.rfcMessageId?.trim()?.takeIf { it.isNotEmpty() }
            val previousReferences = email.rfcReferences?.trim()?.takeIf { it.isNotEmpty() }

            val references = when {
                messageId == null -> null
                previousReferences == null -> messageId
                else -> "$previousReferences $messageId"
            }

            return ReplyContext(
                threadId = email.threadId,
                inReplyTo = messageId,
                references = references
            )
        }
    }
}
