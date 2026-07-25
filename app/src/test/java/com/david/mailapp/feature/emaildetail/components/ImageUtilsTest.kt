package com.david.mailapp.feature.emaildetail.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests JVM para helpers puros de [ImageUtils].
 *
 * No usa Android Context, MediaStore, Toast, Robolectric ni Mockito.
 * Prueba extensión MIME, nombre de archivo desde plantilla y contrato ImageSaveLabels.
 */
class ImageUtilsTest {

    // ─────────────────────────────────────────────────────────────
    // mimeTypeToExtension
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `mimeTypeToExtension devuelve png para image_png`() {
        assertEquals("png", ImageUtils.mimeTypeToExtension("image/png"))
    }

    @Test
    fun `mimeTypeToExtension devuelve webp para image_webp`() {
        assertEquals("webp", ImageUtils.mimeTypeToExtension("image/webp"))
    }

    @Test
    fun `mimeTypeToExtension devuelve jpg como fallback para jpeg`() {
        assertEquals("jpg", ImageUtils.mimeTypeToExtension("image/jpeg"))
    }

    @Test
    fun `mimeTypeToExtension devuelve jpg como fallback para tipos desconocidos`() {
        assertEquals("jpg", ImageUtils.mimeTypeToExtension("image/bmp"))
        assertEquals("jpg", ImageUtils.mimeTypeToExtension("image/gif"))
        assertEquals("jpg", ImageUtils.mimeTypeToExtension("image/svg+xml"))
    }

    // ─────────────────────────────────────────────────────────────
    // buildImageFilename
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `buildImageFilename construye nombre exacto desde plantilla`() {
        val template = "MailApp_Image_%1\$d.%2\$s"
        val timestamp = 1700000000123L
        val extension = "png"

        val result = ImageUtils.buildImageFilename(template, timestamp, extension)
        assertEquals("MailApp_Image_1700000000123.png", result)
    }

    @Test
    fun `buildImageFilename con webp`() {
        val template = "App_%1\$d.%2\$s"
        val result = ImageUtils.buildImageFilename(template, 1699000000000L, "webp")
        assertEquals("App_1699000000000.webp", result)
    }

    @Test
    fun `buildImageFilename con jpg`() {
        val template = "MyPhoto_%1\$d.%2\$s"
        val result = ImageUtils.buildImageFilename(template, 123456789L, "jpg")
        assertEquals("MyPhoto_123456789.jpg", result)
    }

    // ─────────────────────────────────────────────────────────────
    // ImageSaveLabels — contrato
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `ImageSaveLabels transporta mensajes resueltos`() {
        val labels = ImageSaveLabels(
            invalidFormatMessage = "Formato inválido",
            savedToGalleryMessage = "Guardado en Galería",
            saveErrorMessage = "Error al guardar",
            filenameTemplate = "IMG_%1\$d.%2\$s"
        )
        assertEquals("Formato inválido", labels.invalidFormatMessage)
        assertEquals("Guardado en Galería", labels.savedToGalleryMessage)
        assertEquals("Error al guardar", labels.saveErrorMessage)
        assertEquals("IMG_%1\$d.%2\$s", labels.filenameTemplate)
    }

    @Test
    fun `buildImageFilename desde ImageSaveLabels template`() {
        val template = "IMG_%1\$d.%2\$s"
        val result = ImageUtils.buildImageFilename(template, 9999L, "png")
        assertEquals("IMG_9999.png", result)
    }
}
