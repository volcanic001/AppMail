package com.david.mailapp.feature.emaildetail

import com.david.mailapp.core.localization.UiErrorReason
import com.david.mailapp.domain.model.Email
import com.david.mailapp.domain.model.EmailFolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests JVM de contratos para EmailDetailUiState.BodyError.
 *
 * Verifica que BodyError transporta UiErrorReason, no strings,
 * y que las tres razones de detalle se mantienen diferenciadas.
 */
class EmailDetailContractsTest {

    private val sampleEmail = Email(
        id = "msg1",
        threadId = "t1",
        from = "from@test.com",
        fromInitials = "F",
        to = "to@test.com",
        subject = "Test",
        snippet = "...",
        timestamp = 1L,
        isRead = true,
        isStarred = false,
        hasAttachments = false,
        labels = emptyList(),
        folder = EmailFolder.Inbox
    )

    // ─────────────────────────────────────────────────────────────
    // BodyError transporta UiErrorReason
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `BodyError transporta UiErrorReason no String`() {
        val error = EmailDetailUiState.BodyError(null, UiErrorReason.EMAIL_NOT_FOUND)
        assertEquals(UiErrorReason.EMAIL_NOT_FOUND, error.reason)
    }

    @Test
    fun `BodyError con EMAIL_BODY_LOAD_FAILED`() {
        val error = EmailDetailUiState.BodyError(sampleEmail, UiErrorReason.EMAIL_BODY_LOAD_FAILED)
        assertEquals(UiErrorReason.EMAIL_BODY_LOAD_FAILED, error.reason)
        assertEquals(sampleEmail, error.email)
    }

    @Test
    fun `BodyError con EMAIL_BODY_PDFS_ONLY`() {
        val error = EmailDetailUiState.BodyError(sampleEmail, UiErrorReason.EMAIL_BODY_PDFS_ONLY)
        assertEquals(UiErrorReason.EMAIL_BODY_PDFS_ONLY, error.reason)
        assertEquals(sampleEmail, error.email)
    }

    @Test
    fun `BodyError con EMAIL_NOT_FOUND tiene email nulo`() {
        val error = EmailDetailUiState.BodyError(null, UiErrorReason.EMAIL_NOT_FOUND)
        assertEquals(null, error.email)
    }

    // ─────────────────────────────────────────────────────────────
    // Las tres razones son diferenciadas
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `razones de detalle son distintas entre si`() {
        assertTrue(UiErrorReason.EMAIL_NOT_FOUND != UiErrorReason.EMAIL_BODY_LOAD_FAILED)
        assertTrue(UiErrorReason.EMAIL_NOT_FOUND != UiErrorReason.EMAIL_BODY_PDFS_ONLY)
        assertTrue(UiErrorReason.EMAIL_BODY_LOAD_FAILED != UiErrorReason.EMAIL_BODY_PDFS_ONLY)
    }
}
