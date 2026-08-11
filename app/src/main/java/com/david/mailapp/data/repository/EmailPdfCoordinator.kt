package com.david.mailapp.data.repository

import android.util.Log
import com.david.mailapp.core.session.SessionWriteGuard
import com.david.mailapp.data.pdf.PdfCacheManager
import com.david.mailapp.data.pdf.PdfDownloadFailure
import com.david.mailapp.data.pdf.PdfDownloadState
import com.david.mailapp.data.remote.provider.EmailProvider
import com.david.mailapp.domain.model.PdfAttachmentMetadata
import java.io.File
import java.io.FileInputStream
import kotlinx.coroutines.CancellationException

internal class EmailPdfCoordinator(
    private val pdfCacheManager: PdfCacheManager,
    private val maxPdfSize: Long,
    private val providerFactory: () -> EmailProvider?,
    private val writeGuard: SessionWriteGuard
) {
    /** Firma PDF: %PDF- */
    private val PDF_MAGIC = byteArrayOf(0x25, 0x50, 0x44, 0x46, 0x2D)

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
        if (declaredSize != null && declaredSize > maxPdfSize) {
            Log.w(RepositoryTrace.MAIL_PERF_TAG, "[PDF_DOWNLOAD] DECLARED_TOO_LARGE emailId=$emailId " +
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
            Log.d(RepositoryTrace.MAIL_PERF_TAG, "[PDF_DOWNLOAD] CACHE_HIT emailId=$emailId " +
                "attachmentId=${metadata.attachmentId} size=${cachedFile.length()}")
            return PdfDownloadState.Ready(cachedFile.length())
        }
        // Cache inválido — limpiar
        if (cachedFile != null) {
            Log.d(RepositoryTrace.MAIL_PERF_TAG, "[PDF_DOWNLOAD] CACHE_INVALID removing emailId=$emailId " +
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
        val p = providerFactory()
        if (p == null) {
            return PdfDownloadState.Error(PdfDownloadFailure.NO_PROVIDER)
        }

        val bytes: ByteArray
        try {
            Log.d(RepositoryTrace.MAIL_PERF_TAG, "[PDF_DOWNLOAD] DOWNLOADING emailId=$emailId " +
                "attachmentId=${metadata.attachmentId}")
            bytes = p.downloadAttachment(emailId, metadata.attachmentId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(RepositoryTrace.MAIL_PERF_TAG, "[PDF_DOWNLOAD] NETWORK_ERROR emailId=$emailId " +
                "attachmentId=${metadata.attachmentId} error=${e.message}", e)
            return PdfDownloadState.Error(PdfDownloadFailure.NETWORK)
        }

        // ── Post-validación ─────────────────────────────────────
        if (bytes.isEmpty()) {
            Log.w(RepositoryTrace.MAIL_PERF_TAG, "[PDF_DOWNLOAD] EMPTY_CONTENT emailId=$emailId " +
                "attachmentId=${metadata.attachmentId}")
            return PdfDownloadState.Error(PdfDownloadFailure.EMPTY_CONTENT)
        }

        if (bytes.size.toLong() > maxPdfSize) {
            Log.w(RepositoryTrace.MAIL_PERF_TAG, "[PDF_DOWNLOAD] ACTUAL_TOO_LARGE emailId=$emailId " +
                "attachmentId=${metadata.attachmentId} actualSize=${bytes.size}")
            return PdfDownloadState.Error(PdfDownloadFailure.TOO_LARGE)
        }

        if (!hasPdfMagic(bytes)) {
            Log.w(RepositoryTrace.MAIL_PERF_TAG, "[PDF_DOWNLOAD] INVALID_PDF emailId=$emailId " +
                "attachmentId=${metadata.attachmentId} firstBytes=${bytes.take(5).joinToString("") { "%02x".format(it) }}")
            return PdfDownloadState.Error(PdfDownloadFailure.INVALID_PDF)
        }

        // ── Store ───────────────────────────────────────────────
        return try {
            val file = writeGuard.commit(lease) { pdfCacheManager.store(emailId, stableId, bytes) }
                ?: return PdfDownloadState.Error(PdfDownloadFailure.NO_PROVIDER)
            Log.d(RepositoryTrace.MAIL_PERF_TAG, "[PDF_DOWNLOAD] SUCCESS emailId=$emailId " +
                "attachmentId=${metadata.attachmentId} size=${file.length()}")
            PdfDownloadState.Ready(file.length())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(RepositoryTrace.MAIL_PERF_TAG, "[PDF_DOWNLOAD] CACHE_WRITE_ERROR emailId=$emailId " +
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

    private fun isValidPdfFile(file: File): Boolean {
        if (!file.exists() || file.length() == 0L) return false
        if (file.length() > maxPdfSize) return false
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
