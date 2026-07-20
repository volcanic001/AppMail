package com.david.mailapp.data.pdf

import android.util.Log
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * Almacenamiento local de PDFs descargados en [cacheDir]/pdf_attachments/.
 *
 * Cada archivo se guarda como:
 *   {sha256(emailId + "\u0000" + partId)}.pdf
 *
 * El nombre original del archivo Gmail NO se usa para construir rutas,
 * impidiendo ataques Path Traversal.
 *
 * La escritura es atómica: se escribe a un archivo .tmp y se renombra.
 * En caso de error, el .tmp se elimina.
 */
class PdfCacheManager(private val cacheDir: File) {

    private val pdfDir = File(cacheDir, "pdf_attachments")

    /** Devuelve el archivo en caché si existe, o null. No valida el contenido. */
    fun getCachedFile(emailId: String, stablePartId: String): File? {
        val file = resolveFile(emailId, stablePartId)
        return file.takeIf { it.exists() }
    }

    /**
     * Guarda [bytes] en el disco usando escritura atómica (temp → rename).
     * No valida el contenido — la validación corresponde al Repository.
     *
     * @throws IOException si falla la escritura o el rename.
     */
    fun store(emailId: String, stablePartId: String, bytes: ByteArray): File {
        if (!pdfDir.exists() && !pdfDir.mkdirs()) {
            throw IOException("Could not create PDF cache directory")
        }
        val hash = hashKey(emailId, stablePartId)
        val tempFile = File(pdfDir, "$hash.tmp")
        try {
            tempFile.writeBytes(bytes)
            val finalFile = File(pdfDir, "$hash.pdf")
            if (finalFile.exists() && !finalFile.delete()) {
                throw IOException("Could not replace cached PDF")
            }
            if (!tempFile.renameTo(finalFile)) {
                throw IOException("Could not finalize cached PDF")
            }
            return finalFile
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }
    }

    /** Elimina el archivo en caché y cualquier residuo .tmp. */
    fun delete(emailId: String, stablePartId: String) {
        val hash = hashKey(emailId, stablePartId)
        File(pdfDir, "$hash.pdf").delete()
        File(pdfDir, "$hash.tmp").delete()
    }

    /**
     * Elimina todos los archivos .pdf y .tmp dentro de [pdfDir].
     * No elimina [pdfDir] ni archivos fuera de él.
     * No toca PDFs guardados mediante Storage Access Framework (fuera de cacheDir).
     *
     * @return lista de mensajes de error (vacía si todo se eliminó correctamente).
     *         Los errores no interrumpen la operación — se reportan y continúa.
     */
    fun clearAll(): List<String> {
        if (!pdfDir.exists()) return emptyList()

        val errors = mutableListOf<String>()
        val files = pdfDir.listFiles() ?: return emptyList()

        for (file in files) {
            if (!file.isFile) continue
            val name = file.name
            if (!name.endsWith(".pdf") && !name.endsWith(".tmp")) continue
            if (!file.delete()) {
                val msg = "Could not delete ${file.absolutePath}"
                Log.w(TAG, msg)
                errors.add(msg)
            }
        }
        return errors
    }

    // ── Internal ──────────────────────────────────────────────

    private fun resolveFile(emailId: String, stablePartId: String): File {
        return File(pdfDir, "${hashKey(emailId, stablePartId)}.pdf")
    }

    /** SHA-256 hex del par emailId + partId estable separados por \0. */
    internal fun hashKey(emailId: String, stablePartId: String): String {
        val input = "$emailId\u0000$stablePartId"
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private companion object {
        private const val TAG = "PdfCacheManager"
    }
}
