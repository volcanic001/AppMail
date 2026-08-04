package com.david.mailapp.feature.emaildetail

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.david.mailapp.core.localization.UiErrorReason
import com.david.mailapp.core.session.SessionWriteGuardImpl
import com.david.mailapp.data.local.MailDatabase
import com.david.mailapp.data.local.entity.EmailEntity
import com.david.mailapp.data.pdf.PdfCacheManager
import com.david.mailapp.data.remote.provider.EmailLookupResult
import com.david.mailapp.data.repository.EmailRepository
import com.david.mailapp.domain.model.Email
import com.david.mailapp.domain.model.EmailFolder
import com.david.mailapp.testhelpers.FakeEmailProvider
import com.david.mailapp.testhelpers.FakeSessionWriteGuard
import com.david.mailapp.testhelpers.testEmail
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EmailDetailIntegrationTest {

    private lateinit var db: MailDatabase
    private lateinit var provider: FakeEmailProvider
    private lateinit var repository: EmailRepository
    private lateinit var context: android.content.Context
    private val viewModelStores = mutableListOf<ViewModelStore>()

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, MailDatabase::class.java).build()
        provider = FakeEmailProvider()
        val cacheDir = java.io.File(context.cacheDir, "detail_int_${System.nanoTime()}").apply { mkdirs() }
        repository = EmailRepository(
            database = db, providerFactory = { provider },
            pdfCacheManager = PdfCacheManager(cacheDir),
            writeGuard = FakeSessionWriteGuard()
        )
    }

    @After
    fun tearDown() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            viewModelStores.forEach(ViewModelStore::clear)
            viewModelStores.clear()
        }
        db.close()
    }

    private suspend fun createViewModel(
        emailId: String,
        source: EmailDetailEmailSource = RepositoryEmailDetailSource(repository)
    ): EmailDetailViewModel = withContext(Dispatchers.Main) {
        val store = ViewModelStore().also(viewModelStores::add)
        ViewModelProvider(
            store,
            EmailDetailViewModel.Factory(emailId, source)
        )[EmailDetailViewModel::class.java]
    }

    private suspend fun awaitTerminalState(viewModel: EmailDetailViewModel): EmailDetailUiState =
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(5_000) {
                viewModel.uiState.first { state ->
                    state is EmailDetailUiState.Ready ||
                        state is EmailDetailUiState.ResolutionError ||
                        state is EmailDetailUiState.BodyError
                }
            }
        }

    private suspend fun seed(id: String, folder: EmailFolder = EmailFolder.Inbox) {
        db.emailDao().upsertAll(
            listOf(EmailEntity.fromDomain(testEmail(id = id, folder = folder), folder))
        )
    }

    // ═══════════════════════════════════════════════════════════════
    // Resolution integration
    // ═══════════════════════════════════════════════════════════════

    @Test fun cachedEmail_opens_withoutRemoteCall() = runTest {
        seed("c1", EmailFolder.Inbox)
        val result = repository.resolveEmailById("c1")
        assertTrue(result is com.david.mailapp.data.repository.EmailResolutionResult.Found)
        assertEquals("no remote call for cached email", 0, provider.fetchEmailByIdCalls)
    }

    @Test fun absentEmail_recovered_and_persisted() = runTest {
        provider.fetchEmailByIdResult = EmailLookupResult.Found(
            testEmail("absent", folder = EmailFolder.Other, subject = "Recovered")
        )
        val result = repository.resolveEmailById("absent")
        assertTrue(result is com.david.mailapp.data.repository.EmailResolutionResult.Found)
        assertEquals("recovered subject", "Recovered",
            (result as com.david.mailapp.data.repository.EmailResolutionResult.Found).email.subject)
        assertEquals(1, provider.fetchEmailByIdCalls)
    }

    @Test fun remote_404_returns_notFound() = runTest {
        provider.fetchEmailByIdResult = EmailLookupResult.NotFound
        val result = repository.resolveEmailById("nope")
        assertEquals(
            com.david.mailapp.data.repository.EmailResolutionResult.NotFound,
            result
        )
    }

    @Test fun retry_afterRecoverableFailure_succeeds() = runTest {
        provider.fetchEmailByIdResult = EmailLookupResult.Failure(
            com.david.mailapp.data.remote.provider.EmailLookupFailureReason.NO_CONNECTION
        )
        val first = repository.resolveEmailById("retry-me")
        assertTrue(first is com.david.mailapp.data.repository.EmailResolutionResult.Failure)

        provider.fetchEmailByIdResult = EmailLookupResult.Found(
            testEmail("retry-me", folder = EmailFolder.Other)
        )
        val second = repository.resolveEmailById("retry-me")
        assertTrue(second is com.david.mailapp.data.repository.EmailResolutionResult.Found)
        assertEquals(2, provider.fetchEmailByIdCalls)
    }

    // ═══════════════════════════════════════════════════════════════
    // Subfase 4 — matriz de carpetas (correo ausente de Room)
    // ═══════════════════════════════════════════════════════════════

    private suspend fun assertResolutionMatrix(
        id: String,
        labels: List<String>,
        emailFolder: EmailFolder,
        expectedPersistedFolder: String
    ) {
        val remoteEmail = testEmail(id = id, folder = emailFolder, isRead = true).copy(
            labels = labels,
            body = "<html>ready-$id</html>",
            cleanBody = "<html>ready-$id</html>",
            pdfMetadataScanned = true
        )
        provider.fetchEmailByIdResult = EmailLookupResult.Found(
            remoteEmail
        )
        val viewModel = createViewModel(id)
        val state = awaitTerminalState(viewModel)

        assertTrue("detail reaches Ready for $id, got $state", state is EmailDetailUiState.Ready)
        val email = (state as EmailDetailUiState.Ready).email
        assertEquals("folder preserved", emailFolder, email.folder)
        assertEquals("labels preserved", labels, email.labels)
        assertEquals("body rendered", remoteEmail.body, email.body)
        assertEquals("one remote resolution", 1, provider.fetchEmailByIdCalls)

        // Persisted entity keeps the email's primary folder
        val entity = checkNotNull(db.emailDao().getEntitiesByIdsSync(listOf(id)).firstOrNull())
        assertEquals("persisted folder", expectedPersistedFolder, entity.folder)
    }

    @Test fun matrix_inbox_absent_email_resolves_and_persists() = runTest {
        assertResolutionMatrix("m-inbox", listOf("INBOX", "IMPORTANT"), EmailFolder.Inbox, "inbox")
    }

    @Test fun matrix_sent_absent_email_resolves_and_persists() = runTest {
        assertResolutionMatrix("m-sent", listOf("SENT"), EmailFolder.Other, "other")
    }

    @Test fun matrix_archived_absent_email_resolves_and_persists() = runTest {
        assertResolutionMatrix("m-archived", listOf("ARCHIVE"), EmailFolder.Other, "other")
    }

    @Test fun matrix_trash_absent_email_resolves_and_persists() = runTest {
        assertResolutionMatrix("m-trash", listOf("TRASH"), EmailFolder.Trash, "trash")
    }

    @Test fun matrix_trash_sent_absent_email_resolves_and_persists() = runTest {
        assertResolutionMatrix("m-trash-sent", listOf("TRASH", "SENT"), EmailFolder.Trash, "trash")
    }

    @Test fun matrix_email_absent_from_room_doesNotContaminate_inbox_or_trash() = runTest {
        seed("existing-inbox", EmailFolder.Inbox)
        seed("existing-trash", EmailFolder.Trash)
        provider.fetchEmailByIdResult = EmailLookupResult.Found(
            testEmail(id = "sent-email", folder = EmailFolder.Other).copy(labels = listOf("SENT"))
        )
        val result = repository.resolveEmailById("sent-email")
        assertTrue(result is com.david.mailapp.data.repository.EmailResolutionResult.Found)

        val inbox = db.emailDao().getEntitiesByFolderSync("inbox").map { it.id }
        val trash = db.emailDao().getEntitiesByFolderSync("trash").map { it.id }
        assertEquals(listOf("existing-inbox"), inbox)
        assertEquals(listOf("existing-trash"), trash)
    }

    @Test
    fun accountChange_duringResolution_showsAccountChanged_withoutObservingOldRoom() = runTest {
        val guard = SessionWriteGuardImpl().also { it.activate() }
        val guardedRepository = EmailRepository(
            database = db,
            providerFactory = { provider },
            pdfCacheManager = PdfCacheManager(
                java.io.File(context.cacheDir, "detail_account_${System.nanoTime()}").apply { mkdirs() }
            ),
            writeGuard = guard
        )
        val gate = CompletableDeferred<Unit>()
        provider.fetchEmailByIdDeferred = gate
        provider.fetchEmailByIdResult = EmailLookupResult.Found(
            testEmail("account-change", folder = EmailFolder.Other, subject = "old account")
        )

        val delegate = RepositoryEmailDetailSource(guardedRepository)
        val trackingSource = object : EmailDetailEmailSource by delegate {
            var observeCalls = 0

            override fun observe(emailId: String): Flow<Email?> {
                observeCalls++
                return delegate.observe(emailId)
            }
        }
        val viewModel = createViewModel("account-change", trackingSource)

        withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(5_000) {
                while (provider.fetchEmailByIdCalls == 0) delay(10)
            }
        }
        guard.invalidate()
        guard.activate()
        gate.complete(Unit)

        val state = awaitTerminalState(viewModel)
        assertTrue("expected ResolutionError, got $state", state is EmailDetailUiState.ResolutionError)
        state as EmailDetailUiState.ResolutionError
        assertEquals(UiErrorReason.ACCOUNT_CHANGED, state.reason)
        assertEquals(false, state.retryable)
        assertEquals("old Room must never be observed", 0, trackingSource.observeCalls)
        assertTrue(
            "old account email must not be persisted",
            db.emailDao().getEntitiesByIdsSync(listOf("account-change")).isEmpty()
        )
    }
}
