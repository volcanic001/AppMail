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
     * Recovers a single message by its id, independent of search and cache.
     *
     * The result distinguishes a found message, a confirmed-inexistent message,
     * and the different reasons a lookup can fail. CancellationException is
     * always propagated, never converted into a result.
     */
    suspend fun fetchEmailById(emailId: String): EmailLookupResult

    /**
     * Fetch the full HTML body of a message along with references to any inline images (format=full).
     * Returns null if the message cannot be fetched or parsed.
     */
    suspend fun fetchBodyWithRefs(emailId: String): BodyFetchResult?

    /**
     * Download inline images given their references from [fetchBodyWithRefs].
     * Returns a map of CID -> Base64 Data URI.
     */
    suspend fun downloadInlineImages(emailId: String, refs: List<com.david.mailapp.domain.model.EmailInlineReference>): Map<String, String>

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
     * [replyContext] proporciona threadId y encabezados RFC In-Reply-To/References
     * para threading. Nulo para un correo nuevo.
     */
    suspend fun sendEmail(
        to: String,
        cc: String?,
        bcc: String?,
        subject: String,
        body: String,
        replyContext: ReplyContext? = null
    )
}

/** Result of fetching the email body, containing both raw body and inline image references. */
data class BodyFetchResult(
    val rawBody: String?,
    val contentState: com.david.mailapp.domain.model.EmailContentState = com.david.mailapp.domain.model.EmailContentState.EMPTY,
    val bodyKind: com.david.mailapp.domain.model.EmailBodyKind = com.david.mailapp.domain.model.EmailBodyKind.UNKNOWN,
    val inlineRefs: List<com.david.mailapp.domain.model.EmailInlineReference> = emptyList(),
    val pdfAttachments: List<PdfAttachmentMetadata> = emptyList()
)

/**
 * Typed outcome of [EmailProvider.fetchEmailById].
 *
 * Null is never used because it cannot distinguish a confirmed-inexistent
 * message from a network error or an unparseable response. The contract
 * exposes no HTTP codes, exceptions, or technical messages to upper layers.
 */
sealed interface EmailLookupResult {
    data class Found(val email: Email) : EmailLookupResult
    data object NotFound : EmailLookupResult
    data class Failure(val reason: EmailLookupFailureReason) : EmailLookupResult
}

/** Why an individual email lookup failed. */
enum class EmailLookupFailureReason {
    /** Network/IO failure after exhausting retries. */
    NO_CONNECTION,

    /** Session expired or invalidated — reauthentication required. */
    SESSION_EXPIRED,

    /** Remote transient failure after exhausting retries. */
    TEMPORARY_REMOTE,

    /** Remote rejected the request (4xx other than 401/404). */
    REMOTE_REJECTED,

    /** Response was rejected, incomplete, or could not be interpreted. */
    INVALID_RESPONSE
}
