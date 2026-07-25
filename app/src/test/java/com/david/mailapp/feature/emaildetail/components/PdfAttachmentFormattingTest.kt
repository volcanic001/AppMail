package com.david.mailapp.feature.emaildetail.components

import com.david.mailapp.R
import com.david.mailapp.core.localization.UiText
import com.david.mailapp.core.localization.toUiErrorReason
import com.david.mailapp.core.localization.toUiText
import com.david.mailapp.data.pdf.PdfDownloadFailure
import com.david.mailapp.feature.emaildetail.buildPdfSuggestedName
import com.david.mailapp.feature.emaildetail.sanitizeDisplayName
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [formatPdfAttachmentSize], [formatSizeDecimal],
 * [PdfDownloadFailure.toUiErrorReason], [sanitizeDisplayName],
 * and [buildPdfSuggestedName].
 */
class PdfAttachmentFormattingTest {

    private val enLocale = Locale.US
    private val esLocale = Locale.forLanguageTag("es")

    // ── formatPdfAttachmentSize ──────────────────────────────────

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
    fun `zero bytes returns size_bytes resource`() {
        val result = formatPdfAttachmentSize(0)
        assertEquals(R.string.size_bytes, result?.resId)
        assertEquals(listOf(0L), result?.formatArgs)
    }

    @Test
    fun `1023 bytes returns size_bytes resource`() {
        val result = formatPdfAttachmentSize(1023)
        assertEquals(R.string.size_bytes, result?.resId)
        assertEquals(listOf(1023L), result?.formatArgs)
    }

    @Test
    fun `1024 bytes is 1 KB`() {
        val result = formatPdfAttachmentSize(1024, enLocale)
        assertEquals(R.string.size_kb, result?.resId)
        assertEquals(listOf("1"), result?.formatArgs)
    }

    @Test
    fun `1536 bytes is 1 dot 5 KB with English locale`() {
        val result = formatPdfAttachmentSize(1536, enLocale)
        assertEquals(R.string.size_kb, result?.resId)
        assertEquals(listOf("1.5"), result?.formatArgs)
    }

    @Test
    fun `1536 bytes is 1 comma 5 KB with Spanish locale`() {
        val result = formatPdfAttachmentSize(1536, esLocale)
        assertEquals(R.string.size_kb, result?.resId)
        assertEquals(listOf("1,5"), result?.formatArgs)
    }

    @Test
    fun `1 MiB is 1 MB`() {
        val result = formatPdfAttachmentSize(1048576, enLocale)
        assertEquals(R.string.size_mb, result?.resId)
        assertEquals(listOf("1"), result?.formatArgs)
    }

    @Test
    fun `one dot five MiB is 1 dot 5 MB with English locale`() {
        val result = formatPdfAttachmentSize(1572864, enLocale)
        assertEquals(R.string.size_mb, result?.resId)
        assertEquals(listOf("1.5"), result?.formatArgs)
    }

    @Test
    fun `one dot five MiB is 1 comma 5 MB with Spanish locale`() {
        val result = formatPdfAttachmentSize(1572864, esLocale)
        assertEquals(R.string.size_mb, result?.resId)
        assertEquals(listOf("1,5"), result?.formatArgs)
    }

    @Test
    fun `1 GiB is 1 GB`() {
        val result = formatPdfAttachmentSize(1073741824, enLocale)
        assertEquals(R.string.size_gb, result?.resId)
        assertEquals(listOf("1"), result?.formatArgs)
    }

    // ── formatSizeDecimal ────────────────────────────────────────

    @Test
    fun `whole number returns integer string`() {
        assertEquals("2", formatSizeDecimal(2.0, enLocale))
        assertEquals("0", formatSizeDecimal(0.0, enLocale))
        assertEquals("1024", formatSizeDecimal(1024.0, enLocale))
    }

    @Test
    fun `one decimal preserved with English locale`() {
        assertEquals("1.5", formatSizeDecimal(1.5, enLocale))
        assertEquals("0.7", formatSizeDecimal(0.75, enLocale))
    }

    @Test
    fun `one decimal preserved with Spanish locale`() {
        assertEquals("1,5", formatSizeDecimal(1.5, esLocale))
        assertEquals("0,7", formatSizeDecimal(0.75, esLocale))
    }

    @Test
    fun `truncated to one decimal`() {
        assertEquals("1.5", formatSizeDecimal(1.54, enLocale))
        assertEquals("1.5", formatSizeDecimal(1.59, enLocale))
    }

    @Test
    fun `truncated to one decimal Spanish`() {
        assertEquals("1,5", formatSizeDecimal(1.54, esLocale))
        assertEquals("1,5", formatSizeDecimal(1.59, esLocale))
    }

    // ── PdfDownloadFailure → UiErrorReason → expected resource ──

