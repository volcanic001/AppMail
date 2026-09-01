package com.david.mailapp.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.david.mailapp.data.local.MailDatabase
import com.david.mailapp.data.local.entity.EmailEntity
import com.david.mailapp.data.pdf.PdfCacheManager
import com.david.mailapp.domain.model.Email
import com.david.mailapp.domain.model.EmailFolder
import com.david.mailapp.domain.model.PageItemFailure
import com.david.mailapp.domain.model.PageItemFailureKind
import com.david.mailapp.domain.model.PaginatedResult
import com.david.mailapp.domain.model.PdfAttachmentMetadata
import com.david.mailapp.testhelpers.FakeEmailProvider
import com.david.mailapp.testhelpers.FakeSessionWriteGuard
import com.david.mailapp.testhelpers.testEmail
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EmailRepositoryReadSyncSearchContractsTest {

    private lateinit var database: MailDatabase
    private lateinit var provider: FakeEmailProvider
    private lateinit var repository: EmailRepository
    private lateinit var cacheDir: File
    private var providerFactoryReads = 0

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, MailDatabase::class.java).build()
        provider = FakeEmailProvider()
        cacheDir = File(context.cacheDir, "repo_read_${System.nanoTime()}").apply { mkdirs() }
        repository = EmailRepository(
            database = database,
            providerFactory = {
                providerFactoryReads++
                provider
            },
            pdfCacheManager = PdfCacheManager(cacheDir),
            writeGuard = FakeSessionWriteGuard()
        )
    }

    @After
    fun tearDown() {
        database.close()
        cacheDir.deleteRecursively()
    }

    @Test
    fun getInbox_and_getTrash_emit_lightweight_models_while_getEmailById_emits_full_model() = runTest {
        val email = testEmail("heavy-1", timestamp = 1_000L).copy(
            body = "<html>large body</html>",
            cleanBody = "large body",
            pdfAttachments = listOf(PdfAttachmentMetadata("file.pdf", "application/pdf", "att1", 100L)),
            pdfMetadataScanned = true,
            rfcMessageId = "<msg1@test>",
            rfcReferences = "<ref1@test>"
        )

        database.emailDao().upsertAll(listOf(EmailEntity.fromDomain(email, EmailFolder.Inbox)))

        val inboxItem = repository.getInbox().first().first()
        assertEquals("heavy-1", inboxItem.id)
        assertEquals("", inboxItem.body)
        assertEquals("", inboxItem.cleanBody)
        assertTrue(inboxItem.pdfAttachments.isEmpty())
        assertFalse(inboxItem.pdfMetadataScanned)
        assertNull(inboxItem.rfcMessageId)
        assertNull(inboxItem.rfcReferences)

        val fullItem = repository.getEmailById("heavy-1").first()!!
        assertEquals("heavy-1", fullItem.id)
        assertEquals("<html>large body</html>", fullItem.body)
        assertEquals("large body", fullItem.cleanBody)
        assertEquals(1, fullItem.pdfAttachments.size)
        assertTrue(fullItem.pdfMetadataScanned)
        assertEquals("<msg1@test>", fullItem.rfcMessageId)

        database.emailDao().moveToFolder("heavy-1", "trash")
        val trashItem = repository.getTrash().first().first()
        assertEquals("heavy-1", trashItem.id)
        assertEquals("", trashItem.body)
        assertTrue(trashItem.pdfAttachments.isEmpty())
    }

    @Test
    fun getInbox_emits_initial_empty_and_live_updates_ordered_newest_first() = runTest {
        val emissions = Channel<List<Email>>(Channel.UNLIMITED)
        backgroundScope.launch(Dispatchers.IO) {
            repository.getInbox().collect(emissions::send)
        }

        assertEquals(emptyList<Email>(), emissions.awaitValue { it.isEmpty() })

        val older = testEmail("older", timestamp = 1_000L)
        val newer = testEmail("newer", timestamp = 2_000L)
        database.emailDao().upsertAll(
            listOf(
                EmailEntity.fromDomain(older, EmailFolder.Inbox),
                EmailEntity.fromDomain(newer, EmailFolder.Inbox)
            )
        )

        val inserted = emissions.awaitValue { it.map(Email::id) == listOf("newer", "older") }
        assertFalse(inserted.first().isRead)

        database.emailDao().updateReadStatus("newer", isRead = true)
        val updated = emissions.awaitValue { emails ->
            emails.firstOrNull { it.id == "newer" }?.isRead == true
        }

        assertEquals(listOf("newer", "older"), updated.map(Email::id))
        assertNoProviderAccess()
    }

    @Test
    fun getTrash_emits_initial_empty_and_live_insert_delete_updates() = runTest {
        val emissions = Channel<List<Email>>(Channel.UNLIMITED)
        backgroundScope.launch(Dispatchers.IO) {
            repository.getTrash().collect(emissions::send)
        }

        assertEquals(emptyList<Email>(), emissions.awaitValue { it.isEmpty() })

        database.emailDao().upsertAll(
            listOf(
                EmailEntity.fromDomain(
                    testEmail("trash-old", folder = EmailFolder.Trash, timestamp = 1_000L),
                    EmailFolder.Trash
                ),
                EmailEntity.fromDomain(
                    testEmail("trash-new", folder = EmailFolder.Trash, timestamp = 2_000L),
                    EmailFolder.Trash
                )
            )
        )

        assertEquals(
            listOf("trash-new", "trash-old"),
            emissions.awaitValue { it.size == 2 }.map(Email::id)
        )

        database.emailDao().deleteById("trash-new")

        assertEquals(
            listOf("trash-old"),
            emissions.awaitValue { it.map(Email::id) == listOf("trash-old") }.map(Email::id)
        )
        assertNoProviderAccess()
    }

    @Test
    fun folder_flows_isolate_inbox_trash_and_other() = runTest {
        database.emailDao().upsertAll(
            listOf(
                EmailEntity.fromDomain(testEmail("inbox"), EmailFolder.Inbox),
                EmailEntity.fromDomain(
                    testEmail("trash", folder = EmailFolder.Trash),
                    EmailFolder.Trash
                ),
                EmailEntity.fromDomain(
                    testEmail("other", folder = EmailFolder.Other),
                    EmailFolder.Other
                )
            )
        )

        assertEquals(listOf("inbox"), repository.getInbox().first().map(Email::id))
        assertEquals(listOf("trash"), repository.getTrash().first().map(Email::id))
        assertNoProviderAccess()
    }

    @Test
    fun getEmailById_emits_absent_insert_update_and_delete_sequence() = runTest {
        val emissions = Channel<Email?>(Channel.UNLIMITED)
        backgroundScope.launch(Dispatchers.IO) {
            repository.getEmailById("target").collect(emissions::send)
        }

        assertNull(emissions.awaitValue { it == null })

        val inserted = testEmail("target", subject = "Initial", isRead = false)
        database.emailDao().upsertAll(
            listOf(EmailEntity.fromDomain(inserted, EmailFolder.Inbox))
        )
        assertEquals(
            "Initial",
            emissions.awaitValue { it?.subject == "Initial" }?.subject
        )

        val updated = inserted.copy(subject = "Updated", isRead = true)
        database.emailDao().upsertAll(
            listOf(EmailEntity.fromDomain(updated, EmailFolder.Inbox))
        )
        val updatedEmission = emissions.awaitValue { it?.subject == "Updated" }
        assertEquals(true, updatedEmission?.isRead)

        database.emailDao().deleteById("target")
        assertNull(emissions.awaitValue { it == null })
        assertNoProviderAccess()
    }

    @Test
    fun getEmailById_maps_complete_rich_entity_to_domain_model() = runTest {
        val expected = testEmail(
            id = "rich",
            from = "Sender Name <sender@example.com>",
            subject = "Rich subject",
            snippet = "Rich snippet",
            isRead = true,
            timestamp = 9_876_543L
        ).copy(
            threadId = "thread-rich-custom",
            fromInitials = "SN",
            to = "recipient@example.com",
            isStarred = true,
            hasAttachments = true,
            labels = listOf("INBOX", "STARRED", "IMPORTANT"),
            body = "<html><body>Raw</body></html>",
            cleanBody = "<html><body>Clean</body></html>",
            pdfAttachments = listOf(
                PdfAttachmentMetadata(
                    fileName = "contract.pdf",
                    mimeType = "application/pdf",
                    attachmentId = "attachment-token",
                    sizeBytes = 4_096L,
                    partId = "2.1"
                )
            ),
            pdfMetadataScanned = true,
            rfcMessageId = "<message@example.com>",
            rfcReferences = "<parent@example.com> <root@example.com>"
        )
        database.emailDao().upsertAll(
            listOf(EmailEntity.fromDomain(expected, EmailFolder.Inbox))
        )

        assertEquals(expected, repository.getEmailById("rich").first())
        assertNoProviderAccess()
    }

    @Test
    fun refreshInbox_first_page_preserves_heavy_downloaded_data() = runTest {
        // 1. Insert a locally downloaded heavy email
        val heavyId = "preservation-1"
        val originalHeavy = com.david.mailapp.testhelpers.testHeavyEmail(heavyId)
        database.emailDao().upsertAll(
            listOf(EmailEntity.fromDomain(originalHeavy, EmailFolder.Inbox))
        )

        // 2. Mock a refresh from the provider that returns a lightweight version of the same email
        val lightweightFromProvider = testEmail(heavyId)
        provider.fetchInboxResult = PaginatedResult(listOf(lightweightFromProvider), null)

        // 3. Perform a full refresh (page 0 -> replaceFolder)
        repository.refreshInbox(null)

        // 4. Verify that the local database retained the heavy fields via merge
        val entityInDb = database.emailDao().getEntitiesByFolderSync("inbox").first { it.id == heavyId }
        assertEquals(originalHeavy.body, entityInDb.body)
        assertEquals(originalHeavy.cleanBody, entityInDb.cleanBody)
        assertEquals(true, entityInDb.pdfMetadataScanned)
        assertEquals(originalHeavy.rfcMessageId, entityInDb.rfcMessageId)
    }

    @Test
    fun refreshInbox_pagination_preserves_heavy_downloaded_data() = runTest {
        // 1. Insert a locally downloaded heavy email
        val heavyId = "preservation-page2"
        val originalHeavy = com.david.mailapp.testhelpers.testHeavyEmail(heavyId)
        database.emailDao().upsertAll(
            listOf(EmailEntity.fromDomain(originalHeavy, EmailFolder.Inbox))
        )

        // 2. Mock a paginated refresh from the provider that returns a lightweight version
        val lightweightFromProvider = testEmail(heavyId)
        provider.fetchInboxResult = PaginatedResult(listOf(lightweightFromProvider), "page3")

        // 3. Perform a paginated refresh (page > 0 -> upsertPreservingCachedContent)
        repository.refreshInbox("page2")

        // 4. Verify that the local database retained the heavy fields via merge
        val entityInDb = database.emailDao().getEntitiesByFolderSync("inbox").first { it.id == heavyId }
        assertEquals(originalHeavy.body, entityInDb.body)
        assertEquals(originalHeavy.cleanBody, entityInDb.cleanBody)
        assertEquals(true, entityInDb.pdfMetadataScanned)
        assertEquals(originalHeavy.rfcMessageId, entityInDb.rfcMessageId)
    }

    @Test
    fun refresh_without_lease_returns_empty_without_provider_or_room_changes() = runTest {
        seedBaselineFolders()
        val inactiveGuard = FakeSessionWriteGuard().apply { captureResult = null }
        var localProviderFactoryReads = 0
        val guardedRepository = EmailRepository(
            database = database,
            providerFactory = {
                localProviderFactoryReads++
                provider
            },
            pdfCacheManager = PdfCacheManager(cacheDir),
            writeGuard = inactiveGuard
        )

        assertEmptyPage(guardedRepository.refreshInbox(null))
        assertEmptyPage(guardedRepository.refreshTrash(null))

        assertEquals("Provider must not be resolved without a lease", 0, localProviderFactoryReads)
        assertEquals(0, provider.fetchInboxCalls)
        assertEquals(0, provider.fetchTrashCalls)
        assertBaselineFoldersUnchanged()
    }

    @Test
    fun refresh_without_provider_returns_empty_without_room_changes() = runTest {
        seedBaselineFolders()
        val repositoryWithoutProvider = EmailRepository(
            database = database,
            providerFactory = { null },
            pdfCacheManager = PdfCacheManager(cacheDir),
            writeGuard = FakeSessionWriteGuard()
        )

        assertEmptyPage(repositoryWithoutProvider.refreshInbox(null))
        assertEmptyPage(repositoryWithoutProvider.refreshTrash(null))

        assertEquals(0, provider.fetchInboxCalls)
        assertEquals(0, provider.fetchTrashCalls)
        assertBaselineFoldersUnchanged()
    }

    @Test
    fun refresh_delegates_page_tokens_and_returns_complete_remote_results() = runTest {
        seedBaselineFolders()
        val inboxRemote = PaginatedResult(
            items = listOf(testEmail("inbox-page-2")),
            nextPageToken = "inbox-page-3",
            isComplete = true
        )
        val trashRemote = PaginatedResult(
            items = listOf(testEmail("trash-page-2", folder = EmailFolder.Trash)),
            nextPageToken = "trash-page-3",
            isComplete = true
        )
        provider.fetchInboxResult = inboxRemote
        provider.fetchTrashResult = trashRemote

        assertEquals(inboxRemote, repository.refreshInbox("inbox-page-2-token"))
        assertEquals(trashRemote, repository.refreshTrash("trash-page-2-token"))

        assertEquals(listOf("inbox-page-2-token"), provider.receivedInboxTokens)
        assertEquals(listOf("trash-page-2-token"), provider.receivedTrashTokens)
        assertEquals(setOf("old-inbox", "inbox-page-2"), folderIds("inbox").toSet())
        assertEquals(setOf("old-trash", "trash-page-2"), folderIds("trash").toSet())
    }

    @Test
    fun refresh_provider_errors_propagate_same_instance_and_leave_room_unchanged() = runTest {
        seedBaselineFolders()
        val inboxError = IOException("inbox unavailable")
        provider.fetchInboxError = inboxError

        val thrownInbox = runCatching { repository.refreshInbox(null) }.exceptionOrNull()
        assertSame(inboxError, thrownInbox)

        provider.fetchInboxError = null
        val trashError = IOException("trash unavailable")
        provider.fetchTrashError = trashError

        val thrownTrash = runCatching { repository.refreshTrash(null) }.exceptionOrNull()
        assertSame(trashError, thrownTrash)
        assertBaselineFoldersUnchanged()
    }

    @Test
    fun refresh_cancellation_propagates_same_instance_and_leave_room_unchanged() = runTest {
        seedBaselineFolders()
        val inboxCancellation = CancellationException("cancel inbox refresh")
        provider.fetchInboxError = inboxCancellation

        val thrownInbox = try {
            repository.refreshInbox(null)
            null
        } catch (cancelled: CancellationException) {
            cancelled
        }
        assertSame(inboxCancellation, thrownInbox)

        provider.fetchInboxError = null
        val trashCancellation = CancellationException("cancel trash refresh")
        provider.fetchTrashError = trashCancellation

        val thrownTrash = try {
            repository.refreshTrash(null)
            null
        } catch (cancelled: CancellationException) {
            cancelled
        }
        assertSame(trashCancellation, thrownTrash)
        assertBaselineFoldersUnchanged()
    }

    @Test
    fun refresh_rejected_commit_returns_remote_result_but_leaves_room_unchanged() = runTest {
        seedBaselineFolders()
        val rejectingGuard = FakeSessionWriteGuard().apply { commitReturnsNull = true }
        val guardedRepository = EmailRepository(
            database = database,
            providerFactory = { provider },
            pdfCacheManager = PdfCacheManager(cacheDir),
            writeGuard = rejectingGuard
        )
        val inboxRemote = PaginatedResult(
            listOf(testEmail("rejected-inbox")),
            "inbox-next",
            isComplete = true
        )
        val trashRemote = PaginatedResult(
            listOf(testEmail("rejected-trash", folder = EmailFolder.Trash)),
            "trash-next",
            isComplete = true
        )
        provider.fetchInboxResult = inboxRemote
        provider.fetchTrashResult = trashRemote

        assertEquals(inboxRemote, guardedRepository.refreshInbox(null))
        assertEquals(trashRemote, guardedRepository.refreshTrash(null))
        assertBaselineFoldersUnchanged()
    }

    @Test
    fun refresh_local_commit_errors_propagate_same_instance_and_leave_room_unchanged() = runTest {
        seedBaselineFolders()
        val commitError = IllegalStateException("Room commit failed")
        val failingGuard = FakeSessionWriteGuard().apply { this.commitError = commitError }
        val guardedRepository = EmailRepository(
            database = database,
            providerFactory = { provider },
            pdfCacheManager = PdfCacheManager(cacheDir),
            writeGuard = failingGuard
        )
        provider.fetchInboxResult = PaginatedResult(
            listOf(testEmail("failed-inbox")),
            null,
            isComplete = true
        )
        provider.fetchTrashResult = PaginatedResult(
            listOf(testEmail("failed-trash", folder = EmailFolder.Trash)),
            null,
            isComplete = true
        )

        val thrownInbox = runCatching { guardedRepository.refreshInbox(null) }.exceptionOrNull()
        assertSame(commitError, thrownInbox)

        val thrownTrash = runCatching { guardedRepository.refreshTrash(null) }.exceptionOrNull()
        assertSame(commitError, thrownTrash)
        assertBaselineFoldersUnchanged()
    }

    @Test
    fun search_without_provider_returns_empty_without_room_changes() = runTest {
        seedBaselineFolders()
        val repositoryWithoutProvider = EmailRepository(
            database = database,
            providerFactory = { null },
            pdfCacheManager = PdfCacheManager(cacheDir),
            writeGuard = FakeSessionWriteGuard()
        )

        assertEmptyPage(repositoryWithoutProvider.searchEmails("missing provider", "page-2"))

        assertEquals(0, provider.searchCalls)
        assertBaselineFoldersUnchanged()
    }

    @Test
    fun search_delegates_query_and_token_and_returns_remote_result_without_room_changes() = runTest {
        seedBaselineFolders()
        val remoteResult = PaginatedResult(
            items = listOf(testEmail("remote-search-result")),
            nextPageToken = "page-3",
            isComplete = false,
            failures = listOf(
                PageItemFailure(
                    itemId = "failed-result",
                    kind = PageItemFailureKind.TRANSIENT_EXHAUSTED,
                    attempts = 3
                )
            )
        )
        provider.searchResult = remoteResult

        val actual = repository.searchEmails("from:sender exact words", "page-2")

        assertEquals(remoteResult, actual)
        assertEquals(listOf("from:sender exact words" to "page-2"), provider.receivedSearchRequests)
        assertEquals(1, provider.searchCalls)
        assertEquals(1, providerFactoryReads)
        assertBaselineFoldersUnchanged()
    }

    @Test
    fun search_resolves_current_provider_for_each_call() = runTest {
        val firstProvider = FakeEmailProvider().apply {
            searchResult = PaginatedResult(listOf(testEmail("first-account")), null)
        }
        val secondProvider = FakeEmailProvider().apply {
            searchResult = PaginatedResult(listOf(testEmail("second-account")), null)
        }
        var currentProvider: FakeEmailProvider? = firstProvider
        val dynamicRepository = EmailRepository(
            database = database,
            providerFactory = { currentProvider },
            pdfCacheManager = PdfCacheManager(cacheDir),
            writeGuard = FakeSessionWriteGuard()
        )

        assertEquals("first-account", dynamicRepository.searchEmails("first").items.single().id)
        currentProvider = secondProvider
        assertEquals("second-account", dynamicRepository.searchEmails("second").items.single().id)

        assertEquals(listOf("first" to null), firstProvider.receivedSearchRequests)
        assertEquals(listOf("second" to null), secondProvider.receivedSearchRequests)
    }

    @Test
    fun search_provider_errors_propagate_same_instance_and_leave_room_unchanged() = runTest {
        seedBaselineFolders()
        val providerError = IOException("search unavailable")
        provider.searchError = providerError

        val thrown = runCatching {
            repository.searchEmails("error query", "error-page")
        }.exceptionOrNull()

        assertSame(providerError, thrown)
        assertEquals(listOf("error query" to "error-page"), provider.receivedSearchRequests)
        assertBaselineFoldersUnchanged()
    }

    @Test
    fun search_cancellation_propagates_same_instance_and_leave_room_unchanged() = runTest {
        seedBaselineFolders()
        val cancellation = CancellationException("cancel remote search")
        provider.searchError = cancellation

        val thrown = try {
            repository.searchEmails("cancel query", null)
            null
        } catch (cancelled: CancellationException) {
            cancelled
        }

        assertSame(cancellation, thrown)
        assertEquals(listOf("cancel query" to null), provider.receivedSearchRequests)
        assertBaselineFoldersUnchanged()
    }

    @Test
    fun concurrent_first_page_refreshes_commit_inbox_and_trash_independently_three_times() =
        runTest {
            repeat(3) { iteration ->
                replaceBaselineFolders(iteration)
                val inboxGate = CompletableDeferred<Unit>()
                val trashGate = CompletableDeferred<Unit>()
                val inboxPlan = provider.enqueueInbox(
                    result = PaginatedResult(listOf(testEmail("inbox-new-$iteration")), null),
                    gate = inboxGate
                )
                val trashPlan = provider.enqueueTrash(
                    result = PaginatedResult(
                        listOf(testEmail("trash-new-$iteration", folder = EmailFolder.Trash)),
                        null
                    ),
                    gate = trashGate
                )

                val inboxJob = launch { repository.refreshInbox(null) }
                val trashJob = launch { repository.refreshTrash(null) }
                inboxPlan.started.await()
                trashPlan.started.await()

                trashGate.complete(Unit)
                trashJob.join()
                assertFalse("Inbox remains blocked independently", inboxJob.isCompleted)
                assertEquals(listOf("trash-new-$iteration"), folderIds("trash"))

                inboxGate.complete(Unit)
                inboxJob.join()
                assertEquals(listOf("inbox-new-$iteration"), folderIds("inbox"))
                assertEquals(listOf("trash-new-$iteration"), folderIds("trash"))
            }
        }

    @Test
    fun trash_first_page_does_not_invalidate_inbox_pagination_three_times() = runTest {
        repeat(3) { iteration ->
            replaceBaselineFolders(iteration)
            val inboxGate = CompletableDeferred<Unit>()
            val inboxPlan = provider.enqueueInbox(
                result = PaginatedResult(listOf(testEmail("inbox-page-$iteration")), null),
                gate = inboxGate
            )

            val inboxJob = launch { repository.refreshInbox("inbox-page-token-$iteration") }
            inboxPlan.started.await()

            provider.fetchTrashResult = PaginatedResult(
                listOf(testEmail("trash-refresh-$iteration", folder = EmailFolder.Trash)),
                null
            )
            repository.refreshTrash(null)

            inboxGate.complete(Unit)
            inboxJob.join()

            assertEquals(
                setOf("inbox-old-$iteration", "inbox-page-$iteration"),
                folderIds("inbox").toSet()
            )
            assertEquals(listOf("trash-refresh-$iteration"), folderIds("trash"))
        }
    }

    @Test
    fun inbox_first_page_does_not_invalidate_trash_pagination_three_times() = runTest {
        repeat(3) { iteration ->
            replaceBaselineFolders(iteration)
            val trashGate = CompletableDeferred<Unit>()
            val trashPlan = provider.enqueueTrash(
                result = PaginatedResult(
                    listOf(testEmail("trash-page-$iteration", folder = EmailFolder.Trash)),
                    null
                ),
                gate = trashGate
            )

            val trashJob = launch { repository.refreshTrash("trash-page-token-$iteration") }
            trashPlan.started.await()

            provider.fetchInboxResult = PaginatedResult(
                listOf(testEmail("inbox-refresh-$iteration")),
                null
            )
            repository.refreshInbox(null)

            trashGate.complete(Unit)
            trashJob.join()

            assertEquals(listOf("inbox-refresh-$iteration"), folderIds("inbox"))
            assertEquals(
                setOf("trash-old-$iteration", "trash-page-$iteration"),
                folderIds("trash").toSet()
            )
        }
    }

    private suspend fun seedBaselineFolders() {
        database.emailDao().upsertAll(
            listOf(
                EmailEntity.fromDomain(testEmail("old-inbox"), EmailFolder.Inbox),
                EmailEntity.fromDomain(
                    testEmail("old-trash", folder = EmailFolder.Trash),
                    EmailFolder.Trash
                ),
                EmailEntity.fromDomain(
                    testEmail("old-other", folder = EmailFolder.Other),
                    EmailFolder.Other
                )
            )
        )
    }

    private suspend fun replaceBaselineFolders(iteration: Int) {
        database.emailDao().replaceFolder(
            "inbox",
            listOf(
                EmailEntity.fromDomain(testEmail("inbox-old-$iteration"), EmailFolder.Inbox)
            )
        )
        database.emailDao().replaceFolder(
            "trash",
            listOf(
                EmailEntity.fromDomain(
                    testEmail("trash-old-$iteration", folder = EmailFolder.Trash),
                    EmailFolder.Trash
                )
            )
        )
    }

    private suspend fun assertBaselineFoldersUnchanged() {
        assertEquals(listOf("old-inbox"), folderIds("inbox"))
        assertEquals(listOf("old-trash"), folderIds("trash"))
        assertEquals(listOf("old-other"), folderIds("other"))
    }

    private suspend fun folderIds(folder: String): List<String> =
        database.emailDao().getEntitiesByFolderSync(folder).map(EmailEntity::id)

    private fun assertEmptyPage(result: PaginatedResult<Email>) {
        assertEquals(emptyList<Email>(), result.items)
        assertNull(result.nextPageToken)
        assertEquals(true, result.isComplete)
        assertEquals(emptyList<Any>(), result.failures)
    }

    private fun assertNoProviderAccess() {
        assertEquals("Read APIs must not resolve the provider", 0, providerFactoryReads)
        assertEquals(0, provider.fetchInboxCalls)
        assertEquals(0, provider.fetchTrashCalls)
        assertEquals(0, provider.searchCalls)
        assertEquals(0, provider.fetchEmailByIdCalls)
        assertEquals(0, provider.fetchBodyCalls)
        assertEquals(0, provider.inlineImagesCalls)
        assertEquals(0, provider.downloadAttachmentCalls)
        assertEquals(0, provider.moveToTrashCalls)
        assertEquals(0, provider.restoreFromTrashCalls)
        assertEquals(0, provider.deletePermanentlyCalls)
        assertEquals(0, provider.markAsReadCalls)
        assertEquals(0, provider.sendEmailCalls)
    }

    private suspend fun <T> Channel<T>.awaitValue(predicate: (T) -> Boolean): T =
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(5_000L) {
                while (true) {
                    val value = receive()
                    if (predicate(value)) return@withTimeout value
                }
                error("Unreachable")
            }
        }
}
