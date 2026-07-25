package com.david.mailapp.feature.emaildetail.components

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.MediaStore
import android.util.Base64
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Etiquetas ya resueltas para el flujo de guardado de imágenes.
 *
 * Resuélvelas en el ámbito composable (stringResource) antes de pasarlas
 * a [ImageUtils.saveImageToGallery].
 */
data class ImageSaveLabels(
    val invalidFormatMessage: String,
    val savedToGalleryMessage: String,
    val saveErrorMessage: String,
    val filenameTemplate: String
)

/**
 * Helper object to parse data URIs and interact with MediaStore for image saving.
 */
object ImageUtils {

    /**
     * Decodes a "data:image/xxx;base64,..." string into a Bitmap.
     * Returns null if the format is invalid or decoding fails.
     */
    fun decodeDataUriToBitmap(dataUri: String): Bitmap? {
        try {
            if (!dataUri.startsWith("data:image/")) return null
            val base64StartIndex = dataUri.indexOf("base64,")
            if (base64StartIndex == -1) return null

            val base64Data = dataUri.substring(base64StartIndex + 7)
            val bytes = Base64.decode(base64Data, Base64.DEFAULT)
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            return null
        }
    }

    // ── Pure helpers (no-Context, testable) ────────────────────

    /** Mapea un MIME type de imagen a su extensión de archivo. */
    internal fun mimeTypeToExtension(mimeType: String): String {
        return when (mimeType) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
    }

    /**
     * Construye el nombre de archivo desde la plantilla localizada.
     *
     * @param template  Plantilla con formato (ej. "MailApp_Image_%1$d.%2$s").
     * @param timestamp Marca de tiempo actual en ms.
     * @param extension Extensión de archivo (png, webp, jpg).
     */
    internal fun buildImageFilename(template: String, timestamp: Long, extension: String): String {
        return String.format(java.util.Locale.ROOT, template, timestamp, extension)
    }

    // ── Save to gallery ────────────────────────────────────────

    /**
     * Saves a "data:image/xxx;base64,..." string to the device's public gallery (MediaStore).
     *
     * @param labels Etiquetas ya resueltas desde el ámbito composable.
     */
    suspend fun saveImageToGallery(
        context: Context,
        dataUri: String,
        labels: ImageSaveLabels
    ) = withContext(Dispatchers.IO) {
        try {
            if (!dataUri.startsWith("data:image/")) {
                showToast(context, labels.invalidFormatMessage)
                return@withContext
            }

            val mimeTypeEndIndex = dataUri.indexOf(";")
            if (mimeTypeEndIndex == -1) return@withContext
            val mimeType = dataUri.substring(5, mimeTypeEndIndex)

            val base64StartIndex = dataUri.indexOf("base64,")
            if (base64StartIndex == -1) return@withContext
            val base64Data = dataUri.substring(base64StartIndex + 7)

            val bytes = Base64.decode(base64Data, Base64.DEFAULT)
            val extension = mimeTypeToExtension(mimeType)
            val filename = buildImageFilename(labels.filenameTemplate, System.currentTimeMillis(), extension)

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/MailApp")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

            if (uri != null) {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(bytes)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }
                showToast(context, labels.savedToGalleryMessage)
            } else {
                showToast(context, labels.saveErrorMessage)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            showToast(context, labels.saveErrorMessage)
        }
    }

    private suspend fun showToast(context: Context, message: String) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}
