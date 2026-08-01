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

    // ── Pruebas de GalleryStorage (Tarea 3.1-B) ────────────────

    private class FakeGalleryStorage : ImageUtils.GalleryStorage {
        var insertCalled = 0
        var openCalled = 0
        var publishCalled = 0
        var deleteCalled = 0
        var toasts = mutableListOf<String>()
        var throwOnInsert: Throwable? = null
        var throwOnOpen: Throwable? = null
        var throwOnWrite: Throwable? = null

        override fun insertPendingImage(filename: String, mimeType: String): Any? {
            insertCalled++
            throwOnInsert?.let { throw it }
            // Return a plain String token — no android.net.Uri needed in JVM
            return "content://media/external/images/media/1"
        }

        override fun openOutputStream(token: Any): java.io.OutputStream? {
            openCalled++
            throwOnOpen?.let { throw it }
            return object : java.io.ByteArrayOutputStream() {
                override fun write(buffer: ByteArray, offset: Int, length: Int) {
                    throwOnWrite?.let { throw it }
                    super.write(buffer, offset, length)
                }
            }
        }

        override fun publishImage(token: Any) {
            publishCalled++
        }

        override fun deleteImage(token: Any) {
            deleteCalled++
        }

        override suspend fun showToast(message: String) {
            toasts.add(message)
        }
    }

    @Test
    fun `cancelación durante creación propaga CancellationException y no notifica ni escribe`() =
        kotlinx.coroutines.test.runTest {
            val storage = FakeGalleryStorage()
            val sentinel = kotlinx.coroutines.CancellationException("sentinel-insert")
            storage.throwOnInsert = sentinel
            val labels = ImageSaveLabels("Fmt", "Success", "Error", "IMG_%1\$d.%2\$s")

            try {
                ImageUtils.saveImageToGalleryInternal(
                    "data:image/png;base64,iVBORw0KGgo",
                    labels,
                    storage,
                    123L,
                    decodeBase64 = { byteArrayOf(1, 2, 3) }
                )
                org.junit.Assert.fail("Expected CancellationException")
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Stack-trace recovery may copy the outer exception. The original
                // sentinel must still be preserved by identity in the cause chain.
                assertTrue(
                    "Must preserve the exact sentinel instance",
                    generateSequence(e as Throwable) { it.cause }.any { it === sentinel }
                )
            }

            assertEquals(1, storage.insertCalled)
            assertEquals(0, storage.openCalled)
            assertEquals(0, storage.publishCalled)
            assertEquals(0, storage.deleteCalled) // nothing was created yet
            assertTrue(storage.toasts.isEmpty())
        }

    @Test
    fun `cancelación durante escritura elimina entrada parcial sin notificar ni publicar`() =
        kotlinx.coroutines.test.runTest {
            val storage = FakeGalleryStorage()
            val sentinel = kotlinx.coroutines.CancellationException("sentinel-write")
            storage.throwOnWrite = sentinel
            val labels = ImageSaveLabels("Fmt", "Success", "Error", "IMG_%1\$d.%2\$s")

            try {
                ImageUtils.saveImageToGalleryInternal(
                    "data:image/png;base64,iVBORw0KGgo",
                    labels,
                    storage,
                    123L,
                    decodeBase64 = { byteArrayOf(1, 2, 3) }
                )
                org.junit.Assert.fail("Expected CancellationException")
            } catch (e: kotlinx.coroutines.CancellationException) {
                assertTrue(
                    "Must preserve the exact sentinel instance",
                    generateSequence(e as Throwable) { it.cause }.any { it === sentinel }
                )
            }

            assertEquals(1, storage.insertCalled)
            assertEquals(1, storage.openCalled)
            assertEquals(0, storage.publishCalled)
            assertEquals(1, storage.deleteCalled) // cleanup happened
            assertTrue(storage.toasts.isEmpty())
        }

    @Test
    fun `excepción ordinaria limpia entrada parcial y muestra un único error`() =
        kotlinx.coroutines.test.runTest {
            val storage = FakeGalleryStorage()
            storage.throwOnWrite = RuntimeException("unexpected")
            val labels = ImageSaveLabels("Fmt", "Success", "Error", "IMG_%1\$d.%2\$s")

            ImageUtils.saveImageToGalleryInternal(
                "data:image/png;base64,iVBORw0KGgo",
                labels,
                storage,
                123L,
                decodeBase64 = { byteArrayOf(1, 2, 3) }
            )

            assertEquals(1, storage.insertCalled)
            assertEquals(1, storage.openCalled)
            assertEquals(0, storage.publishCalled)
            assertEquals(1, storage.deleteCalled) // cleanup happened
            assertEquals(listOf("Error"), storage.toasts)
        }
}
