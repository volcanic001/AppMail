package com.david.mailapp.feature.emaildetail

import com.david.mailapp.core.localization.UiErrorReason
import com.david.mailapp.data.pdf.PdfDownloadState
import com.david.mailapp.data.remote.provider.BodyFetchResult
import com.david.mailapp.data.remote.provider.InlineImageRef
import com.david.mailapp.data.repository.EmailActionResult
import com.david.mailapp.data.repository.EmailResolutionFailureReason
import com.david.mailapp.data.repository.EmailResolutionResult
import com.david.mailapp.domain.model.Email
import com.david.mailapp.domain.model.EmailFolder
import com.david.mailapp.domain.model.PdfAttachmentMetadata
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

/**
 * Controllable fake for [EmailDetailEmailSource] used in ViewModel JVM tests.
 */
class FakeEmailDetailSource(
    private val emailId: String
) : EmailDetailEmailSource {

    // ── Flow observation ────────────────────────────────────────
    private val _roomFlow = MutableStateFlow<Email?>(null)
    var roomFlowValue: Flow<Email?> = _roomFlow

    fun emitRoomEmail(email: Email?) { _roomFlow.value = email }

    override fun observe(emailId: String): Flow<Email?> = roomFlowValue

    // ── Resolution ──────────────────────────────────────────────
    var resolveResult: EmailResolutionResult = EmailResolutionResult.NotFound
    var resolveError: Exception? = null
    var resolveGate: CompletableDeferred<Unit>? = null
    var resolveCallCount = 0

    override suspend fun resolveById(emailId: String): EmailResolutionResult {
        resolveCallCount++
        resolveGate?.await()
        resolveError?.let { throw it }
        return resolveResult
    }

    // ── Mark as read ────────────────────────────────────────────
    var markAsReadResult: EmailActionResult = EmailActionResult.Success
    var markAsReadCallCount = 0

    override suspend fun markAsRead(emailId: String): EmailActionResult {
        markAsReadCallCount++
        return markAsReadResult
    }

    // ── Body fetch ──────────────────────────────────────────────
    var bodyFetchResult: BodyFetchResult? = null
    var bodyFetchGate: CompletableDeferred<Unit>? = null
    var onBodyFetch: ((callCount: Int) -> Unit)? = null
    var bodyFetchCallCount = 0

    override suspend fun fetchAndCacheBody(emailId: String): BodyFetchResult? {
        bodyFetchCallCount++
        bodyFetchGate?.await()
        onBodyFetch?.invoke(bodyFetchCallCount)
        return bodyFetchResult
    }

    // ── Inline images ───────────────────────────────────────────
    var inlineImagesResult: Map<String, String> = emptyMap()
    var inlineImagesCallCount = 0
    var injectInlineImagesResult: String = ""

    override suspend fun downloadInlineImages(emailId: String, refs: List<InlineImageRef>): Map<String, String> {
        inlineImagesCallCount++
        return inlineImagesResult
    }

    override suspend fun injectInlineImages(html: String, images: Map<String, String>): String =
        injectInlineImagesResult.ifBlank { html }

    // ── PDF ─────────────────────────────────────────────────────
    override suspend fun checkPdfCache(emailId: String, stablePartId: String): PdfDownloadState.Ready? = null
    override suspend fun downloadPdf(emailId: String, metadata: PdfAttachmentMetadata): PdfDownloadState =
        PdfDownloadState.Error(com.david.mailapp.data.pdf.PdfDownloadFailure.NETWORK)
    override suspend fun getValidatedCachedPdf(emailId: String, stablePartId: String): File? = null

    companion object {
        fun sampleEmail(
            id: String = "e1",
            body: String = "",
            cleanBody: String = "",
            bodyBlank: Boolean = body.isBlank(),
            pdfScanned: Boolean = false,
            folder: EmailFolder = EmailFolder.Inbox
        ): Email = Email(
            id = id, threadId = "t1", from = "a@b.com", fromInitials = "A",
            to = "c@d.com", subject = "S", snippet = "snip", timestamp = 1L,
            isRead = false, isStarred = false, hasAttachments = false,
            labels = emptyList(), folder = folder,
            body = if (bodyBlank && body.isEmpty()) "" else body.ifEmpty { "<html>body</html>" },
            cleanBody = cleanBody,
            pdfAttachments = emptyList(), pdfMetadataScanned = pdfScanned
        )
    }
}
