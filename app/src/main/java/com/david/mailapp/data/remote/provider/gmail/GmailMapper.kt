package com.david.mailapp.data.remote.provider.gmail

import com.david.mailapp.domain.model.Email
import com.david.mailapp.domain.model.EmailFolder

/**
 * Pure, testable mapping from Gmail's [MessageResponse] to [Email].
 *
 * Delegated here from [GmailProvider] so that unit tests can exercise
 * the mapping without instantiating a full provider.
 */
internal fun MessageResponse.toDomainEmail(): Email {
    val headers = payload?.headers ?: emptyList()
    val from = headers.headerValue("From") ?: "Unknown"
    val to = headers.headerValue("To") ?: ""
    val subject = headers.headerValue("Subject") ?: "(no subject)"
    val msgId = headers.headerValue("Message-ID")
    val references = headers.headerValue("References")

    val labels = labelIds ?: emptyList()
    val pdfAttachments = GmailMimeParser.parse(this).pdfAttachments

    return Email(
        id = id,
        threadId = threadId,
        from = from,
        fromInitials = extractInitials(from),
        to = to,
        subject = subject,
        snippet = snippet ?: "",
        timestamp = internalDate?.toLongOrNull() ?: 0L,
        isRead = labels.contains("UNREAD").not(),
        isStarred = labels.contains("STARRED"),
        hasAttachments = pdfAttachments.isNotEmpty(),
        labels = labels,
        folder = classifyGmailFolder(labels),
        pdfAttachments = pdfAttachments,
        pdfMetadataScanned = payload != null,
        rfcMessageId = msgId,
        rfcReferences = references
    )
}

/** Extract initials from "John Doe <john@example.com>" → "JD". */
internal fun extractInitials(from: String): String {
    val name = from.substringBefore("<").trim()
    if (name.isEmpty() || (name == from && name.contains("@"))) {
        val email = from.substringAfter("<").substringBefore(">").trim()
        return email.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?"
    }
    val initials = name.split(Regex("[^\\p{L}\\p{Nd}]+"))
        .filter { it.isNotBlank() }
        .take(2)
        .map { it.first().uppercase() }
        .joinToString("")
    if (initials.isEmpty()) {
        val email = from.substringAfter("<").substringBefore(">").trim()
        return email.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?"
    }
    return initials
}

/**
 * Classifies a Gmail label set into the local primary [EmailFolder].
 *
 * Precedence:
 *  1. Contains TRASH → [EmailFolder.Trash]
 *  2. Contains INBOX → [EmailFolder.Inbox]
 *  3. Any other combination → [EmailFolder.Other]
 *
 * Other represents sent, archived, spam, and any message that does not
 * genuinely belong to Inbox or Trash. All original labels are preserved in
 * [Email.labels]; this classifier only decides the primary local folder.
 * Used both for individual recovery and for search results, always through
 * [MessageResponse.toDomainEmail].
 */
internal fun classifyGmailFolder(labels: List<String>): EmailFolder = when {
    labels.contains("TRASH") -> EmailFolder.Trash
    labels.contains("INBOX") -> EmailFolder.Inbox
    else -> EmailFolder.Other
}
