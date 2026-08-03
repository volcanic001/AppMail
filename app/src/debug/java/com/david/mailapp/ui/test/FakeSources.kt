package com.david.mailapp.ui.test

import com.david.mailapp.domain.model.Email
import com.david.mailapp.domain.model.EmailFolder
import com.david.mailapp.domain.model.PaginatedResult
import com.david.mailapp.feature.compose.ComposeEmailSource
import com.david.mailapp.feature.search.SearchEmailSource
import kotlinx.coroutines.awaitCancellation

/**
 * Deterministic fake for [SearchEmailSource].
 *
 * Returns 10 results per query with predictable IDs: "{query}_0" … "{query}_9".
 * pageToken = null returns page 0; token "next_page_N" returns page N+1;
 * token "last_page" returns empty results.
 */
class FakeSearchEmailSource : SearchEmailSource {
    override suspend fun search(query: String, pageToken: String?): PaginatedResult<Email> {
        RestorationProbe.searchCalls += SearchCall(query, pageToken)
        val page = when {
            pageToken == null -> 0
            pageToken.startsWith("next_page_") -> pageToken.removePrefix("next_page_").toInt()
            pageToken == "last_page" -> return PaginatedResult(emptyList(), nextPageToken = null)
            else -> 0
        }
        val start = page * 10
        val results = (start until start + 10).map { i ->
            makeEmail("${query}_$i", folder = EmailFolder.Inbox)
        }
        val nextToken = if (page < 9) "next_page_${page + 1}" else null
        return PaginatedResult(results, nextPageToken = nextToken)
    }
}

/**
 * Deterministic fake for [ComposeEmailSource].
 *
 * Returns a crafted [Email] for any emailId and records the last send call.
 */
class FakeComposeEmailSource : ComposeEmailSource {
    var holdSend: Boolean = false
    var lastSendTo = ""
    var lastSendSubject = ""
    var lastSendBody = ""
    var lastSendCc = ""
    var lastSendBcc = ""

    override suspend fun getUserEmail(): String? = "me@test.com"

    override suspend fun getEmailById(emailId: String): Email? {
        return makeEmail(emailId, folder = EmailFolder.Inbox)
    }

    override suspend fun sendEmail(
        to: String,
        cc: String?,
        bcc: String?,
        subject: String,
        body: String,
        replyContext: com.david.mailapp.data.remote.provider.ReplyContext?
    ) {
        lastSendTo = to
        lastSendCc = cc.orEmpty()
        lastSendBcc = bcc.orEmpty()
        lastSendSubject = subject
        lastSendBody = body
        if (holdSend) awaitCancellation()
    }
}

/** Deterministic [Email] factory. */
internal fun makeEmail(
    id: String,
    from: String = "sender@test.com",
    subject: String = "Subject $id",
    folder: EmailFolder = EmailFolder.Inbox
) = Email(
    id = id, threadId = "t_$id", from = from, fromInitials = "S",
    to = "me@test.com", subject = subject, snippet = "Snippet $id",
    timestamp = 1000L, isRead = false, isStarred = false,
    hasAttachments = false, labels = emptyList(), folder = folder
)
