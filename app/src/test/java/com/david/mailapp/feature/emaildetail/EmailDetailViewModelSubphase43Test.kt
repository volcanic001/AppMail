package com.david.mailapp.feature.emaildetail

import com.david.mailapp.data.cleaner.HtmlCleanResult
import com.david.mailapp.data.pdf.PdfDownloadState
import com.david.mailapp.data.repository.EmailActionResult
import com.david.mailapp.data.repository.EmailContentRecoveryResult
import com.david.mailapp.data.repository.EmailResolutionResult
import com.david.mailapp.domain.model.Email
import com.david.mailapp.domain.model.EmailBodyKind
import com.david.mailapp.domain.model.EmailContentState
import com.david.mailapp.domain.model.EmailFolder
import com.david.mailapp.domain.model.EmailInlineReference
import com.david.mailapp.domain.model.PdfAttachmentMetadata
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class EmailDetailViewModelSubphase43Test {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createEmailWithCid(
        id: String = "e20",
        cleanBody: String = "<p>Text</p><img src=\"cid:img1\">",
        refs: List<EmailInlineReference> = listOf(EmailInlineReference("img1", "att1", "image/png"))
    ) = Email(
        id = id,
        threadId = "t20",
        from = "sender@example.com",
        fromInitials = "S",
        to = "me@example.com",
        subject = "Progressive Images Test",
        snippet = "Snippet",
        timestamp = 100000L,
        isRead = true,
        isStarred = false,
        hasAttachments = false,
        labels = emptyList(),
        folder = EmailFolder.Inbox,
        body = cleanBody,
        cleanBody = cleanBody,
        contentState = EmailContentState.READY,
        bodyKind = EmailBodyKind.HTML,
        inlineReferences = refs
    )

    private class ControlledSource(
        private val email: Email
    ) : EmailDetailEmailSource {
        val flow = MutableStateFlow<Email?>(email)
        var downloadCallCount = 0
        var downloadedRefs = mutableListOf<EmailInlineReference>()
        var downloadGate: CompletableDeferred<Unit>? = null
        var downloadResult: Map<String, String> = mapOf("img1" to "data:image/png;base64,AAA")
        var downloadException: Exception? = null

        override fun observe(emailId: String): Flow<Email?> = flow
        override suspend fun resolveById(emailId: String) = EmailResolutionResult.Found(email)
        override suspend fun markAsRead(emailId: String) = EmailActionResult.Success
        override suspend fun prepareHtmlBody(email: Email): HtmlCleanResult = HtmlCleanResult.Cleaned(email.cleanBody)
        override suspend fun recoverContentById(emailId: String) = EmailContentRecoveryResult.NotFound
        override suspend fun recordContentAccess(emailId: String) {}
        override suspend fun checkPdfCache(emailId: String, stablePartId: String): PdfDownloadState.Ready? = null
        override suspend fun downloadPdf(emailId: String, metadata: PdfAttachmentMetadata) = PdfDownloadState.Idle
        override suspend fun getValidatedCachedPdf(emailId: String, stablePartId: String): File? = null

        override suspend fun downloadInlineImages(
            emailId: String,
            refs: List<EmailInlineReference>
        ): Map<String, String> {
            downloadCallCount++
            downloadedRefs.addAll(refs)
            downloadGate?.await()
            downloadException?.let { throw it }
            return downloadResult
        }

        override suspend fun injectInlineImages(html: String, images: Map<String, String>): String {
            var result = html
            for ((cid, dataUri) in images) {
                result = result.replace("cid:$cid", dataUri, ignoreCase = true)
            }
            return result
        }
    }

    @Test
    fun `html with cid publishes Ready with inlineImagesLoading true before download finishes`() = runTest {
        val email = createEmailWithCid()
        val source = ControlledSource(email)
        val gate = CompletableDeferred<Unit>()
        source.downloadGate = gate

        val viewModel = EmailDetailViewModel("e20", source, testDispatcher)

        val state = viewModel.uiState.value
        assertTrue("State should be Ready", state is EmailDetailUiState.Ready)
        val ready = state as EmailDetailUiState.Ready
        assertTrue("inlineImagesLoading should be true while download is pending", ready.inlineImagesLoading)
        assertEquals(email.cleanBody, ready.email.body)

        // Release download
        gate.complete(Unit)
        advanceUntilIdle()

        val updatedState = viewModel.uiState.value as EmailDetailUiState.Ready
        assertFalse("inlineImagesLoading should be false after download finishes", updatedState.inlineImagesLoading)
        assertEquals("<p>Text</p><img src=\"data:image/png;base64,AAA\">", updatedState.email.body)
        assertEquals(1, source.downloadCallCount)
    }

    @Test
    fun `partial cid resolution injects available images and finishes loading`() = runTest {
        val refs = listOf(
            EmailInlineReference("img1", "att1", "image/png"),
            EmailInlineReference("img2", "att2", "image/png")
        )
        val email = createEmailWithCid(
            cleanBody = "<img src=\"cid:img1\"><img src=\"cid:img2\">",
            refs = refs
        )
        val source = ControlledSource(email)
        // Only img1 resolves, img2 missing/failed
        source.downloadResult = mapOf("img1" to "data:image/png;base64,AAA")

        val viewModel = EmailDetailViewModel("e20", source, testDispatcher)
        advanceUntilIdle()

        val state = viewModel.uiState.value as EmailDetailUiState.Ready
        assertFalse(state.inlineImagesLoading)
        assertEquals("<img src=\"data:image/png;base64,AAA\"><img src=\"cid:img2\">", state.email.body)
    }

    @Test
    fun `download exception retains Ready with cleanBody and completes loading`() = runTest {
        val email = createEmailWithCid()
        val source = ControlledSource(email)
        source.downloadException = java.io.IOException("Network offline")

        val viewModel = EmailDetailViewModel("e20", source, testDispatcher)
        advanceUntilIdle()

        val state = viewModel.uiState.value as EmailDetailUiState.Ready
        assertFalse(state.inlineImagesLoading)
        assertEquals(email.cleanBody, state.email.body)
    }

    @Test
    fun `duplicate cid references deduplicate before download`() = runTest {
        val refs = listOf(
            EmailInlineReference("img1", "att1", "image/png"),
            EmailInlineReference("img1", "att1", "image/png")
        )
        val email = createEmailWithCid(refs = refs)
        val source = ControlledSource(email)

        val viewModel = EmailDetailViewModel("e20", source, testDispatcher)
        advanceUntilIdle()

        assertEquals(1, source.downloadedRefs.size)
    }
}
