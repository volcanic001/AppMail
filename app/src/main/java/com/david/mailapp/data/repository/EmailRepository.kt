package com.david.mailapp.data.repository

import android.os.SystemClock
import android.util.Log
import com.david.mailapp.data.local.MailDatabase
import com.david.mailapp.data.local.converter.PdfAttachmentMetadataCodec
import com.david.mailapp.data.local.entity.EmailEntity
import com.david.mailapp.data.pdf.PdfCacheManager
import com.david.mailapp.data.pdf.PdfDownloadFailure
import com.david.mailapp.data.pdf.PdfDownloadState
import com.david.mailapp.data.remote.provider.BodyFetchResult
import com.david.mailapp.data.remote.provider.EmailProvider
import com.david.mailapp.data.remote.provider.ReplyContext
import com.david.mailapp.data.remote.provider.InlineImageRef
import com.david.mailapp.domain.model.Email
import com.david.mailapp.domain.model.EmailFolder
import com.david.mailapp.domain.model.PaginatedResult
import com.david.mailapp.domain.model.PdfAttachmentMetadata
import com.david.mailapp.core.localization.UiErrorReason
import com.david.mailapp.core.localization.toUiErrorReason
import com.david.mailapp.core.session.SessionWriteGuard
import com.david.mailapp.core.session.SessionWriteLease
import com.david.mailapp.feature.emaildetail.components.EmailHtmlCleaner
import java.io.File
import java.io.FileInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

