package com.david.mailapp.feature.emaildetail

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.david.mailapp.data.local.MailDatabase
import com.david.mailapp.data.local.entity.EmailEntity
import com.david.mailapp.data.pdf.PdfCacheManager
import com.david.mailapp.data.remote.provider.BodyFetchResult
import com.david.mailapp.data.remote.provider.InlineImageRef
import com.david.mailapp.data.repository.EmailRepository
import com.david.mailapp.domain.model.EmailFolder
import com.david.mailapp.testhelpers.FakeEmailProvider
import com.david.mailapp.testhelpers.FakeSessionWriteGuard
import com.david.mailapp.testhelpers.testEmail
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EmailDetailCancellationTest {

    private lateinit var database: MailDatabase
    private lateinit var provider: FakeEmailProvider
    private lateinit var repository: EmailRepository
    private lateinit var cacheDir: File
    private lateinit var viewModelStore: ViewModelStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MailDatabase::class.java).build()
        provider = FakeEmailProvider()
        cacheDir = File(context.cacheDir, "detail_cancel_${System.nanoTime()}").apply { mkdirs() }
        repository = EmailRepository(
            database = database,
            providerFactory = { provider },
            pdfCacheManager = PdfCacheManager(cacheDir),
            writeGuard = FakeSessionWriteGuard()
        )
        viewModelStore = ViewModelStore()
    }

    @After
    fun tearDown() {
        viewModelStore.clear()
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

        awaitCondition("body provider was not invoked") { provider.fetchBodyCalls == 1 }
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

        awaitCondition("inline provider was not invoked") { provider.inlineImagesCalls == 1 }
        val storedAfterBodyFetch = database.emailDao()
            .getEntitiesByIdsSync(listOf(email.id))
            .single()
        delay(50)

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

    private fun createViewModel(emailId: String): EmailDetailViewModel {
        return ViewModelProvider(
            viewModelStore,
            EmailDetailViewModel.Factory(emailId, repository)
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
