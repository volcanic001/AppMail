package com.david.mailapp.data.repository

import android.os.SystemClock
import android.util.Log
import com.david.mailapp.data.local.MailDatabase
import com.david.mailapp.data.local.entity.EmailEntity
import com.david.mailapp.data.pdf.PdfCacheManager
import com.david.mailapp.data.pdf.PdfDownloadFailure
import com.david.mailapp.data.pdf.PdfDownloadState
import com.david.mailapp.data.remote.provider.BodyFetchResult
import com.david.mailapp.data.remote.provider.EmailLookupFailureReason
import com.david.mailapp.data.remote.provider.EmailLookupResult
import com.david.mailapp.data.remote.provider.EmailProvider
import com.david.mailapp.data.remote.provider.ReplyContext
import com.david.mailapp.data.remote.provider.InlineImageRef
import com.david.mailapp.domain.model.Email
import com.david.mailapp.domain.model.EmailFolder
import com.david.mailapp.domain.model.PaginatedResult
import com.david.mailapp.domain.model.PdfAttachmentMetadata
import com.david.mailapp.core.network.OAuthSessionExpiredException
import com.david.mailapp.core.session.SessionWriteGuard
import com.david.mailapp.core.session.SessionWriteLease
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow

// DEBUG_PERF
private const val REPO_TAG = "MailPerfTrace"
private const val RESOLVE_TAG = "EmailResolve"
private fun repoNow() = SystemClock.elapsedRealtime()

/**
 * Single source of truth for email data.
 *
 * Read path:  Room (Flow) → ViewModel (always live, always cached)
 * Write path: ViewModel → Repository → Provider (remote) → Room (cache)
 *
 * The UI observes [getInbox] / [getTrash] which emit from Room instantly.
 * [refreshInbox] / [refreshTrash] fetch from the provider and update Room
 * in the background — the Flow automatically emits the new data.
 */
