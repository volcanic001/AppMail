package com.david.mailapp.data.repository

import com.david.mailapp.data.pdf.PdfCacheManager
import com.david.mailapp.data.pdf.PdfDownloadState
import java.io.File
import java.io.FileInputStream

internal class EmailPdfCoordinator(
    private val pdfCacheManager: PdfCacheManager,
    private val maxPdfSize: Long
) {
    /** Firma PDF: %PDF- */
    private val PDF_MAGIC = byteArrayOf(0x25, 0x50, 0x44, 0x46, 0x2D)

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

    // Exposed as internal temporarily for downloadPdf (still in EmailRepository until 4.2)
    internal fun isValidPdfFile(file: File): Boolean {
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

    internal fun hasPdfMagic(bytes: ByteArray): Boolean {
        if (bytes.size < 5) return false
        // Compare byte-by-byte para evitar alocación extra y ser compatible
        return bytes[0] == PDF_MAGIC[0] && bytes[1] == PDF_MAGIC[1] &&
            bytes[2] == PDF_MAGIC[2] && bytes[3] == PDF_MAGIC[3] &&
            bytes[4] == PDF_MAGIC[4]
    }
}
