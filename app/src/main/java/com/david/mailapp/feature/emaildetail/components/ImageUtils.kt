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

    internal interface GalleryStorage {
        /** Returns an opaque token representing the inserted entry, or null on failure. */
        fun insertPendingImage(filename: String, mimeType: String): Any?
        fun openOutputStream(token: Any): java.io.OutputStream?
        fun publishImage(token: Any)
        fun deleteImage(token: Any)
        suspend fun showToast(message: String)
    }

    internal class DefaultGalleryStorage(private val context: Context) : GalleryStorage {
        override fun insertPendingImage(filename: String, mimeType: String): Any? {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/MailApp")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }
            return context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        }

        override fun openOutputStream(token: Any): java.io.OutputStream? =
            context.contentResolver.openOutputStream(token as android.net.Uri)

        override fun publishImage(token: Any) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                context.contentResolver.update(token as android.net.Uri, contentValues, null, null)
            }
        }

        override fun deleteImage(token: Any) {
            try {
                context.contentResolver.delete(token as android.net.Uri, null, null)
            } catch (e: Exception) {
                // Ignore failure on cleanup
            }
        }

        override suspend fun showToast(message: String) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Saves a "data:image/xxx;base64,..." string to the device's public gallery (MediaStore).
     *
     * @param labels Etiquetas ya resueltas desde el ámbito composable.
     */
    suspend fun saveImageToGallery(
        context: Context,
        dataUri: String,
        labels: ImageSaveLabels
    ) {
        saveImageToGalleryInternal(
            dataUri,
            labels,
            DefaultGalleryStorage(context),
            System.currentTimeMillis()
        )
    }

    internal suspend fun saveImageToGalleryInternal(
        dataUri: String,
        labels: ImageSaveLabels,
        storage: GalleryStorage,
        timestamp: Long,
        decodeBase64: (String) -> ByteArray = { encoded ->
            Base64.decode(encoded, Base64.DEFAULT)
        }
    ) = withContext(Dispatchers.IO) {
        var createdUri: Any? = null
        try {
            if (!dataUri.startsWith("data:image/")) {
                storage.showToast(labels.invalidFormatMessage)
                return@withContext
            }

            val mimeTypeEndIndex = dataUri.indexOf(";")
            if (mimeTypeEndIndex == -1) return@withContext
            val mimeType = dataUri.substring(5, mimeTypeEndIndex)

            val base64StartIndex = dataUri.indexOf("base64,")
            if (base64StartIndex == -1) return@withContext
            val base64Data = dataUri.substring(base64StartIndex + 7)

            val bytes = decodeBase64(base64Data)
            val extension = mimeTypeToExtension(mimeType)
            val filename = buildImageFilename(labels.filenameTemplate, timestamp, extension)

            createdUri = storage.insertPendingImage(filename, mimeType)

            if (createdUri != null) {
                storage.openOutputStream(createdUri)?.use { outputStream ->
                    outputStream.write(bytes)
                }
                storage.publishImage(createdUri)
                storage.showToast(labels.savedToGalleryMessage)
            } else {
                storage.showToast(labels.saveErrorMessage)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            createdUri?.let { token ->
                withContext(kotlinx.coroutines.NonCancellable) {
                    storage.deleteImage(token)
                }
            }
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            createdUri?.let { token ->
                withContext(kotlinx.coroutines.NonCancellable) {
                    storage.deleteImage(token)
                }
            }
            storage.showToast(labels.saveErrorMessage)
        }
    }
}
