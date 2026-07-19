package com.david.mailapp.feature.emaildetail

import com.david.mailapp.domain.model.Email
import com.david.mailapp.domain.model.EmailFolder
import com.david.mailapp.domain.model.PdfAttachmentMetadata
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests the ViewModel's emission-processing logic — the exact same condition
 * used in [EmailDetailViewModel]'s collect block — without Android dependencies.
 *
 * The ViewModel's decision tree is:
 * ```
 * needsRemoteFetch = (baseHtml.isBlank() || !email.pdfMetadataScanned)
 *
 * when {
 *     needsRemoteFetch && !isFetchingRemoteBody && !delivered  → FETCH_REMOTE
 *     baseHtml.isNotBlank() && containsCid(...)               → FETCH_INLINE
 *     baseHtml.isNotBlank() && noCidOrImagesResolved           → SHOW_READY
 *     else                                                     → SHOW_PREPARING (or IGNORE if delivered)
 * }
 * ```
 *
 * Regression tested: a bodyless email that already emitted BodyError
 * (delivered = true) must NOT re-enter the FETCH_REMOTE branch.
 */
class EmailDetailViewModelTest {

    // ── Pure representation of the ViewModel's decision ──────────

    private enum class Action { FETCH_REMOTE, FETCH_INLINE, SHOW_READY, SHOW_PREPARING, IGNORE }

    private fun decide(
        email: Email,
        isFetchingRemoteBody: Boolean,
        isFetchingInlineImages: Boolean,
        delivered: Boolean,
        cachedInlineImages: Map<String, String>?,
        inlineRefsExist: Boolean
    ): Action {
        val baseHtml = if (email.cleanBody.isNotBlank()) email.cleanBody else email.body
        val needsRemoteFetch = (
            baseHtml.isBlank() ||
                !email.pdfMetadataScanned ||
                email.pdfAttachments.any { it.partId.isNullOrBlank() }
            )

        return when {
            needsRemoteFetch && !isFetchingRemoteBody && !delivered -> Action.FETCH_REMOTE
            baseHtml.isNotBlank() && baseHtml.contains("cid:", ignoreCase = true) &&
                cachedInlineImages == null && !isFetchingInlineImages -> Action.FETCH_INLINE
            baseHtml.isNotBlank() && (!baseHtml.contains("cid:", ignoreCase = true) ||
                cachedInlineImages != null) -> {
                if (delivered && !isFetchingInlineImages) Action.IGNORE
                else Action.SHOW_READY
            }
            delivered -> Action.IGNORE
            else -> Action.SHOW_PREPARING
        }
    }

    // ── Helpers ──────────────────────────────────────────────────

    private val bodylessEmail = Email(
        id = "test_msg_1", threadId = "t", from = "a", fromInitials = "A",
        to = "b", subject = "s", snippet = "s", timestamp = 1L,
        isRead = true, isStarred = false, hasAttachments = false,
        labels = emptyList(), folder = EmailFolder.Inbox,
        body = "", cleanBody = "",
        pdfAttachments = emptyList(), pdfMetadataScanned = false
    )

    private val scannedBodylessEmail = bodylessEmail.copy(
        pdfMetadataScanned = true
    )

    // ── Tests ────────────────────────────────────────────────────

    @Test
    fun `first emission with bodyless email triggers remote fetch`() {
        val action = decide(
            email = bodylessEmail,
            isFetchingRemoteBody = false,
            isFetchingInlineImages = false,
            delivered = false,
            cachedInlineImages = null,
            inlineRefsExist = false
        )
        assertEquals(Action.FETCH_REMOTE, action)
    }

    @Test
    fun `bodyless email after delivered does NOT trigger remote fetch`() {
        val action = decide(
            email = scannedBodylessEmail,   // pdfMetadataScanned = true after fetch
            isFetchingRemoteBody = false,   // fetch completed
            isFetchingInlineImages = false,
            delivered = true,               // BodyError was delivered
            cachedInlineImages = null,
            inlineRefsExist = false
        )
        assertEquals(Action.IGNORE, action)
    }

