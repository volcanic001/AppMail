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
