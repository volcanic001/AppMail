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
    val pdfAttachments = payload?.collectPdfAttachments().orEmpty()

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
        folder = if (labels.contains("TRASH")) EmailFolder.Trash else EmailFolder.Inbox,
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
