package com.david.mailapp.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.david.mailapp.data.local.converter.PdfAttachmentMetadataCodec
import com.david.mailapp.domain.model.Email
import com.david.mailapp.domain.model.EmailFolder

/**
 * Room entity for email persistence.
 *
 * [labels] is stored as a comma-separated string to avoid needing
 * a separate join table. The list is small (Gmail labels are sparse).
 * [folder] is "inbox" or "trash" — used as the primary query filter.
 */
@Entity(tableName = "emails")
data class EmailEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "thread_id") val threadId: String,
    @ColumnInfo(name = "sender") val from: String,
    @ColumnInfo(name = "sender_initials") val fromInitials: String,
    @ColumnInfo(name = "recipient_to") val to: String,
    val subject: String,
    val snippet: String,
    val timestamp: Long,
    @ColumnInfo(name = "is_read") val isRead: Boolean,
    @ColumnInfo(name = "is_starred") val isStarred: Boolean,
    @ColumnInfo(name = "has_attachments") val hasAttachments: Boolean,
    val labels: String,        // comma-separated label list
    val folder: String,        // "inbox" or "trash"
    val body: String = "",     // HTML body content, empty until fetched
    @ColumnInfo(name = "clean_body") val cleanBody: String = "",
    @ColumnInfo(name = "pdf_attachments_json", defaultValue = "'[]'")
    val pdfAttachmentsJson: String = "[]",
    @ColumnInfo(name = "pdf_metadata_scanned", defaultValue = "0")
    val pdfMetadataScanned: Boolean = false,
    @ColumnInfo(name = "rfc_message_id")
    val rfcMessageId: String? = null,
    @ColumnInfo(name = "rfc_references")
    val rfcReferences: String? = null,
    @ColumnInfo(name = "content_state", defaultValue = "'NOT_FETCHED'")
    val contentState: String = "NOT_FETCHED",
    @ColumnInfo(name = "body_kind", defaultValue = "'UNKNOWN'")
    val bodyKind: String = "UNKNOWN",
    @ColumnInfo(name = "inline_references_json", defaultValue = "'[]'")
    val inlineReferencesJson: String = "[]",
    @ColumnInfo(name = "cached_content_bytes", defaultValue = "0")
    val cachedContentBytes: Long = 0L,
    @ColumnInfo(name = "content_last_access_epoch_ms", defaultValue = "0")
    val contentLastAccessEpochMs: Long = 0L
) {

    fun toDomain(): Email {
        val pdfAttachments = PdfAttachmentMetadataCodec.decode(pdfAttachmentsJson)
        val inlineRefs = com.david.mailapp.data.local.converter.InlineContentReferenceCodec.decode(inlineReferencesJson)
        return Email(
            id = id,
            threadId = threadId,
            from = from,
            fromInitials = fromInitials,
            to = to,
            subject = subject,
            snippet = snippet,
            timestamp = timestamp,
            isRead = isRead,
            isStarred = isStarred,
            hasAttachments = pdfAttachments.isNotEmpty(),
            labels = labels.split(",").filter { it.isNotBlank() },
            folder = EmailFolder.valueOf(folder.replaceFirstChar { it.uppercase() }),
            body = body,
            cleanBody = cleanBody,
            pdfAttachments = pdfAttachments,
            pdfMetadataScanned = pdfMetadataScanned,
            rfcMessageId = rfcMessageId,
            rfcReferences = rfcReferences,
            contentState = runCatching { com.david.mailapp.domain.model.EmailContentState.valueOf(contentState) }
                .getOrDefault(com.david.mailapp.domain.model.EmailContentState.NOT_FETCHED),
            bodyKind = runCatching { com.david.mailapp.domain.model.EmailBodyKind.valueOf(bodyKind) }
                .getOrDefault(com.david.mailapp.domain.model.EmailBodyKind.UNKNOWN),
            inlineReferences = inlineRefs,
            cachedContentBytes = cachedContentBytes,
            contentLastAccessEpochMs = contentLastAccessEpochMs
        )
    }

    companion object {
        fun fromDomain(email: Email, folder: EmailFolder): EmailEntity {
            return EmailEntity(
                id = email.id,
                threadId = email.threadId,
                from = email.from,
                fromInitials = email.fromInitials,
                to = email.to,
                subject = email.subject,
                snippet = email.snippet,
                timestamp = email.timestamp,
                isRead = email.isRead,
                isStarred = email.isStarred,
                hasAttachments = email.pdfAttachments.isNotEmpty(),
                labels = email.labels.joinToString(","),
                folder = folder.name.lowercase(),
                body = email.body,
                cleanBody = email.cleanBody,
                pdfAttachmentsJson = PdfAttachmentMetadataCodec.encode(email.pdfAttachments),
                pdfMetadataScanned = email.pdfMetadataScanned,
                rfcMessageId = email.rfcMessageId,
                rfcReferences = email.rfcReferences,
                contentState = email.contentState.name,
                bodyKind = email.bodyKind.name,
                inlineReferencesJson = com.david.mailapp.data.local.converter.InlineContentReferenceCodec.encode(email.inlineReferences),
                cachedContentBytes = email.cachedContentBytes,
                contentLastAccessEpochMs = email.contentLastAccessEpochMs
            )
        }
    }
}
