package com.david.mailapp.feature.emaildetail

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.david.mailapp.data.local.MailDatabase
import com.david.mailapp.data.local.entity.EmailEntity
import com.david.mailapp.data.pdf.PdfCacheManager
import com.david.mailapp.data.pdf.PdfDownloadState
import com.david.mailapp.data.remote.provider.BodyFetchResult
import com.david.mailapp.data.remote.provider.InlineImageRef
import com.david.mailapp.data.repository.EmailRepository
import com.david.mailapp.domain.model.EmailFolder
import com.david.mailapp.domain.model.PdfAttachmentMetadata
import com.david.mailapp.testhelpers.FakeEmailProvider
import com.david.mailapp.testhelpers.FakeSessionWriteGuard
import com.david.mailapp.testhelpers.testEmail
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class EmailDetailCancellationTest {

    private lateinit var database: MailDatabase
    private lateinit var provider: FakeEmailProvider
    private lateinit var repository: EmailRepository
    private lateinit var pdfCache: PdfCacheManager
    private lateinit var cacheDir: File
    private lateinit var viewModelStore: ViewModelStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MailDatabase::class.java).build()
        provider = FakeEmailProvider()
        cacheDir = File(context.cacheDir, "detail_cancel_${System.nanoTime()}").apply { mkdirs() }
        pdfCache = PdfCacheManager(cacheDir)
        repository = EmailRepository(
            database = database,
            providerFactory = { provider },
            pdfCacheManager = pdfCache,
            writeGuard = FakeSessionWriteGuard()
        )
        viewModelStore = ViewModelStore()
    }

    @After
    fun tearDown() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync { viewModelStore.clear() }
        instrumentation.waitForIdleSync()
        // Room Flow cancellation may already have dispatched a query to its
        // executor. Let that cancellation settle before closing the pool.
        runBlocking { delay(100) }
        database.close()
        cacheDir.deleteRecursively()
    }

    @Test
    fun bodyCancellationKeepsPreparingStateAndDoesNotWriteRoom() = runBlocking {
        val email = testEmail("body-cancel", isRead = true)
        database.emailDao().upsertAll(
            listOf(EmailEntity.fromDomain(email, EmailFolder.Inbox))
        )
        provider.fetchBodyError = CancellationException("sentinel-body")

        val viewModel = createViewModel(email.id)

        awaitCondition("body provider was not invoked") { provider.fetchBodyCalls >= 1 }
        delay(50)

        val state = viewModel.uiState.value
        assertTrue("Expected PreparingBody, got $state", state is EmailDetailUiState.PreparingBody)

        val stored = database.emailDao().getEntitiesByIdsSync(listOf(email.id)).single()
        assertEquals("", stored.body)
        assertEquals("", stored.cleanBody)
        assertFalse(stored.pdfMetadataScanned)
    }

    @Test
    fun inlineCancellationKeepsPendingReadyStateAndDoesNotWriteFallback() = runBlocking {
        val email = testEmail("inline-cancel", isRead = true)
        database.emailDao().upsertAll(
            listOf(EmailEntity.fromDomain(email, EmailFolder.Inbox))
        )

        val bodyWithCid = "<html><body><img src=\"cid:image-1\"></body></html>"
        provider.fetchBodyResult = BodyFetchResult(
            rawBody = bodyWithCid,
            inlineRefs = listOf(
                InlineImageRef(
                    contentId = "image-1",
                    attachmentId = "attachment-1",
                    mimeType = "image/png"
                )
            )
        )
        provider.inlineImagesError = CancellationException("sentinel-inline")

        val viewModel = createViewModel(email.id)

        awaitCondition("inline provider was not invoked") { provider.inlineImagesCalls >= 1 }
        awaitCondition("pending inline Ready state was not published") {
            val state = viewModel.uiState.value
            state is EmailDetailUiState.Ready &&
                state.inlineImagesLoading &&
                state.email.body.contains("cid:image-1")
        }
        val storedAfterBodyFetch = database.emailDao()
            .getEntitiesByIdsSync(listOf(email.id))
            .single()

        val state = viewModel.uiState.value
        assertTrue("Expected Ready, got $state", state is EmailDetailUiState.Ready)
        state as EmailDetailUiState.Ready
        assertTrue(state.inlineImagesLoading)
        assertEquals(storedAfterBodyFetch.cleanBody, state.email.body)
        assertTrue(state.email.body.contains("cid:image-1"))

        val storedAfterCancellation = database.emailDao()
            .getEntitiesByIdsSync(listOf(email.id))
            .single()
        assertEquals(storedAfterBodyFetch, storedAfterCancellation)
        assertEquals(bodyWithCid, storedAfterCancellation.body)
        assertFalse(storedAfterCancellation.body.contains("data:image/"))
    }

    @Test
    fun abandonDetailDuringBodyFetchCancelsCallAndDoesNotModifyRoomOrState() = runBlocking {
        val email = testEmail("body-cancel-abandon", isRead = true)
        database.emailDao().upsertAll(
            listOf(EmailEntity.fromDomain(email, EmailFolder.Inbox))
        )
        val bodyGate = CompletableDeferred<Unit>()
        provider.fetchBodyDeferred = bodyGate
        provider.fetchBodyResult = BodyFetchResult(
            rawBody = "Fetched Body",
            inlineRefs = emptyList(),
            pdfAttachments = emptyList()
        )

        createViewModel(email.id)

        // Wait for body fetch to start
        awaitCondition("body fetch start") { provider.fetchBodyCalls == 1 }

        // Clear viewModelStore (simulating leaving the screen)
        viewModelStore.clear()

        // Complete the gate
        bodyGate.complete(Unit)
        delay(50)

        // Check provider cancellation registered
        assertTrue("Provider should detect cancellation", provider.wasCancelledFetchBody)

        // Verify Room was not modified
        val stored = database.emailDao().getEntitiesByIdsSync(listOf(email.id)).single()
        assertEquals("", stored.body)
    }

    @Test
    fun abandonDetailDuringInlineImagesDoesNotWriteFallbackOrDataImage() = runBlocking {
        val email = testEmail("inline-cancel-abandon", isRead = true)
        val bodyWithCid = "<html><body><img src=\"cid:image-1\"></body></html>"
        database.emailDao().upsertAll(
            listOf(EmailEntity.fromDomain(email, EmailFolder.Inbox))
        )

        provider.fetchBodyResult = BodyFetchResult(
            rawBody = bodyWithCid,
            inlineRefs = listOf(
                InlineImageRef(
                    contentId = "image-1",
                    attachmentId = "attachment-1",
                    mimeType = "image/png"
                )
            )
        )

        val inlineGate = CompletableDeferred<Unit>()
        provider.downloadInlineImagesDeferred = inlineGate
        provider.inlineImagesResult = mapOf("image-1" to "data:image/png;base64,AAAA")

        createViewModel(email.id)

        // Wait for inline images download to start
        awaitCondition("inline images start") { provider.inlineImagesCalls == 1 }

        // Clear viewModelStore
        viewModelStore.clear()

        // Complete the gate
        inlineGate.complete(Unit)
        delay(50)

        assertTrue(provider.wasCancelledInlineImages)

        // Verify database has body with cid, but not inlined base64
        val stored = database.emailDao().getEntitiesByIdsSync(listOf(email.id)).single()
        assertEquals(bodyWithCid, stored.body)
        assertFalse(stored.body.contains("data:image/"))
    }

    @Test
    fun abandonDetailDuringPdfDownloadCancelsNetworkAndDoesNotCreateFileOrEmitOpenSave() = runBlocking {
        val email = testEmail("pdf-cancel-abandon", isRead = true)
        val attachment = PdfAttachmentMetadata(
            fileName = "test.pdf",
            mimeType = "application/pdf",
            attachmentId = "att-1",
            sizeBytes = 100L,
            partId = "stable-1"
        )
        val emailWithPdf = email.copy(
            pdfAttachments = listOf(attachment),
            pdfMetadataScanned = true,
            body = "Hello PDF"
        )
        database.emailDao().upsertAll(
            listOf(EmailEntity.fromDomain(emailWithPdf, EmailFolder.Inbox))
        )

        val pdfGate = CompletableDeferred<Unit>()
        provider.downloadAttachmentDeferred = pdfGate

        val viewModel = createViewModel(emailWithPdf.id)

        // Start download by clicking
        viewModel.onPdfAttachmentClick(attachment)

        awaitCondition("pdf download start") { provider.downloadAttachmentCalls == 1 }

        // Capture event collector
        val events = mutableListOf<PdfExternalActionRequest>()
        val collectJob = launch(Dispatchers.Unconfined) {
            viewModel.pdfOpenEvents.collect { events.add(it) }
        }

        // Clear viewModelStore
        viewModelStore.clear()

        // Observe cancellation before releasing the provider gate. Completing
        // the gate first races normal completion against viewModelScope teardown.
        awaitCondition("pdf download cancellation") {
            provider.wasCancelledDownloadAttachment
        }

        // Release any residual waiter after cancellation has been observed.
        pdfGate.complete(Unit)

        // Verify file does not exist
        val targetFile = pdfCache.getCachedFile(emailWithPdf.id, "stable-1")
        assertNull("Target file should not exist in cache", targetFile)
        val cacheResidues = cacheDir.walkTopDown()
            .filter { it.isFile && (it.extension == "pdf" || it.extension == "tmp") }
            .toList()
        assertTrue("No final or temporary PDF files should remain: $cacheResidues", cacheResidues.isEmpty())

        // Verify no events emitted
        delay(50)
        collectJob.cancel()
        assertTrue("No open/save events should have been emitted", events.isEmpty())
    }

    @Test
    fun providerIgnoresCancellationAndReturnsLateDoesNotModifyState() = runBlocking {
        val email = testEmail("body-cancel-late", isRead = true)
        database.emailDao().upsertAll(
            listOf(EmailEntity.fromDomain(email, EmailFolder.Inbox))
        )
        val bodyGate = CompletableDeferred<Unit>()
        provider.fetchBodyDeferred = bodyGate
        provider.ignoreCancellationFetchBody = true
        provider.fetchBodyResult = BodyFetchResult(
            rawBody = "Fetched Body Late",
            inlineRefs = emptyList(),
            pdfAttachments = emptyList()
        )

        val viewModel = createViewModel(email.id)

        awaitCondition("body fetch start") { provider.fetchBodyCalls == 1 }
        val stateBeforeDismissal = viewModel.uiState.value

        // Clear viewModelStore
        viewModelStore.clear()

        // Complete the gate
        bodyGate.complete(Unit)
        delay(50)

        // Verify provider ran to completion after ignoring cancellation.
        assertTrue(provider.wasCancelledFetchBody)
        assertTrue(provider.completedFetchBody)

        // Verify neither ViewModel state nor Room changed after the late response.
        assertEquals(stateBeforeDismissal, viewModel.uiState.value)
        val stored = database.emailDao().getEntitiesByIdsSync(listOf(email.id)).single()
        assertEquals("", stored.body)
    }

    @Test
    fun pdfCommittedBeforeDismissalRemainsValidInCache() = runBlocking {
        val email = testEmail("pdf-commit-before-dismiss", isRead = true)
        val attachment = PdfAttachmentMetadata(
            fileName = "test.pdf",
            mimeType = "application/pdf",
            attachmentId = "att-2",
            sizeBytes = 100L,
            partId = "stable-2"
        )
        val emailWithPdf = email.copy(
            pdfAttachments = listOf(attachment),
            pdfMetadataScanned = true,
            body = "Hello PDF"
        )
        database.emailDao().upsertAll(
            listOf(EmailEntity.fromDomain(emailWithPdf, EmailFolder.Inbox))
        )

        val viewModel = createViewModel(emailWithPdf.id)

        viewModel.onPdfAttachmentClick(attachment)

        awaitCondition("pdf download done") { viewModel.pdfDownloadStates.value["stable-2"] is PdfDownloadState.Ready }

        // Clear VM store
        viewModelStore.clear()

        // Create new VM
        val newViewModel = createViewModel(emailWithPdf.id)

        awaitCondition("pdf cache check restored") { newViewModel.pdfDownloadStates.value["stable-2"] is PdfDownloadState.Ready }

        val finalState = newViewModel.pdfDownloadStates.value["stable-2"]
        assertTrue(finalState is PdfDownloadState.Ready)
        assertNotNull(pdfCache.getCachedFile(emailWithPdf.id, "stable-2"))
    }

    private fun createViewModel(emailId: String): EmailDetailViewModel {
        val source = RepositoryEmailDetailSource(repository)
        return ViewModelProvider(
            viewModelStore,
            EmailDetailViewModel.Factory(emailId, source)
        )[EmailDetailViewModel::class.java]
    }

    private suspend fun awaitCondition(message: String, condition: () -> Boolean) {
        withTimeout(5_000) {
            while (!condition()) {
                delay(10)
            }
        }
        assertTrue(message, condition())
    }
}
