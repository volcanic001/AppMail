package com.david.mailapp.data.remote.provider

import com.david.mailapp.domain.model.Email
import com.david.mailapp.domain.model.PaginatedResult
import com.david.mailapp.domain.model.PdfAttachmentMetadata

/**
 * Abstraction over email providers (Gmail, Outlook, IMAP, etc.).
 *
 * Every provider implements this interface. The rest of the app
 * — Repository, ViewModel, UI — depends ONLY on this interface,
 * never on a concrete provider class.
 *
 * Adding a new provider = 1 file implementing this interface.
 * Zero changes anywhere else.
 */
interface EmailProvider {

    /** Fetch inbox messages. Pass [pageToken] from the previous [PaginatedResult] for pagination. */
    suspend fun fetchInbox(pageToken: String? = null): PaginatedResult<Email>

    /** Fetch trashed messages. */
    suspend fun fetchTrash(pageToken: String? = null): PaginatedResult<Email>

    /** Search messages using Gmail's native query syntax. */
    suspend fun search(query: String, pageToken: String? = null): PaginatedResult<Email>

    /** Move an email to trash on the server. */
    suspend fun moveToTrash(emailId: String)

    /** Restore an email from trash back to inbox on the server. */
    suspend fun restoreFromTrash(emailId: String)

    /** Permanently delete an email on the server. */
    suspend fun deletePermanently(emailId: String)

    /** Mark an email as read on the server. */
    suspend fun markAsRead(emailId: String)

    /**
     * Fetch the full HTML body of a message along with references to any inline images (format=full).
     * Returns null if the message cannot be fetched or parsed.
     */
    suspend fun fetchBodyWithRefs(emailId: String): BodyFetchResult?

    /**
     * Download inline images given their references from [fetchBodyWithRefs].
     * Returns a map of CID -> Base64 Data URI.
     */
    suspend fun downloadInlineImages(emailId: String, refs: List<InlineImageRef>): Map<String, String>

    /** Obtiene la dirección de email del usuario autenticado. */
    suspend fun getUserEmail(): String?

    /**
     * Descarga los bytes crudos de un adjunto identificado por [attachmentId].
     * Llamada directa a la API REST: GET …/messages/{emailId}/attachments/{attachmentId}.
     */
    suspend fun downloadAttachment(emailId: String, attachmentId: String): ByteArray

    /**
     * Envía un email de texto plano vía el provider.
     *
     * [inReplyToId] y [references] se usan para threading RFC 2822 en Reply.
     */
    suspend fun sendEmail(
        to: String,
        cc: String?,
        bcc: String?,
        subject: String,
        body: String,
        inReplyToId: String? = null,
        references: String? = null
    )
}

/** Reference to an inline image found in the email payload during initial body fetch. */
data class InlineImageRef(
    val contentId: String,
    val attachmentId: String,
    val mimeType: String
)

/** Result of fetching the email body, containing both raw body and inline image references. */
data class BodyFetchResult(
    val rawBody: String?,
    val inlineRefs: List<InlineImageRef>,
    val pdfAttachments: List<PdfAttachmentMetadata> = emptyList()
)