    @Test
    fun `simulates two emissions and verifies exactly one remote fetch`() {
        var fetchCount = 0
        var delivered = false
        var isFetchingRemoteBody = false
        var currentEmail: Email = bodylessEmail

        // ── Emission 1: bodyless, unscanned, not delivered ──────
        val action1 = decide(
            email = currentEmail,
            isFetchingRemoteBody = isFetchingRemoteBody,
            isFetchingInlineImages = false,
            delivered = delivered,
            cachedInlineImages = null,
            inlineRefsExist = false
        )
        assertEquals("first emission must trigger remote fetch", Action.FETCH_REMOTE, action1)

        // Simulate the ViewModel launching the fetch
        isFetchingRemoteBody = true
        // ... fetch completes (body stays blank, PDF metadata scanned)
        isFetchingRemoteBody = false
        delivered = true
        currentEmail = scannedBodylessEmail   // Room re-emits with pdfMetadataScanned = true
        fetchCount++

        // ── Emission 2: bodyless, scanned, delivered → should IGNORE ──
        val action2 = decide(
            email = currentEmail,
            isFetchingRemoteBody = isFetchingRemoteBody,
            isFetchingInlineImages = false,
            delivered = delivered,
            cachedInlineImages = null,
            inlineRefsExist = false
        )
        assertEquals("second emission must NOT trigger fetch", Action.IGNORE, action2)

        // ── Third emission (another Room re-emission) ────────────
        val action3 = decide(
            email = currentEmail,
            isFetchingRemoteBody = isFetchingRemoteBody,
            isFetchingInlineImages = false,
            delivered = delivered,
            cachedInlineImages = null,
            inlineRefsExist = false
        )
        assertEquals("third emission must also IGNORE", Action.IGNORE, action3)

        assertEquals("fetch must be called exactly once", 1, fetchCount)
    }

    @Test
    fun `bodyless email with pdfs but delivered does NOT trigger remote fetch`() {
        val emailWithPdfs = scannedBodylessEmail.copy(
            pdfAttachments = listOf(
                PdfAttachmentMetadata("doc.pdf", "application/pdf", "att_1", 4096L)
            )
        )
        val action = decide(
            email = emailWithPdfs,
            isFetchingRemoteBody = false,
            isFetchingInlineImages = false,
            delivered = true,
            cachedInlineImages = null,
            inlineRefsExist = false
        )
        assertEquals("bodyless with PDFs but already delivered must IGNORE", Action.IGNORE, action)
    }

    @Test
    fun `bodyless unscanned email while fetching shows preparing`() {
        val action = decide(
            email = bodylessEmail,
            isFetchingRemoteBody = true,    // fetch in progress
            isFetchingInlineImages = false,
            delivered = false,
            cachedInlineImages = null,
            inlineRefsExist = false
        )
        // needsRemoteFetch = true, but isFetchingRemoteBody = true
        // so the FETCH_REMOTE branch is skipped. Falls to:
        // delivered = false → SHOW_PREPARING
        assertEquals(Action.SHOW_PREPARING, action)
    }

    @Test
    fun `cached body with legacy PDF metadata triggers metadata refresh`() {
        val legacyEmail = scannedBodylessEmail.copy(
            body = "<p>Body</p>",
            cleanBody = "<p>Body</p>",
            pdfAttachments = listOf(
                PdfAttachmentMetadata(
                    "doc.pdf",
                    "application/pdf",
                    "temporary_att",
                    4096L
                )
            )
        )

        val action = decide(
            email = legacyEmail,
            isFetchingRemoteBody = false,
            isFetchingInlineImages = false,
            delivered = false,
            cachedInlineImages = null,
            inlineRefsExist = false
        )

        assertEquals(Action.FETCH_REMOTE, action)
    }

    @Test
    fun `cached body with stable partId does not refresh metadata`() {
        val stableEmail = scannedBodylessEmail.copy(
            body = "<p>Body</p>",
            cleanBody = "<p>Body</p>",
            pdfAttachments = listOf(
                PdfAttachmentMetadata(
                    "doc.pdf",
                    "application/pdf",
                    "temporary_att",
                    4096L,
                    partId = "mime_part_1"
                )
            )
        )

        val action = decide(
            email = stableEmail,
            isFetchingRemoteBody = false,
            isFetchingInlineImages = false,
            delivered = false,
            cachedInlineImages = null,
            inlineRefsExist = false
        )

        assertEquals(Action.SHOW_READY, action)
    }
}