class EmailRepository(
    private val database: MailDatabase,
    private val providerFactory: () -> EmailProvider?,
    private val pdfCacheManager: PdfCacheManager,
    private val writeGuard: SessionWriteGuard
) {
    private val dao = database.emailDao()

    /** Simple provider delegation for search, account and send operations. */
    private val providerGateway = EmailProviderGateway(providerFactory)

    /** Live Room-backed reads and refresh coordination for the mailbox folders. */
    private val mailboxCoordinator = EmailMailboxCoordinator(dao, providerFactory, writeGuard)

    /** Remote-first mutators with local commit and reconciliation. */
    private val actionCoordinator = EmailActionCoordinator(dao, providerFactory, writeGuard)

    /** Body fetch, HTML cleanup, PDF metadata encoding and Room persistence. */
    private val contentCoordinator = EmailContentCoordinator(dao, providerFactory, writeGuard)

    /** Current provider — read via factory every time so it stays fresh after sign-in/sign-out. */
    private val provider: EmailProvider? get() = providerFactory()

    /** Single-flight pending resolutions keyed by (sessionGeneration, emailId). Cleaned up on completion, cancellation, or session change. */
    private val pendingResolutions = ConcurrentHashMap<Pair<Long, String>, CompletableDeferred<EmailResolutionResult>>()

    /** Wrapper so writeGuard.commit(null) ≠ commit(read=null) — distinguishes session-changed from no-row. */
    private data class CachedRead(val entity: EmailEntity?)

    // ── Resolution (Subfase 2) ───────────────────────────────────

    /**
     * Resolves an email by id: cache-first, then remote via [EmailProvider.fetchEmailById],
     * then persistence to Room.  The local read is executed inside [writeGuard.commit]
     * so stale cache can never be delivered after a session change.
     *
     * Single-flight per (sessionGeneration, id): concurrent calls for the same id
     * within the same session share one resolution. Cancellation of a follower does
     * not cancel the leader; cancellation of the leader cleans the flight entry and
     * allows a later retry. A new session never joins a flight from a prior session.
     */
    suspend fun resolveEmailById(emailId: String): EmailResolutionResult {
        val t0 = repoNow()

        if (emailId.isBlank()) {
            logResolve(emailId, null, t0, "INVALID_ID")
            return EmailResolutionResult.Failure(EmailResolutionFailureReason.INVALID_ID)
        }

        val lease = writeGuard.capture()
        if (lease == null) {
            logResolve(emailId, null, t0, "NO_ACTIVE_ACCOUNT")
            return EmailResolutionResult.Failure(EmailResolutionFailureReason.NO_ACTIVE_ACCOUNT)
        }

        val flightKey = lease.generation to emailId

        // Single-flight: atomically register or join an existing flight
        val newDeferred = CompletableDeferred<EmailResolutionResult>()
        val existing = pendingResolutions.putIfAbsent(flightKey, newDeferred)

        if (existing != null) {
            // Follower — wait on the leader's deferred (own cancellation does not cancel the leader)
            logResolve(emailId, null, t0, "JOIN_SINGLE_FLIGHT")
            return try {
                existing.await()
            } catch (e: CancellationException) {
                throw e
            }
        }

        // Leader
        try {
            val result = resolveInternal(emailId, lease, t0)
            newDeferred.complete(result)
            return result
        } catch (e: CancellationException) {
            newDeferred.cancel(e)
            throw e
        } catch (e: Exception) {
            val failure = EmailResolutionResult.Failure(EmailResolutionFailureReason.INVALID_RESPONSE)
            newDeferred.complete(failure)
            return failure
        } finally {
            pendingResolutions.remove(flightKey, newDeferred)
        }
    }

    private suspend fun resolveInternal(
        emailId: String,
        lease: SessionWriteLease,
        t0: Long
    ): EmailResolutionResult {
        // Guarded local read: commit validates the lease; returns null → session changed
        val read = try {
            writeGuard.commit(lease) {
                CachedRead(dao.getByIdOnce(emailId))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logResolve(emailId, null, t0, "LOCAL_READ_FAILED")
            return EmailResolutionResult.Failure(EmailResolutionFailureReason.LOCAL_READ_FAILED)
        }

        if (read == null) {
            logResolve(emailId, null, t0, "SESSION_CHANGED")
            return EmailResolutionResult.Failure(EmailResolutionFailureReason.SESSION_CHANGED)
        }

        val cached = read.entity
        if (cached != null) {
            logResolve(emailId, "cache", t0, "FOUND")
            return EmailResolutionResult.Found(cached.toDomain())
        }

        // Cache miss → remote
        val provider = providerFactory()
        if (provider == null) {
            logResolve(emailId, "remote", t0, "NO_ACTIVE_ACCOUNT")
            return EmailResolutionResult.Failure(EmailResolutionFailureReason.NO_ACTIVE_ACCOUNT)
        }

        val lookupResult = try {
            provider.fetchEmailById(emailId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: OAuthSessionExpiredException) {
            EmailLookupResult.Failure(EmailLookupFailureReason.SESSION_EXPIRED)
        } catch (e: IOException) {
            EmailLookupResult.Failure(EmailLookupFailureReason.NO_CONNECTION)
        } catch (e: Exception) {
            EmailLookupResult.Failure(EmailLookupFailureReason.INVALID_RESPONSE)
        }

        return when (lookupResult) {
            is EmailLookupResult.NotFound -> {
                logResolve(emailId, "remote", t0, "NOT_FOUND")
                EmailResolutionResult.NotFound
            }
            is EmailLookupResult.Failure -> {
                val reason = mapLookupFailure(lookupResult.reason)
                logResolve(emailId, "remote", t0, lookupResult.reason.name)
                EmailResolutionResult.Failure(reason)
            }
            is EmailLookupResult.Found -> {
                val email = lookupResult.email
                val entity = EmailEntity.fromDomain(email, email.folder)
                try {
                    val persisted = writeGuard.commit(lease) {
                        dao.upsertWithMerge(entity)
                    }
                    if (persisted == null) {
                        logResolve(emailId, "remote", t0, "SESSION_CHANGED")
                        EmailResolutionResult.Failure(EmailResolutionFailureReason.SESSION_CHANGED)
                    } else {
                        logResolve(emailId, "remote", t0, "FOUND")
                        EmailResolutionResult.Found(persisted.toDomain())
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logResolve(emailId, "remote", t0, "LOCAL_WRITE_FAILED")
                    EmailResolutionResult.Failure(EmailResolutionFailureReason.LOCAL_WRITE_FAILED)
                }
            }
        }
    }

    private fun mapLookupFailure(reason: EmailLookupFailureReason): EmailResolutionFailureReason = when (reason) {
        EmailLookupFailureReason.NO_CONNECTION -> EmailResolutionFailureReason.NO_CONNECTION
        EmailLookupFailureReason.SESSION_EXPIRED -> EmailResolutionFailureReason.SESSION_EXPIRED
        EmailLookupFailureReason.TEMPORARY_REMOTE -> EmailResolutionFailureReason.TEMPORARY_REMOTE
        EmailLookupFailureReason.REMOTE_REJECTED -> EmailResolutionFailureReason.REMOTE_REJECTED
        EmailLookupFailureReason.INVALID_RESPONSE -> EmailResolutionFailureReason.INVALID_RESPONSE
    }

    private fun logResolve(emailId: String, source: String?, t0: Long, category: String) {
        Log.d(RESOLVE_TAG, "[RESOLVE] RESULT id=$emailId source=${source ?: "-"} durationMs=${repoNow() - t0} category=$category")
    }

    // ── Read (always from cache) ─────────────────────────────────

    fun getInbox(): Flow<List<Email>> = mailboxCoordinator.getInbox()

    fun getTrash(): Flow<List<Email>> = mailboxCoordinator.getTrash()

    fun getEmailById(emailId: String): Flow<Email?> = mailboxCoordinator.getEmailById(emailId)

    // ── Write (remote → cache) ──────────────────────────────────

    /** Fetch from provider and persist to Room. Returns the paginated result for UI pagination. */
    suspend fun refreshInbox(pageToken: String? = null): PaginatedResult<Email> =
        mailboxCoordinator.refreshInbox(pageToken)

    suspend fun refreshTrash(pageToken: String? = null): PaginatedResult<Email> =
        mailboxCoordinator.refreshTrash(pageToken)

    /**
     * Remote search — NOT cached in Room. Results are ephemeral
     * and live only in the ViewModel's state.
     */
    suspend fun searchEmails(query: String, pageToken: String? = null): PaginatedResult<Email> =
        providerGateway.searchEmails(query, pageToken)

    suspend fun moveToTrash(emailId: String): EmailActionResult =
        actionCoordinator.moveToTrash(emailId)

    suspend fun restoreFromTrash(emailId: String): EmailActionResult =
        actionCoordinator.restoreFromTrash(emailId)

    suspend fun deletePermanently(emailId: String): EmailActionResult =
        actionCoordinator.deletePermanently(emailId)

    suspend fun markAsRead(emailId: String): EmailActionResult =
        actionCoordinator.markAsRead(emailId)

    /** Fetch the full HTML body along with inline image refs and PDF metadata from the provider,
     * then persist everything atomically to Room. Metadata is persisted even when the body is empty. */
    suspend fun fetchAndCacheBody(emailId: String): BodyFetchResult? =
        contentCoordinator.fetchAndCacheBody(emailId)

    suspend fun downloadInlineImages(emailId: String, refs: List<InlineImageRef>): Map<String, String> =
        contentCoordinator.downloadInlineImages(emailId, refs)

    fun injectInlineImages(html: String, inlineImages: Map<String, String>): String =
        contentCoordinator.injectInlineImages(html, inlineImages)

    // ── PDF download ───────────────────────────────────────────

    /**
     * Descarga un PDF adjunto, lo valida y lo guarda en caché.
     *
     * Pre-validación (sin red):
     * - MIME == application/pdf
     * - fileName termina en .pdf (ignoreCase)
     * - attachmentId no vacío
     * - tamaño declarado ≤ MAX_PDF_SIZE (cuando esté disponible)
     *
     * Post-validación (tras descargar):
     * - contenido no vacío
     * - tamaño real ≤ MAX_PDF_SIZE
     * - primeros 5 bytes == %PDF-
     *
     * Si el archivo ya está en caché y es válido, devuelve Ready sin red.
     */
    suspend fun downloadPdf(
        emailId: String,
        metadata: PdfAttachmentMetadata
    ): PdfDownloadState {
        // ── Pre-validación ──────────────────────────────────────
        if (metadata.mimeType != "application/pdf") {
            return PdfDownloadState.Error(PdfDownloadFailure.INVALID_PDF)
        }
        if (!metadata.fileName.endsWith(".pdf", ignoreCase = true)) {
            return PdfDownloadState.Error(PdfDownloadFailure.INVALID_PDF)
        }
        if (metadata.attachmentId.isBlank()) {
            return PdfDownloadState.Error(PdfDownloadFailure.INVALID_PDF)
        }
        val declaredSize = metadata.sizeBytes
        if (declaredSize != null && declaredSize > MAX_PDF_SIZE) {
            Log.w(REPO_TAG, "[PDF_DOWNLOAD] DECLARED_TOO_LARGE emailId=$emailId " +
                "attachmentId=${metadata.attachmentId} declaredSize=$declaredSize")
            return PdfDownloadState.Error(PdfDownloadFailure.TOO_LARGE)
        }

        // Capture before any cache mutation or network request. A lease obtained
        // after the download could belong to a different, newly signed-in account.
        val lease = writeGuard.capture()
            ?: return PdfDownloadState.Error(PdfDownloadFailure.NO_PROVIDER)

        // ── Cache check ─────────────────────────────────────────
        val stableId = metadata.stableId
        val cachedFile = pdfCacheManager.getCachedFile(emailId, stableId)
        if (cachedFile != null && isValidPdfFile(cachedFile)) {
            Log.d(REPO_TAG, "[PDF_DOWNLOAD] CACHE_HIT emailId=$emailId " +
                "attachmentId=${metadata.attachmentId} size=${cachedFile.length()}")
            return PdfDownloadState.Ready(cachedFile.length())
        }
        // Cache inválido — limpiar
        if (cachedFile != null) {
            Log.d(REPO_TAG, "[PDF_DOWNLOAD] CACHE_INVALID removing emailId=$emailId " +
                "attachmentId=${metadata.attachmentId}")
            val removed = writeGuard.commit(lease) {
                cachedFile.delete()
                pdfCacheManager.delete(emailId, stableId)
                true
            } ?: false
            if (!removed) {
                return PdfDownloadState.Error(PdfDownloadFailure.NO_PROVIDER)
            }
        }

        // ── Download ────────────────────────────────────────────
        val p = provider
        if (p == null) {
            return PdfDownloadState.Error(PdfDownloadFailure.NO_PROVIDER)
        }

        val bytes: ByteArray
        try {
            Log.d(REPO_TAG, "[PDF_DOWNLOAD] DOWNLOADING emailId=$emailId " +
                "attachmentId=${metadata.attachmentId}")
            bytes = p.downloadAttachment(emailId, metadata.attachmentId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(REPO_TAG, "[PDF_DOWNLOAD] NETWORK_ERROR emailId=$emailId " +
                "attachmentId=${metadata.attachmentId} error=${e.message}", e)
            return PdfDownloadState.Error(PdfDownloadFailure.NETWORK)
        }

        // ── Post-validación ─────────────────────────────────────
        if (bytes.isEmpty()) {
            Log.w(REPO_TAG, "[PDF_DOWNLOAD] EMPTY_CONTENT emailId=$emailId " +
                "attachmentId=${metadata.attachmentId}")
            return PdfDownloadState.Error(PdfDownloadFailure.EMPTY_CONTENT)
        }

        if (bytes.size.toLong() > MAX_PDF_SIZE) {
            Log.w(REPO_TAG, "[PDF_DOWNLOAD] ACTUAL_TOO_LARGE emailId=$emailId " +
                "attachmentId=${metadata.attachmentId} actualSize=${bytes.size}")
            return PdfDownloadState.Error(PdfDownloadFailure.TOO_LARGE)
        }

        if (!hasPdfMagic(bytes)) {
            Log.w(REPO_TAG, "[PDF_DOWNLOAD] INVALID_PDF emailId=$emailId " +
                "attachmentId=${metadata.attachmentId} firstBytes=${bytes.take(5).joinToString("") { "%02x".format(it) }}")
            return PdfDownloadState.Error(PdfDownloadFailure.INVALID_PDF)
        }

        // ── Store ───────────────────────────────────────────────
        return try {
            val file = writeGuard.commit(lease) { pdfCacheManager.store(emailId, stableId, bytes) }
                ?: return PdfDownloadState.Error(PdfDownloadFailure.NO_PROVIDER)
            Log.d(REPO_TAG, "[PDF_DOWNLOAD] SUCCESS emailId=$emailId " +
                "attachmentId=${metadata.attachmentId} size=${file.length()}")
            PdfDownloadState.Ready(file.length())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(REPO_TAG, "[PDF_DOWNLOAD] CACHE_WRITE_ERROR emailId=$emailId " +
                "attachmentId=${metadata.attachmentId} error=${e.message}", e)
            PdfDownloadState.Error(PdfDownloadFailure.CACHE_WRITE)
        }
    }

    /**
     * Consulta si un PDF ya está descargado y válido en caché,
     * sin hacer llamadas de red.
     */
    suspend fun isPdfCached(emailId: String, stablePartId: String): Boolean {
        val file = pdfCacheManager.getCachedFile(emailId, stablePartId)
        return file != null && isValidPdfFile(file)
    }

    /**
     * Devuelve [PdfDownloadState.Ready] si el adjunto está cacheadoy válido,
     * o null si no está en caché. Sin llamadas de red.
     */
    suspend fun checkPdfCache(emailId: String, stablePartId: String): PdfDownloadState.Ready? {
        val file = pdfCacheManager.getCachedFile(emailId, stablePartId)
        return if (file != null && isValidPdfFile(file)) {
            PdfDownloadState.Ready(file.length())
        } else null
    }

    /**
     * Devuelve el archivo cacheadoy validado para abrir con el visor externo.
     * Si el archivo no existe o no supera la validación, devuelve null.
     */
    suspend fun getValidatedCachedPdf(emailId: String, stablePartId: String): File? {
        val file = pdfCacheManager.getCachedFile(emailId, stablePartId)
        return if (file != null && isValidPdfFile(file)) file else null
    }

    // ── Helpers ────────────────────────────────────────────────

    companion object {
        /** Límite máximo: 25 MiB. */
        const val MAX_PDF_SIZE = 26_214_400L

        /** Firma PDF: %PDF- */
        private val PDF_MAGIC = byteArrayOf(0x25, 0x50, 0x44, 0x46, 0x2D)

        private fun isValidPdfFile(file: File): Boolean {
            if (!file.exists() || file.length() == 0L) return false
            if (file.length() > MAX_PDF_SIZE) return false
            return runCatching {
                FileInputStream(file).use { input ->
                    val header = ByteArray(PDF_MAGIC.size)
                    var offset = 0
                    while (offset < header.size) {
                        val read = input.read(header, offset, header.size - offset)
                        if (read < 0) return@use false
                        offset += read
                    }
                    hasPdfMagic(header)
                }
            }.getOrDefault(false)
        }

        private fun hasPdfMagic(bytes: ByteArray): Boolean {
            if (bytes.size < 5) return false
            // Compare byte-by-byte para evitar alocación extra y ser compatible
            return bytes[0] == PDF_MAGIC[0] && bytes[1] == PDF_MAGIC[1] &&
                bytes[2] == PDF_MAGIC[2] && bytes[3] == PDF_MAGIC[3] &&
                bytes[4] == PDF_MAGIC[4]
        }
    }

    /** Obtiene la dirección de email del usuario autenticado desde el provider. */
    suspend fun getUserEmail(): String? = providerGateway.getUserEmail()

    suspend fun sendEmail(
        to: String, cc: String?, bcc: String?,
        subject: String, body: String,
        replyContext: ReplyContext? = null
    ) {
        providerGateway.sendEmail(to, cc, bcc, subject, body, replyContext)
    }
}
