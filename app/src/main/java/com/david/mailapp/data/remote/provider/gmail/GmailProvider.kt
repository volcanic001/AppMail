package com.david.mailapp.data.remote.provider.gmail

import android.os.SystemClock
import android.util.Log
import com.david.mailapp.data.remote.provider.BodyFetchResult
import com.david.mailapp.data.remote.provider.EmailProvider
import com.david.mailapp.data.remote.provider.InlineImageRef
import com.david.mailapp.data.remote.provider.ReplyContext
import com.david.mailapp.domain.model.Email
import com.david.mailapp.domain.model.EmailFolder
import com.david.mailapp.domain.model.PaginatedResult
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

// DEBUG_PERF: tag central para filtrar en Logcat → tag:MailPerfTrace
private const val PERF_TAG = "MailPerfTrace"
private fun perfNow() = SystemClock.elapsedRealtime()

/**
 * Gmail API v1 implementation of [EmailProvider].
 *
 * Pagination contract (per user's spec):
 *   fetchInbox(pageToken: String?) → PaginatedResult<Email>
 *   [pageToken] is the opaque token from the previous response.
 *   The returned [PaginatedResult.nextPageToken] drives subsequent pages.
 */
class GmailProvider(
    private val client: HttpClient
) : EmailProvider {

    // ── fetch ───────────────────────────────────────────────────

    override suspend fun fetchInbox(pageToken: String?): PaginatedResult<Email> {
        return fetchFolder(labelId = "INBOX", pageToken = pageToken)
    }

    override suspend fun fetchTrash(pageToken: String?): PaginatedResult<Email> {
        return fetchFolder(labelId = "TRASH", pageToken = pageToken)
    }

    override suspend fun search(query: String, pageToken: String?): PaginatedResult<Email> {
        Log.d("SearchDebug", "[GmailProvider] Executing API search with q='$query', pageToken=$pageToken")
        try {
            val listResponse: MessageListResponse = client.get("users/me/messages") {
                parameter("q", query)
                parameter("maxResults", 20)
                if (pageToken != null) parameter("pageToken", pageToken)
            }.body()

            val messages = listResponse.messages ?: emptyList()
            Log.d("SearchDebug", "[GmailProvider] API returned ${messages.size} message headers. nextPageToken: ${listResponse.nextPageToken}")
            if (messages.isEmpty()) return PaginatedResult(emptyList(), null)

            val emails = kotlinx.coroutines.supervisorScope {
                messages.map { msgHeader ->
                    async { fetchMessage(msgHeader.id) }
                }.awaitAll().filterNotNull()
            }
            Log.d("SearchDebug", "[GmailProvider] Successfully fetched ${emails.size} full email details.")
            return PaginatedResult(items = emails, nextPageToken = listResponse.nextPageToken)
        } catch (e: Exception) {
            Log.e("SearchDebug", "[GmailProvider] API search failed for q='$query': ${e.message}", e)
            throw e
        }
    }

    private suspend fun fetchFolder(labelId: String, pageToken: String?): PaginatedResult<Email> {
        // Step 1: list message IDs
        val listResponse: MessageListResponse = client.get("users/me/messages") {
            parameter("labelIds", labelId)
            parameter("maxResults", 20)
            if (pageToken != null) parameter("pageToken", pageToken)
        }.body()

        val messages = listResponse.messages ?: emptyList()
        if (messages.isEmpty()) {
            return PaginatedResult(emptyList(), null)
        }

        val emails = kotlinx.coroutines.supervisorScope {
            messages.map { msgHeader ->
                async { fetchMessage(msgHeader.id) }
            }.awaitAll().filterNotNull()
        }

        return PaginatedResult(
            items = emails,
            nextPageToken = listResponse.nextPageToken
        )
    }

    private suspend fun fetchMessage(messageId: String): Email? {
        return try {
            val response: MessageResponse = client.get("users/me/messages/$messageId") {
                parameter("format", "full")
            }.body()
            response.toDomain()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    // ── body fetch ──────────────────────────────────────────────

    override suspend fun fetchBodyWithRefs(emailId: String): BodyFetchResult? {
        // DEBUG_PERF
        val t0 = perfNow()
        Log.d(PERF_TAG, "[BODY_FETCH] START emailId=$emailId")
        return try {
            val response: MessageResponse = client.get("users/me/messages/$emailId") {
                parameter("format", "full")
            }.body()
            val tHttp = perfNow()
            Log.d(PERF_TAG, "[BODY_FETCH] HTTP_DONE emailId=$emailId durationMs=${tHttp - t0}")

            val payload = response.payload ?: run {
                Log.d(PERF_TAG, "[BODY_FETCH] NO_PAYLOAD emailId=$emailId")
                return null
            }

            val tExtract0 = perfNow()
            val rawBody = extractHtmlBody(payload)
            val tExtract1 = perfNow()
            Log.d(PERF_TAG, "[BODY_FETCH] EXTRACT_DONE emailId=$emailId extractMs=${tExtract1 - tExtract0} bodyLen=${rawBody?.length ?: 0} totalMs=${tExtract1 - t0}")

            val inlineImages = payload.collectInlineImages()
            val inlineRefs = inlineImages.map {
                InlineImageRef(
                    contentId = it.contentId,
                    attachmentId = it.attachmentId,
                    mimeType = it.mimeType
                )
            }
            Log.d(PERF_TAG, "[BODY_FETCH] INLINE_REFS_FOUND count=${inlineRefs.size} emailId=$emailId")

            val pdfAttachments = payload.collectPdfAttachments()
            Log.d(PERF_TAG, "[BODY_FETCH] PDF_ATTACHMENTS_FOUND count=${pdfAttachments.size} emailId=$emailId")

            BodyFetchResult(rawBody = rawBody, inlineRefs = inlineRefs, pdfAttachments = pdfAttachments)
        } catch (e: Exception) {
            Log.d(PERF_TAG, "[BODY_FETCH] ERROR emailId=$emailId durationMs=${perfNow() - t0} error=${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    override suspend fun downloadInlineImages(
        emailId: String,
        refs: List<InlineImageRef>
    ): Map<String, String> {
        // DEBUG_PERF
        Log.d(PERF_TAG, "[INLINE_DOWNLOAD] START imageCount=${refs.size} emailId=$emailId")
        if (refs.isEmpty()) return emptyMap()

        val t0 = perfNow()
        val result = coroutineScope {
            refs.mapIndexed { idx, ref ->
                async {
                    // DEBUG_PERF
                    val tImg = perfNow()
                    Log.d(PERF_TAG, "[INLINE_DOWNLOAD] IMG_START idx=$idx cid=${ref.contentId} mime=${ref.mimeType} attachId=${ref.attachmentId}")
                    try {
                        val bytes = fetchAttachmentBytes(emailId, ref.attachmentId)
                        val tBytes = perfNow()
                        Log.d(PERF_TAG, "[INLINE_DOWNLOAD] IMG_BYTES idx=$idx sizeBytes=${bytes.size} fetchMs=${tBytes - tImg}")
                        val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                        val tEncode = perfNow()
                        Log.d(PERF_TAG, "[INLINE_DOWNLOAD] IMG_ENCODED idx=$idx b64Len=${b64.length} encodeMs=${tEncode - tBytes} totalImgMs=${tEncode - tImg}")
                        ref.contentId to "data:${ref.mimeType};base64,$b64"
                    } catch (e: Exception) {
                        Log.d(PERF_TAG, "[INLINE_DOWNLOAD] IMG_ERROR idx=$idx cid=${ref.contentId} error=${e.javaClass.simpleName}: ${e.message}")
                        null
                    }
                }
            }.awaitAll().mapNotNull { it }.toMap()
        }
        Log.d(PERF_TAG, "[INLINE_DOWNLOAD] ALL_DONE emailId=$emailId successCount=${result.size} parallelMs=${perfNow() - t0}")
        return result
    }

    /**
     * Downloads raw bytes for a single attachment via
     * `GET …/messages/{messageId}/attachments/{id}`.
     */
    private suspend fun fetchAttachmentBytes(
        messageId: String,
        attachmentId: String
    ): ByteArray {
        // DEBUG_PERF
        val t0 = perfNow()
        val response: AttachmentResponse =
            client.get("users/me/messages/$messageId/attachments/$attachmentId").body()
        val tHttp = perfNow()
        Log.d(PERF_TAG, "[ATTACHMENT] HTTP_DONE attachmentId=$attachmentId httpMs=${tHttp - t0}")

        val b64Data = response.data ?: run {
            Log.d(PERF_TAG, "[ATTACHMENT] NO_DATA attachmentId=$attachmentId")
            return ByteArray(0)
        }
        val bytes = android.util.Base64.decode(b64Data, android.util.Base64.URL_SAFE)
        Log.d(PERF_TAG, "[ATTACHMENT] DECODED attachmentId=$attachmentId rawB64Len=${b64Data.length} bytesLen=${bytes.size} decodeMs=${perfNow() - tHttp}")
        return bytes
    }



    /**
     * Recursively walks [payload] + [Payload.parts] to find the best textual body.
     *
     * - Ignores parts with a non-empty [Payload.filename] or a
     *   [MessagePartBody.attachmentId] — those are attachments (D9).
     * - Prefers mimeType == "text/html"; falls back to text/plain.
     * - Decodes Gmail's URL-safe base64 data to UTF-8.
     */
    private fun extractHtmlBody(payload: Payload): String? {
        // DEBUG_PERF
        Log.d(PERF_TAG, "[EXTRACT_BODY] START")
        // First, prefer text/html anywhere in the tree
        val htmlPart = findPartByMimeType(payload, "text/html")
        if (htmlPart != null) {
            val rawData = htmlPart.body?.data
            Log.d(PERF_TAG, "[EXTRACT_BODY] HTML_PART_FOUND rawDataLen=${rawData?.length ?: 0}")
            val t0 = perfNow()
            val decoded = rawData?.let { decodeBase64Url(it) }
            Log.d(PERF_TAG, "[EXTRACT_BODY] HTML_DECODED decodedLen=${decoded?.length ?: 0} decodeMs=${perfNow() - t0}")
            if (!decoded.isNullOrBlank()) {
                return decoded
            }
        } else {
            Log.d(PERF_TAG, "[EXTRACT_BODY] NO_HTML_PART fallback=text/plain")
        }

        // Fallback to text/plain if no valid text/html part exists
        val plainPart = findPartByMimeType(payload, "text/plain")
        if (plainPart != null) {
            val rawData = plainPart.body?.data
            Log.d(PERF_TAG, "[EXTRACT_BODY] PLAIN_PART_FOUND rawDataLen=${rawData?.length ?: 0}")
            val t0 = perfNow()
            val decoded = rawData?.let { decodeBase64Url(it) }
            Log.d(PERF_TAG, "[EXTRACT_BODY] PLAIN_DECODED decodedLen=${decoded?.length ?: 0} decodeMs=${perfNow() - t0}")
            if (!decoded.isNullOrBlank()) {
                val escaped = decoded
                    .replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                return "<pre style=\"white-space: pre-wrap; font-family: inherit; margin: 0;\">$escaped</pre>"
            }
        } else {
            Log.d(PERF_TAG, "[EXTRACT_BODY] NO_PLAIN_PART result=null")
        }

        return null
    }

    private fun findPartByMimeType(payload: Payload, targetMimeType: String): Payload? {
        val bodyData = payload.body
        if (bodyData != null && bodyData.attachmentId == null && payload.filename.isNullOrEmpty()) {
            if (payload.mimeType?.equals(targetMimeType, ignoreCase = true) == true) {
                if (!bodyData.data.isNullOrBlank()) {
                    return payload
                }
            }
        }
        payload.parts?.forEach { part ->
            val found = findPartByMimeType(part, targetMimeType)
            if (found != null) return found
        }
        return null
    }

    private fun decodeBase64Url(data: String): String {
        // DEBUG_PERF
        val t0 = perfNow()
        val clean = data.filter { !it.isWhitespace() }
        val bytes = android.util.Base64.decode(clean, android.util.Base64.URL_SAFE)
        val result = String(bytes, Charsets.UTF_8)
        Log.d(PERF_TAG, "[DECODE_B64URL] inputLen=${data.length} cleanLen=${clean.length} outputLen=${result.length} durationMs=${perfNow() - t0}")
        return result
    }

    // ── actions ─────────────────────────────────────────────────

    override suspend fun moveToTrash(emailId: String) {
        client.post("users/me/messages/$emailId/trash")
    }

    override suspend fun restoreFromTrash(emailId: String) {
        client.post("users/me/messages/$emailId/untrash")
    }

    override suspend fun deletePermanently(emailId: String) {
        client.delete("users/me/messages/$emailId")
    }

    override suspend fun markAsRead(emailId: String) {
        client.post("users/me/messages/$emailId/modify") {
            setBody(ModifyRequest(removeLabelIds = listOf("UNREAD")))
        }
    }

    // ── attachment download ──────────────────────────────────────

    override suspend fun downloadAttachment(
        emailId: String,
        attachmentId: String
    ): ByteArray {
        return fetchAttachmentBytes(emailId, attachmentId)
    }

    // ── profile ─────────────────────────────────────────────────

    override suspend fun getUserEmail(): String? {
        return try {
            val response: ProfileResponse = client.get("users/me/profile").body()
            response.emailAddress
        } catch (_: Exception) {
            null
        }
    }

    // ── send ────────────────────────────────────────────────────

    override suspend fun sendEmail(
        to: String,
        cc: String?,
        bcc: String?,
        subject: String,
        body: String,
        replyContext: ReplyContext?
    ) {
        val fromAddress = getUserEmail() ?: throw IllegalStateException("No se pudo obtener la dirección del remitente")

        val rawMime = buildMimeMessage(
            from = fromAddress,
            to = to,
            cc = cc,
            bcc = bcc,
            subject = subject,
            body = body,
            replyContext = replyContext
        )

        val encoded = java.util.Base64.getUrlEncoder().encodeToString(
            rawMime.toByteArray(Charsets.UTF_8)
        )

        client.post("users/me/messages/send") {
            setBody(SendRequest(raw = encoded, threadId = replyContext?.threadId))
        }
    }

    /**
     * Construye un mensaje MIME RFC 2822 mínimo con cuerpo text/plain.
     */
    private fun buildMimeMessage(
        from: String,
        to: String,
        cc: String?,
        bcc: String?,
        subject: String,
        body: String,
        replyContext: ReplyContext?
    ): String = buildString {
        append("From: $from\r\n")
        append("To: $to\r\n")
        if (!cc.isNullOrBlank()) append("Cc: $cc\r\n")
        if (!bcc.isNullOrBlank()) append("Bcc: $bcc\r\n")
        append("Subject: =?UTF-8?B?").append(
            java.util.Base64.getEncoder().encodeToString(
                subject.toByteArray(Charsets.UTF_8)
            )
        ).append("?=\r\n")
        append("MIME-Version: 1.0\r\n")
        append("Content-Type: text/plain; charset=UTF-8\r\n")
        append("Content-Transfer-Encoding: base64\r\n")
        val inReplyTo = replyContext?.inReplyTo
        val references = replyContext?.references
        if (!inReplyTo.isNullOrBlank()) {
            append("In-Reply-To: $inReplyTo\r\n")
        }
        if (!references.isNullOrBlank()) {
            append("References: $references\r\n")
        }
        append("\r\n")
        append(
            java.util.Base64.getEncoder().encodeToString(
                body.toByteArray(Charsets.UTF_8)
            )
        )
    }

    // ── mapping ─────────────────────────────────────────────────

    /**
     * Pure mapping from Gmail's [MessageResponse] to our [Email] domain model.
     *
     * Uses case-insensitive header lookup, deterministic defaults, and
     * preserves RFC Message-ID/References for threading.
     */
    private fun MessageResponse.toDomain(): Email = toDomainEmail()
}
