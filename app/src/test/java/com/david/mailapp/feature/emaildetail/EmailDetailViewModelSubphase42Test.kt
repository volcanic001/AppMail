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
import com.david.mailapp.domain.model.PdfAttachmentMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class EmailDetailViewModelSubphase42Test {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createDummyEmail(
        id: String = "e10",
        body: String = "<p>Raw HTML</p>",
        cleanBody: String = "",
        bodyKind: EmailBodyKind = EmailBodyKind.HTML
    ) = Email(
        id = id,
        threadId = "t10",
        from = "sender@example.com",
        fromInitials = "S",
        to = "me@example.com",
        subject = "Test Subject",
        snippet = "Snippet",
        timestamp = 100000L,
        isRead = true,
        isStarred = false,
        hasAttachments = false,
        labels = emptyList(),
        folder = EmailFolder.Inbox,
        body = body,
        cleanBody = cleanBody,
        contentState = EmailContentState.READY,
        bodyKind = bodyKind
    )

    private class FakeEmailDetailSource(
        private val email: Email,
        private val prepareResult: HtmlCleanResult = HtmlCleanResult.Cleaned("<div style=\"margin:0 16px;\"><p>Raw HTML</p></div>")
    ) : EmailDetailEmailSource {
        val flow = MutableStateFlow<Email?>(email)

        override fun observe(emailId: String): Flow<Email?> = flow
        override suspend fun resolveById(emailId: String) = EmailResolutionResult.Found(email)
        override suspend fun markAsRead(emailId: String) = EmailActionResult.Success
        override suspend fun prepareHtmlBody(email: Email): HtmlCleanResult = prepareResult
        override suspend fun recoverContentById(emailId: String) = EmailContentRecoveryResult.NotFound
        override suspend fun downloadInlineImages(emailId: String, refs: List<com.david.mailapp.domain.model.EmailInlineReference>) = emptyMap<String, String>()
        override suspend fun injectInlineImages(html: String, images: Map<String, String>) = html
        override suspend fun recordContentAccess(emailId: String) {}
        override suspend fun checkPdfCache(emailId: String, stablePartId: String): PdfDownloadState.Ready? = null
        override suspend fun downloadPdf(emailId: String, metadata: PdfAttachmentMetadata) = PdfDownloadState.Idle
        override suspend fun getValidatedCachedPdf(emailId: String, stablePartId: String): File? = null
    }

    @Test
    fun `uncleaned HTML email transits PreparingBody to Ready`() = runTest {
        val email = createDummyEmail(cleanBody = "")
        val source = FakeEmailDetailSource(email)
        val viewModel = EmailDetailViewModel("e10", source, testDispatcher)

        val state = viewModel.uiState.value
        assertTrue("Expected Ready state after preparation, got $state", state is EmailDetailUiState.Ready)
        val readyState = state as EmailDetailUiState.Ready
        assertEquals("<div style=\"margin:0 16px;\"><p>Raw HTML</p></div>", readyState.email.body)
    }

    @Test
    fun `already clean HTML email delivers Ready immediately`() = runTest {
        val cleanText = "<div style=\"margin:0 16px;\"><p>Cleaned</p></div>"
        val email = createDummyEmail(cleanBody = cleanText)
        val source = FakeEmailDetailSource(email)
        val viewModel = EmailDetailViewModel("e10", source, testDispatcher)

        val state = viewModel.uiState.value
        assertTrue(state is EmailDetailUiState.Ready)
        assertEquals(cleanText, (state as EmailDetailUiState.Ready).email.body)
    }

    @Test
    fun `plain text email delivers Ready immediately without HTML preparation`() = runTest {
        val email = createDummyEmail(body = "Literal plain text", cleanBody = "", bodyKind = EmailBodyKind.PLAIN_TEXT)
        val source = FakeEmailDetailSource(email)
        val viewModel = EmailDetailViewModel("e10", source, testDispatcher)

        val state = viewModel.uiState.value
        assertTrue(state is EmailDetailUiState.Ready)
        assertEquals("Literal plain text", (state as EmailDetailUiState.Ready).email.body)
    }
}
