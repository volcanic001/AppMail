package com.david.mailapp.feature.emaildetail

/**
 * Copia [source] al [destinationUri] usando ContentResolver con streams.
 * No carga el archivo completo en memoria — copia por bloques de 8 KiB.
 * Devuelve true si todos los bytes se copiaron correctamente.
 */
internal fun copyFileToUri(
    context: android.content.Context,
    source: java.io.File,
    destinationUri: android.net.Uri
): Boolean {
    return try {
        context.contentResolver.openOutputStream(destinationUri, "wt").use { output ->
            copyFileToStream(source, output)
        }
    } catch (_: Exception) {
        false
    }
}

/**
 * Copia [source] a [output] y verifica que se hayan escrito todos sus bytes.
 * Un proveedor SAF puede rechazar el URI devolviendo un stream nulo.
 */
internal fun copyFileToStream(
    source: java.io.File,
    output: java.io.OutputStream?
): Boolean {
    if (output == null) return false

    val sourceSize = source.length()
    return try {
        source.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var totalWritten = 0L
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
                totalWritten += bytesRead
            }
            totalWritten == sourceSize
        }
    } catch (_: Exception) {
        false
    }
}