    @Test
    fun `TOO_LARGE maps to pdf_too_large`() {
        val reason = PdfDownloadFailure.TOO_LARGE.toUiErrorReason()
        val text = reason.toUiText()
        assertEquals(R.string.pdf_too_large, text.resId)
    }

    @Test
    fun `INVALID_PDF maps to pdf_invalid`() {
        val reason = PdfDownloadFailure.INVALID_PDF.toUiErrorReason()
        val text = reason.toUiText()
        assertEquals(R.string.pdf_invalid, text.resId)
    }

    @Test
    fun `EMPTY_CONTENT maps to pdf_invalid`() {
        val reason = PdfDownloadFailure.EMPTY_CONTENT.toUiErrorReason()
        val text = reason.toUiText()
        assertEquals(R.string.pdf_invalid, text.resId)
    }

    @Test
    fun `NO_PROVIDER maps to error_no_active_account`() {
        val reason = PdfDownloadFailure.NO_PROVIDER.toUiErrorReason()
        val text = reason.toUiText()
        assertEquals(R.string.error_no_active_account, text.resId)
    }

    @Test
    fun `NETWORK maps to pdf_download_failed`() {
        val reason = PdfDownloadFailure.NETWORK.toUiErrorReason()
        val text = reason.toUiText()
        assertEquals(R.string.pdf_download_failed, text.resId)
    }

    @Test
    fun `CACHE_WRITE maps to pdf_download_failed`() {
        val reason = PdfDownloadFailure.CACHE_WRITE.toUiErrorReason()
        val text = reason.toUiText()
        assertEquals(R.string.pdf_download_failed, text.resId)
    }

    // ── sanitizeDisplayName ──────────────────────────────────────

    @Test
    fun `normal filename unchanged`() {
        assertEquals("documento.pdf", sanitizeDisplayName("documento.pdf", "fallback.pdf"))
    }

    @Test
    fun `slash replaced with underscore`() {
        assertEquals("a_b.pdf", sanitizeDisplayName("a/b.pdf", "fallback.pdf"))
    }

    @Test
    fun `backslash replaced with underscore`() {
        assertEquals("a_b.pdf", sanitizeDisplayName("a\\b.pdf", "fallback.pdf"))
    }

    @Test
    fun `path traversal sanitized`() {
        val result = sanitizeDisplayName("../../etc/passwd", "fallback.pdf")
        assertFalse("must not contain /", result.contains("/"))
        assertFalse("must not contain backslash", result.contains("\\"))
    }

    @Test
    fun `control characters removed`() {
        assertEquals("clean.pdf", sanitizeDisplayName("cle\u0000an.pdf", "fallback.pdf"))
        assertEquals("clean.pdf", sanitizeDisplayName("cle\u0001an.pdf", "fallback.pdf"))
        assertEquals("clean.pdf", sanitizeDisplayName("cle\u001fan.pdf", "fallback.pdf"))
    }

    @Test
    fun `blank name falls back to defaultName`() {
        assertEquals("default.pdf", sanitizeDisplayName("", "default.pdf"))
        assertEquals("default.pdf", sanitizeDisplayName("   ", "default.pdf"))
        assertEquals("default.pdf", sanitizeDisplayName("\u0000", "default.pdf"))
    }

    @Test
    fun `leading and trailing whitespace trimmed`() {
        assertEquals("doc.pdf", sanitizeDisplayName("  doc.pdf  ", "fallback.pdf"))
        assertEquals("doc.pdf", sanitizeDisplayName("\tdoc.pdf\n", "fallback.pdf"))
    }

    // ── buildPdfSuggestedName ────────────────────────────────────

    @Test
    fun `name with dot pdf unchanged`() {
        assertEquals("report.pdf", buildPdfSuggestedName("report.pdf", "fallback.pdf"))
    }

    @Test
    fun `name without extension receives dot pdf`() {
        assertEquals("documento.pdf", buildPdfSuggestedName("documento", "fallback.pdf"))
    }

    @Test
    fun `uppercase PDF not duplicated`() {
        assertEquals("REPORT.PDF", buildPdfSuggestedName("REPORT.PDF", "fallback.pdf"))
    }

    @Test
    fun `empty name uses fallback localized`() {
        assertEquals("doc.pdf", buildPdfSuggestedName("", "doc.pdf"))
    }

    @Test
    fun `whitespace only uses fallback localized`() {
        assertEquals("fallback.pdf", buildPdfSuggestedName("   ", "fallback.pdf"))
    }

    @Test
    fun `sanitized name with no extension gets dot pdf`() {
        // slashes → underscores, then .pdf appended
        assertEquals("a_b.pdf", buildPdfSuggestedName("a/b", "fallback.pdf"))
    }
}
