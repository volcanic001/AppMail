package com.david.mailapp.data.repository

import com.david.mailapp.data.local.MailDatabase
import com.david.mailapp.data.pdf.PdfCacheManager
import com.david.mailapp.data.pdf.PdfDownloadState
import com.david.mailapp.data.remote.provider.BodyFetchResult
import com.david.mailapp.data.remote.provider.EmailProvider
import com.david.mailapp.data.remote.provider.ReplyContext
import com.david.mailapp.domain.model.Email
import com.david.mailapp.domain.model.PaginatedResult
import com.david.mailapp.domain.model.PdfAttachmentMetadata
import com.david.mailapp.core.session.SessionWriteGuard
import java.io.File
import kotlinx.coroutines.flow.Flow

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

    /** PDF download, cache queries and binary validation. */
    private val pdfCoordinator =
        EmailPdfCoordinator(pdfCacheManager, MAX_PDF_SIZE, providerFactory, writeGuard)

    /** Cache-first resolution with single-flight deduplication and session isolation. */
    private val resolutionCoordinator = EmailResolutionCoordinator(dao, providerFactory, writeGuard)

    // ── Resolution ──────────────────────────────────────────────

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
    suspend fun resolveEmailById(emailId: String): EmailResolutionResult =
        resolutionCoordinator.resolveEmailById(emailId)

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

    suspend fun downloadInlineImages(emailId: String, refs: List<com.david.mailapp.domain.model.EmailInlineReference>): Map<String, String> =
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
    ): PdfDownloadState = pdfCoordinator.downloadPdf(emailId, metadata)

    /**
     * Consulta si un PDF ya está descargado y válido en caché,
     * sin hacer llamadas de red.
     */
    suspend fun isPdfCached(emailId: String, stablePartId: String): Boolean =
        pdfCoordinator.isPdfCached(emailId, stablePartId)

    /**
     * Devuelve [PdfDownloadState.Ready] si el adjunto está cacheadoy válido,
     * o null si no está en caché. Sin llamadas de red.
     */
    suspend fun checkPdfCache(emailId: String, stablePartId: String): PdfDownloadState.Ready? =
        pdfCoordinator.checkPdfCache(emailId, stablePartId)

    /**
     * Devuelve el archivo cacheadoy validado para abrir con el visor externo.
     * Si el archivo no existe o no supera la validación, devuelve null.
     */
    suspend fun getValidatedCachedPdf(emailId: String, stablePartId: String): File? =
        pdfCoordinator.getValidatedCachedPdf(emailId, stablePartId)

    // ── Helpers ────────────────────────────────────────────────

    companion object {
        /** Límite máximo: 25 MiB. */
        const val MAX_PDF_SIZE = 26_214_400L
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
