package com.david.mailapp.feature.emaildetail.components

import com.david.mailapp.data.pdf.PdfDownloadFailure
import com.david.mailapp.feature.emaildetail.sanitizeDisplayName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [formatPdfAttachmentSize] and [pdfDownloadErrorMessage].
 */
class PdfAttachmentFormattingTest {

    @Test
    fun `null size returns null`() {
        assertNull(formatPdfAttachmentSize(null))
    }

    @Test
    fun `negative size returns null`() {
        assertNull(formatPdfAttachmentSize(-1))
        assertNull(formatPdfAttachmentSize(-1024))
    }

    @Test
    fun `zero bytes`() {
        assertEquals("0 B", formatPdfAttachmentSize(0))
    }

    @Test
    fun `1023 bytes`() {
        assertEquals("1023 B", formatPdfAttachmentSize(1023))
    }

    @Test
    fun `1024 bytes is 1 KB`() {
        assertEquals("1 KB", formatPdfAttachmentSize(1024))
    }

    @Test
    fun `1536 bytes is 1 dot 5 KB`() {
        assertEquals("1.5 KB", formatPdfAttachmentSize(1536))
    }

    @Test
    fun `1 MiB is 1 MB`() {
        assertEquals("1 MB", formatPdfAttachmentSize(1048576))
    }

    @Test
    fun `one dot five MiB is 1 dot 5 MB`() {
        assertEquals("1.5 MB", formatPdfAttachmentSize(1572864))
    }

    @Test
    fun `1 GiB is 1 GB`() {
        assertEquals("1 GB", formatPdfAttachmentSize(1073741824))
    }

    // ── pdfDownloadErrorMessage ──────────────────────────────────

    @Test
    fun `TOO_LARGE error message`() {
        val msg = pdfDownloadErrorMessage(PdfDownloadFailure.TOO_LARGE)
        assertEquals("El PDF supera el límite de 25 MB", msg)
    }

    @Test
    fun `INVALID_PDF error message`() {
        val msg = pdfDownloadErrorMessage(PdfDownloadFailure.INVALID_PDF)
        assertEquals("El archivo descargado no es un PDF válido", msg)
    }

    @Test
    fun `EMPTY_CONTENT error message`() {
        val msg = pdfDownloadErrorMessage(PdfDownloadFailure.EMPTY_CONTENT)
        assertEquals("El archivo descargado no es un PDF válido", msg)
    }

    @Test
    fun `NETWORK error message`() {
        val msg = pdfDownloadErrorMessage(PdfDownloadFailure.NETWORK)
        assertEquals("No se pudo descargar. Toca para reintentar", msg)
    }

    @Test
    fun `NO_PROVIDER error message`() {
        val msg = pdfDownloadErrorMessage(PdfDownloadFailure.NO_PROVIDER)
        assertEquals("No se pudo descargar. Toca para reintentar", msg)
    }

    @Test
    fun `CACHE_WRITE error message`() {
        val msg = pdfDownloadErrorMessage(PdfDownloadFailure.CACHE_WRITE)
        assertEquals("No se pudo descargar. Toca para reintentar", msg)
    }

    // ── sanitizeDisplayName ──────────────────────────────────────

    @Test
    fun `normal filename unchanged`() {
        assertEquals("documento.pdf", sanitizeDisplayName("documento.pdf"))
    }

    @Test
    fun `slash replaced with underscore`() {
        assertEquals("a_b.pdf", sanitizeDisplayName("a/b.pdf"))
    }

    @Test
    fun `backslash replaced with underscore`() {
        assertEquals("a_b.pdf", sanitizeDisplayName("a\\b.pdf"))
    }

    @Test
    fun `path traversal sanitized`() {
        val result = sanitizeDisplayName("../../etc/passwd")
        // Slashes must be replaced — no path separators remain
        assertFalse("must not contain /", result.contains("/"))
        assertFalse("must not contain backslash", result.contains("\\"))
    }

    @Test
    fun `control characters removed`() {
        assertEquals("clean.pdf", sanitizeDisplayName("cle\u0000an.pdf"))
        assertEquals("clean.pdf", sanitizeDisplayName("cle\u0001an.pdf"))
        assertEquals("clean.pdf", sanitizeDisplayName("cle\u001fan.pdf"))
    }

    @Test
    fun `blank name falls back to documento dot pdf`() {
        assertEquals("documento.pdf", sanitizeDisplayName(""))
        assertEquals("documento.pdf", sanitizeDisplayName("   "))
        assertEquals("documento.pdf", sanitizeDisplayName("\u0000"))
    }

    @Test
    fun `leading and trailing whitespace trimmed`() {
        assertEquals("doc.pdf", sanitizeDisplayName("  doc.pdf  "))
        assertEquals("doc.pdf", sanitizeDisplayName("\tdoc.pdf\n"))
    }
}
