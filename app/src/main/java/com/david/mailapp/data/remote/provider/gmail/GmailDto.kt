package com.david.mailapp.data.remote.provider.gmail

import com.david.mailapp.domain.model.PdfAttachmentMetadata
import kotlinx.serialization.Serializable

/**
 * Gmail API v1 response DTOs — mapped to/from our domain [Email] model
 * in [GmailProvider].
 */

// ── messages.list response ──────────────────────────────────────

@Serializable
data class MessageListResponse(
    val messages: List<MessageHeader>? = null,
    val nextPageToken: String? = null,
    val resultSizeEstimate: Int? = null
)

@Serializable
data class MessageHeader(
    val id: String,
    val threadId: String
)

// ── messages.get response (format=metadata) ─────────────────────

@Serializable
data class MessageResponse(
    val id: String,
    val threadId: String,
    val labelIds: List<String>? = null,
    val snippet: String? = null,
    val payload: Payload? = null,
    val internalDate: String? = null  // epoch millis
)

@Serializable
data class Payload(
    val headers: List<Header>? = null,
    val mimeType: String? = null,
    val body: MessagePartBody? = null,
    val parts: List<Payload>? = null,
    val filename: String? = null,
    val partId: String? = null
)

@Serializable
data class MessagePartBody(
    val size: Int? = null,
    val data: String? = null,
    val attachmentId: String? = null
)

@Serializable
data class Header(
    val name: String,
    val value: String
)

// ── messages.modify / trash / untrash / delete ──────────────────

@Serializable
data class ModifyRequest(
    val addLabelIds: List<String>? = null,
    val removeLabelIds: List<String>? = null
)

// ── users.getProfile response ──────────────────────────────────

@Serializable
data class ProfileResponse(
    val emailAddress: String? = null
)

// ── messages.send request / response ───────────────────────────

@Serializable
data class SendRequest(
    val raw: String,
    val threadId: String? = null
)

@Serializable
data class SendResponse(
    val id: String,
    val threadId: String,
    val labelIds: List<String>? = null
)

// ── attachments.get response ───────────────────────────────────

@Serializable
data class AttachmentResponse(
    val attachmentId: String? = null,
    val size: Int? = null,
    val data: String? = null
)

// ── CID inline image helpers ────────────────────────────────────

/** Parsed Content-ID header value, with angle-brackets stripped. */
val Payload.contentId: String?
    get() = headers?.headerValue("Content-Id")?.trim('<', '>', ' ')

/** A part that is an inline image referenced via `cid:` in the HTML body. */
data class InlineImage(
    val contentId: String,
    val attachmentId: String,
    val mimeType: String
)

/**
 * Walks the MIME tree recursively and collects every part that has both
 * a [contentId] and a [body] with an [attachmentId][MessagePartBody.attachmentId].
 */
fun Payload.collectInlineImages(): List<InlineImage> {
    val result = mutableListOf<InlineImage>()

    val cid = contentId
    val attId = body?.attachmentId
    val mt = mimeType
    if (cid != null && attId != null && mt != null) {
        result += InlineImage(contentId = cid, attachmentId = attId, mimeType = mt)
    }

    parts?.forEach { result += it.collectInlineImages() }
    return result
}

/**
 * Collects PDF attachment metadata from this MIME part tree.
 *
 * Walks the root part and recurses into all nested [parts], evaluating each
 * node against the PDF criteria defined below. Results are deduplicated by
 * [MessagePartBody.attachmentId] (first occurrence wins).
 *
 * Acceptance criteria (ALL must hold):
 * - [mimeType] == "application/pdf" (case-sensitive exact match).
 * - [filename] is present and non-blank, ending with ".pdf" (case-insensitive).
 * - [body.attachmentId] is present and non-blank after trim().
 * - Content-Disposition header does NOT start with "inline" (case-insensitive).
 * - Content-Id header is absent or blank (having a CID → inline image, not an attachment).
 *
 * This function does NOT read [MessagePartBody.data], decode Base64, or perform
 * any network operations — it is a pure MIME-metadata scan.
 */
internal fun Payload.collectPdfAttachments(): List<PdfAttachmentMetadata> {
    val seen = mutableSetOf<String>()
    val result = mutableListOf<PdfAttachmentMetadata>()

    fun walk(node: Payload) {
        val mime = node.mimeType
        val fname = node.filename
        val attId = node.body?.attachmentId?.trim()

        // Quick reject on MIME before checking anything else
        if (mime != "application/pdf") {
            node.parts?.forEach { walk(it) }
            return
        }

        // filename check
        if (fname.isNullOrBlank() || !fname.endsWith(".pdf", ignoreCase = true)) {
            node.parts?.forEach { walk(it) }
            return
        }

        // attachmentId check
        if (attId.isNullOrBlank()) {
            node.parts?.forEach { walk(it) }
            return
        }

        // Content-Disposition: reject if token starts with "inline"
        val disposition = node.headers?.headerValue("Content-Disposition")
        if (disposition != null && disposition.startsWith("inline", ignoreCase = true)) {
            node.parts?.forEach { walk(it) }
            return
        }

        // Content-ID: reject ONLY when there's no explicit attachment disposition.
        // Gmail assigns CIDs to all MIME parts, including actual attachments.
        // A part with Content-Disposition: attachment should be accepted even if
        // it has a CID. Only reject when the part has no disposition (inline image).
        val cid = node.contentId
        val isExplicitAttachment = disposition != null &&
            disposition.startsWith("attachment", ignoreCase = true)
        if (!isExplicitAttachment && !cid.isNullOrBlank()) {
            node.parts?.forEach { walk(it) }
            return
        }

        // Deduplicate by attachmentId
        if (seen.add(attId)) {
            result += PdfAttachmentMetadata(
                fileName = fname,
                mimeType = "application/pdf",
                attachmentId = attId,
                sizeBytes = node.body?.size?.toLong(),
                partId = node.partId?.trim()?.takeIf { it.isNotEmpty() }
            )
        }

        node.parts?.forEach { walk(it) }
    }

    walk(this)
    return result
}