// DEBUG_PERF
private const val REPO_TAG = "MailPerfTrace"
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

    /** Current provider — read via factory every time so it stays fresh after sign-in/sign-out. */
    private val provider: EmailProvider? get() = providerFactory()

    // ── Read (always from cache) ─────────────────────────────────

    fun getInbox(): Flow<List<Email>> {
        return dao.getByFolder("inbox").map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun getTrash(): Flow<List<Email>> {
        return dao.getByFolder("trash").map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun getEmailById(emailId: String): Flow<Email?> {
        return dao.getById(emailId).map { entity -> entity?.toDomain() }
    }

    // ── Write (remote → cache) ──────────────────────────────────

    /** Fetch from provider and persist to Room. Returns the paginated result for UI pagination. */
    suspend fun refreshInbox(pageToken: String? = null): PaginatedResult<Email> {
        val lease = writeGuard.capture() ?: return PaginatedResult(emptyList(), null)
        val p = provider ?: return PaginatedResult(emptyList(), null)
        val fetched = p.fetchInbox(pageToken)
        val result = if (fetched.isComplete) fetched else fetched.copy(nextPageToken = null)

        val entities = result.items.map { EmailEntity.fromDomain(it, EmailFolder.Inbox) }
        writeGuard.commit(lease) {
            // Replace folder only on a complete first page;
            // partial pages or pagination append/merge.
            if (pageToken == null && result.isComplete) {
                dao.replaceFolder("inbox", entities)
            } else {
                dao.upsertPreservingBodies(entities)
            }
        }

        return result
    }

    suspend fun refreshTrash(pageToken: String? = null): PaginatedResult<Email> {
        val lease = writeGuard.capture() ?: return PaginatedResult(emptyList(), null)
        val p = provider ?: return PaginatedResult(emptyList(), null)
        val fetched = p.fetchTrash(pageToken)
        val result = if (fetched.isComplete) fetched else fetched.copy(nextPageToken = null)

        val entities = result.items.map { EmailEntity.fromDomain(it, EmailFolder.Trash) }
        writeGuard.commit(lease) {
            // Refresh replaces the paginated window only for a complete first page.
            // Partial pages and subsequent pages can only merge into the cache.
            if (pageToken == null && result.isComplete) {
                dao.replaceFolder("trash", entities)
            } else {
                dao.upsertPreservingBodies(entities)
            }
        }

        return result
    }

    /**
     * Remote search — NOT cached in Room. Results are ephemeral
     * and live only in the ViewModel's state.
     */
    suspend fun searchEmails(query: String, pageToken: String? = null): PaginatedResult<Email> {
        val p = provider ?: return PaginatedResult(emptyList(), null)
        return p.search(query, pageToken)
    }

    suspend fun moveToTrash(emailId: String): EmailActionResult {
        val lease = writeGuard.capture() ?: return EmailActionResult.Failure(
            UiErrorReason.NO_ACTIVE_ACCOUNT, remoteApplied = false)
        val p = provider ?: return EmailActionResult.Failure(
            UiErrorReason.NO_ACTIVE_ACCOUNT, remoteApplied = false)

        // 1. Remote first
        try {
            p.moveToTrash(emailId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return EmailActionResult.Failure(e.toUiErrorReason(), remoteApplied = false)
        }

        // 2. Local write with exception/rejection handling
        return commitWithReconcile(lease, p, folders = listOf("inbox", "trash")) {
            dao.moveToFolder(emailId, "trash")
        }
    }

    suspend fun restoreFromTrash(emailId: String): EmailActionResult {
        val lease = writeGuard.capture() ?: return EmailActionResult.Failure(
            UiErrorReason.NO_ACTIVE_ACCOUNT, remoteApplied = false)
        val p = provider ?: return EmailActionResult.Failure(
            UiErrorReason.NO_ACTIVE_ACCOUNT, remoteApplied = false)

        try {
            p.restoreFromTrash(emailId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return EmailActionResult.Failure(e.toUiErrorReason(), remoteApplied = false)
        }

        return commitWithReconcile(lease, p, folders = listOf("trash", "inbox")) {
            dao.moveToFolder(emailId, "inbox")
        }
    }

    suspend fun deletePermanently(emailId: String): EmailActionResult {
        val lease = writeGuard.capture() ?: return EmailActionResult.Failure(
            UiErrorReason.NO_ACTIVE_ACCOUNT, remoteApplied = false)
        val p = provider ?: return EmailActionResult.Failure(
            UiErrorReason.NO_ACTIVE_ACCOUNT, remoteApplied = false)

        try {
            p.deletePermanently(emailId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return EmailActionResult.Failure(e.toUiErrorReason(), remoteApplied = false)
        }

        return commitWithReconcile(lease, p, folders = listOf("trash")) {
            dao.deleteById(emailId)
        }
    }

    suspend fun markAsRead(emailId: String): EmailActionResult {
        val lease = writeGuard.capture() ?: return EmailActionResult.Failure(
            UiErrorReason.NO_ACTIVE_ACCOUNT, remoteApplied = false)
        val p = provider ?: return EmailActionResult.Failure(
            UiErrorReason.NO_ACTIVE_ACCOUNT, remoteApplied = false)

        try {
            p.markAsRead(emailId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return EmailActionResult.Failure(e.toUiErrorReason(), remoteApplied = false)
        }

        return commitWithReconcile(lease, p, folders = listOf("inbox")) {
            dao.updateReadStatus(emailId, isRead = true)
        }
    }

    // ── Commit helper (best-effort reconciliation on local failure) ──

    /**
     * Attempts the local [block] via [writeGuard.commit].
     *
     * - Successful commit → [EmailActionResult.Success].
     * - Null/exception commit → reconciliation for [folders] → Failure(UNKNOWN, true).
     * - CancellationException during commit → rethrown (not reconciled).
     * - CancellationException during reconciliation → rethrown.
     */
    private suspend fun commitWithReconcile(
        lease: SessionWriteLease,
        provider: EmailProvider,
        folders: List<String>,
        block: suspend () -> Unit
    ): EmailActionResult {
        val commitResult = try {
            writeGuard.commit(lease) { block(); true }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(REPO_TAG, "Local commit failed after remote success", e)
            null
        }

        if (commitResult == true) return EmailActionResult.Success

        // Reconcile in folder order; each folder in its own try
        for (folder in folders) {
            try {
                reconcileFolder(provider, lease, folder)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(REPO_TAG, "Reconcile $folder failed after remote success", e)
            }
        }

        return EmailActionResult.Failure(UiErrorReason.UNKNOWN, remoteApplied = true)
    }

    private suspend fun reconcileFolder(
        p: EmailProvider,
        lease: SessionWriteLease,
        folder: String
    ) {
        val result = when (folder) {
            "inbox" -> p.fetchInbox(null)
            "trash" -> p.fetchTrash(null)
            else -> return
        }
        writeGuard.commit(lease) {
            val entities = result.items.map {
                EmailEntity.fromDomain(it, if (folder == "inbox") EmailFolder.Inbox else EmailFolder.Trash)
            }
            if (result.isComplete) {
                dao.replaceFolder(folder, entities)
            } else {
                dao.upsertPreservingBodies(entities)
            }
        }
    }

    /** Fetch the full HTML body along with inline image refs and PDF metadata from the provider,
     * then persist everything atomically to Room. Metadata is persisted even when the body is empty. */
    suspend fun fetchAndCacheBody(emailId: String): BodyFetchResult? {
        // DEBUG_PERF
        val t0 = repoNow()
        Log.d(REPO_TAG, "[REPO_BODY] START emailId=$emailId")
        val lease = writeGuard.capture() ?: run {
            Log.d(REPO_TAG, "[REPO_BODY] GUARD_INVALIDATED emailId=$emailId")
            return null
        }
        val result = provider?.fetchBodyWithRefs(emailId) ?: run {
            Log.d(REPO_TAG, "[REPO_BODY] NO_PROVIDER_OR_FAILED emailId=$emailId")
            return null
        }
        val rawBody = result.rawBody.orEmpty()
        val tFetch = repoNow()
        Log.d(REPO_TAG, "[REPO_BODY] FETCHED emailId=$emailId bodyLen=${rawBody.length} refs=${result.inlineRefs.size} pdfs=${result.pdfAttachments.size} fetchMs=${tFetch - t0}")

        // Clean HTML only when there's a body
        val cleanBody = if (rawBody.isNotBlank()) {
            withContext(Dispatchers.Default) {
                EmailHtmlCleaner.clean(rawBody)
            }
        } else ""

        val pdfJson = PdfAttachmentMetadataCodec.encode(result.pdfAttachments)
        val hasAtt = result.pdfAttachments.isNotEmpty()

        writeGuard.commit(lease) {
            dao.updateBodyAndPdfMetadata(
                emailId = emailId,
                body = rawBody,
                cleanBody = cleanBody,
                pdfAttachmentsJson = pdfJson,
                hasAttachments = hasAtt
            )
        }
        Log.d(REPO_TAG, "[REPO_BODY] CACHED emailId=$emailId roomMs=${repoNow() - tFetch} totalMs=${repoNow() - t0}")
        return result
    }

    suspend fun downloadInlineImages(emailId: String, refs: List<InlineImageRef>): Map<String, String> {
        if (refs.isEmpty()) return emptyMap()
        // DEBUG_PERF
        val t0 = repoNow()
        Log.d(REPO_TAG, "[REPO_INLINE] START emailId=$emailId count=${refs.size}")
        val result = provider?.downloadInlineImages(emailId, refs) ?: emptyMap()
        Log.d(REPO_TAG, "[REPO_INLINE] DONE emailId=$emailId count=${result.size} totalMs=${repoNow() - t0}")
        return result
    }

    fun injectInlineImages(html: String, inlineImages: Map<String, String>): String {
        if (inlineImages.isEmpty()) {
            // DEBUG_PERF
            Log.d(REPO_TAG, "[REPO_INJECT] SKIP reason=no_inline_images htmlLen=${html.length}")
            return html
        }
        // DEBUG_PERF
        val t0 = repoNow()
        Log.d(REPO_TAG, "[REPO_INJECT] START htmlLen=${html.length} imageCount=${inlineImages.size}")
        var result = html
        for ((cid, dataUri) in inlineImages) {
            result = result
                .replace("cid:$cid", dataUri)
                .replace("cid:&lt;$cid&gt;", dataUri)
                .replace("cid:<$cid>", dataUri)
        }
        Log.d(REPO_TAG, "[REPO_INJECT] DONE outputLen=${result.length} durationMs=${repoNow() - t0}")
        return result
    }

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
    suspend fun getUserEmail(): String? = provider?.getUserEmail()

    suspend fun sendEmail(
        to: String, cc: String?, bcc: String?,
        subject: String, body: String,
        replyContext: ReplyContext? = null
    ) {
        provider?.sendEmail(to, cc, bcc, subject, body, replyContext)
            ?: error("No hay proveedor activo")
    }
}
