package com.david.mailapp.domain.model

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable

/**
 * Domain model for an email message — platform-agnostic.
 *
 * [fromInitials] is computed from the sender name at parse time
 * so the UI doesn't need to extract initials on every recomposition.
 *
 * Annotated [Immutable] so the Compose compiler can skip recomposition
 * when the same instance is passed to a composable — eliminates scroll lag.
 */
@Immutable
data class Email(
    val id: String,
    val threadId: String,
    val from: String,
    val fromInitials: String,
    val to: String,
    val subject: String,
    val snippet: String,
    val timestamp: Long,
    val isRead: Boolean,
    val isStarred: Boolean,
    val hasAttachments: Boolean,
    val labels: List<String>,
    val folder: EmailFolder,
    val body: String = "",
    val cleanBody: String = "",
    val pdfAttachments: List<PdfAttachmentMetadata> = emptyList(),
    val pdfMetadataScanned: Boolean = false,
    val rfcMessageId: String? = null,
    val rfcReferences: String? = null,

    // Explicit content contract
    val contentState: EmailContentState = EmailContentState.NOT_FETCHED,
    val bodyKind: EmailBodyKind = EmailBodyKind.UNKNOWN,
    val inlineReferences: List<EmailInlineReference> = emptyList(),
    val cachedContentBytes: Long = 0L,
    val contentLastAccessEpochMs: Long = 0L
)

@Stable
enum class EmailFolder {
    Inbox,
    Trash,
    Other
}
