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

    /**
     * Saves a "data:image/xxx;base64,..." string to the device's public gallery (MediaStore).
     * Automatically handles the MIME type and file extension based on the URI.
     */
    suspend fun saveImageToGallery(context: Context, dataUri: String) = withContext(Dispatchers.IO) {
        try {
            if (!dataUri.startsWith("data:image/")) {
                showToast(context, "Formato de imagen inválido")
                return@withContext
            }

            // Extract MIME type, e.g., "image/jpeg"
            val mimeTypeEndIndex = dataUri.indexOf(";")
            if (mimeTypeEndIndex == -1) return@withContext
            val mimeType = dataUri.substring(5, mimeTypeEndIndex)

            // Extract base64
            val base64StartIndex = dataUri.indexOf("base64,")
            if (base64StartIndex == -1) return@withContext
            val base64Data = dataUri.substring(base64StartIndex + 7)

            val bytes = Base64.decode(base64Data, Base64.DEFAULT)

            val extension = when (mimeType) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                else -> "jpg"
            }
            val filename = "MailApp_Image_${System.currentTimeMillis()}.$extension"

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
                showToast(context, "Imagen guardada en Galería")
            } else {
                showToast(context, "Error al guardar imagen")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            showToast(context, "Error al guardar imagen")
        }
    }

    private suspend fun showToast(context: Context, message: String) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}
