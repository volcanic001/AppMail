package com.david.mailapp.data.remote.provider.gmail

import android.os.SystemClock
import android.util.Log
import com.david.mailapp.core.network.OAuthSessionExpiredException
import com.david.mailapp.data.remote.provider.EmailLookupFailureReason
import com.david.mailapp.data.remote.provider.EmailLookupResult
import com.david.mailapp.data.remote.provider.EmailProvider
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
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

// DEBUG_PERF: tag central para filtrar en Logcat → tag:MailPerfTrace
private const val PERF_TAG = "MailPerfTrace"
private const val LOOKUP_TAG = "EmailLookup"
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
    private val client: HttpClient,
    private val lookupBackoffMillis: List<Long> = DEFAULT_LOOKUP_BACKOFF_MILLIS,
    private val lookupDelay: suspend (Long) -> Unit = { delay(it) },
    private val clock: () -> Long = { com.david.mailapp.core.perf.MailOpenPerformanceTrace.now() },
    private val networkDiagnosticSink: NetworkDiagnosticSink? = null
) : EmailProvider {

    companion object {
        /** Initial attempt + 2 retries, exclusively for network, 408, 429 and 5xx. */
        internal const val MAX_LOOKUP_ATTEMPTS = 3

        /** 250 ms after the initial attempt, 750 ms after the first retry. */
        private val DEFAULT_LOOKUP_BACKOFF_MILLIS = listOf(250L, 750L)
    }

    // ── individual recovery ────────────────────────────────────

    /**
     * Recovers a single message by id: `GET users/me/messages/{emailId}?format=full`.
     *
     * Result classification:
     *  - HTTP 200 valid → [EmailLookupResult.Found]
     *  - HTTP 404 → [EmailLookupResult.NotFound]
     *  - HTTP 401 or OAuth-detected expiration → SESSION_EXPIRED
     *  - HTTP 408/429/5xx → retried; exhausted → TEMPORARY_REMOTE
     *  - Other 4xx → REMOTE_REJECTED
     *  - IOException → retried; exhausted → NO_CONNECTION
     *  - Unparseable JSON → INVALID_RESPONSE
     *  - Cancellation → always propagated, never converted into a result.
     *
     * A blank id is treated as INVALID_RESPONSE without touching the network.
     */
    override suspend fun fetchEmailById(emailId: String): EmailLookupResult {
        if (emailId.isBlank()) {
            return EmailLookupResult.Failure(EmailLookupFailureReason.INVALID_RESPONSE)
        }

        var attempt = 0
        while (true) {
            val t0 = clock()
            attempt++
            try {
                val response: HttpResponse = requestFullMessage(client, emailId)
                when {
                    response.status == HttpStatusCode.NotFound -> {
                        logLookup(emailId, attempt, t0, "NOT_FOUND")
                        return EmailLookupResult.NotFound
                    }
                    response.status == HttpStatusCode.Unauthorized -> {
                        logLookup(emailId, attempt, t0, "SESSION_EXPIRED")
                        return EmailLookupResult.Failure(EmailLookupFailureReason.SESSION_EXPIRED)
                    }
                    isTransientHttpError(response.status.value) -> {
                        logLookup(emailId, attempt, t0, "TEMPORARY_REMOTE")
                        if (attempt < MAX_LOOKUP_ATTEMPTS) {
                            waitBeforeRetry(attempt)
                            continue
                        }
                        return EmailLookupResult.Failure(EmailLookupFailureReason.TEMPORARY_REMOTE)
                    }
                    response.status.isSuccess() -> {
                        return try {
                            val message: MessageResponse = response.body()
                            val email = message.toDomainEmail()
                            logLookup(emailId, attempt, t0, "FOUND")
                            EmailLookupResult.Found(email)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            logLookup(emailId, attempt, t0, "INVALID_RESPONSE")
                            EmailLookupResult.Failure(EmailLookupFailureReason.INVALID_RESPONSE)
                        }
                    }
                    else -> {
                        // Other 4xx (400, 403, …): rejected, never retried.
                        logLookup(emailId, attempt, t0, "REMOTE_REJECTED")
                        return EmailLookupResult.Failure(EmailLookupFailureReason.REMOTE_REJECTED)
                    }
                }
            } catch (e: CancellationException) {
                logLookup(emailId, attempt, t0, "CANCELLED")
                throw e
            } catch (e: OAuthSessionExpiredException) {
                logLookup(emailId, attempt, t0, "SESSION_EXPIRED")
                return EmailLookupResult.Failure(EmailLookupFailureReason.SESSION_EXPIRED)
            } catch (e: IOException) {
                logLookup(emailId, attempt, t0, "NO_CONNECTION")
                if (attempt < MAX_LOOKUP_ATTEMPTS) {
                    waitBeforeRetry(attempt)
                    continue
                }
                return EmailLookupResult.Failure(EmailLookupFailureReason.NO_CONNECTION)
            } catch (e: Exception) {
                logLookup(emailId, attempt, t0, "INVALID_RESPONSE")
                return EmailLookupResult.Failure(EmailLookupFailureReason.INVALID_RESPONSE)
            }
        }
    }

    private suspend fun waitBeforeRetry(attempt: Int) {
        if (lookupBackoffMillis.isEmpty()) return
        val delayMillis = lookupBackoffMillis.getOrElse(attempt - 1) { lookupBackoffMillis.last() }
        lookupDelay(delayMillis)
    }

    private fun logLookup(emailId: String, attempts: Int, t0: Long, category: String) {
        val cat = when (category) {
            "FOUND" -> DiagnosticCategory.SUCCESS
            "NOT_FOUND" -> DiagnosticCategory.NOT_FOUND
            "SESSION_EXPIRED" -> DiagnosticCategory.SESSION_EXPIRED
            "TEMPORARY_REMOTE" -> DiagnosticCategory.TRANSIENT_HTTP
            "REMOTE_REJECTED" -> DiagnosticCategory.PERMANENT_HTTP
            "NO_CONNECTION" -> DiagnosticCategory.IO
            "INVALID_RESPONSE" -> DiagnosticCategory.INVALID_RESPONSE
            "CANCELLED" -> DiagnosticCategory.CANCELLED
            else -> DiagnosticCategory.PERMANENT_HTTP
        }
        val mailKey = com.david.mailapp.core.perf.MailOpenPerformanceTrace.mailKey(emailId)
        val durationMs = clock() - t0
        networkDiagnosticSink?.invoke(NetworkDiagnosticEvent(mailKey, attempts, durationMs, cat))
    }

    // ── fetch ───────────────────────────────────────────────────

    override suspend fun fetchInbox(pageToken: String?): PaginatedResult<Email> {
        return fetchGmailPage(client, labelId = "INBOX", pageToken = pageToken, delayFn = lookupDelay, clock = clock, sink = networkDiagnosticSink)
    }

    override suspend fun fetchTrash(pageToken: String?): PaginatedResult<Email> {
        return fetchGmailPage(client, labelId = "TRASH", pageToken = pageToken, delayFn = lookupDelay, clock = clock, sink = networkDiagnosticSink)
    }

    override suspend fun search(query: String, pageToken: String?): PaginatedResult<Email> {
        val result = fetchGmailPage(client, query = query, pageToken = pageToken, delayFn = lookupDelay, clock = clock, sink = networkDiagnosticSink)
        return result
    }

    override suspend fun downloadInlineImages(
        emailId: String,
        refs: List<com.david.mailapp.domain.model.EmailInlineReference>
    ): Map<String, String> {
        // DEBUG_PERF
        if (refs.isEmpty()) return emptyMap()

        val t0 = perfNow()
        val result = coroutineScope {
            refs.mapIndexed { idx, ref ->
                async {
                    // DEBUG_PERF
                    val tImg = perfNow()
                    try {
                        val bytes = fetchAttachmentBytes(emailId, ref.attachmentId)
                        val tBytes = perfNow()
                        val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                        val tEncode = perfNow()
                        ref.contentId to "data:${ref.mimeType};base64,$b64"
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        null
                    }
                }
            }.awaitAll().mapNotNull { it }.toMap()
        }
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

        val b64Data = response.data ?: run {
            return ByteArray(0)
        }
        val bytes = android.util.Base64.decode(b64Data, android.util.Base64.URL_SAFE)
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
        } catch (e: CancellationException) {
            throw e
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
