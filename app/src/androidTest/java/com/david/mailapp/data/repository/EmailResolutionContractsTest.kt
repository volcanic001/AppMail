package com.david.mailapp.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.david.mailapp.core.network.OAuthSessionExpiredException
import com.david.mailapp.core.session.SessionWriteGuardImpl
import com.david.mailapp.data.local.MailDatabase
import com.david.mailapp.data.local.entity.EmailEntity
import com.david.mailapp.data.pdf.PdfCacheManager
import com.david.mailapp.data.remote.provider.EmailLookupFailureReason
import com.david.mailapp.data.remote.provider.EmailLookupResult
import com.david.mailapp.domain.model.EmailFolder
import com.david.mailapp.domain.model.PaginatedResult
import com.david.mailapp.domain.model.PdfAttachmentMetadata
import com.david.mailapp.testhelpers.FakeEmailProvider
import com.david.mailapp.testhelpers.FakeSessionWriteGuard
import com.david.mailapp.testhelpers.testEmail
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class EmailResolutionContractsTest {

    private lateinit var db: MailDatabase
    private lateinit var provider: FakeEmailProvider
    private lateinit var writeGuard: FakeSessionWriteGuard
    private lateinit var repository: EmailRepository
    private lateinit var context: android.content.Context
    private lateinit var events: MutableList<String>

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, MailDatabase::class.java).build()
        provider = FakeEmailProvider()
        writeGuard = FakeSessionWriteGuard()
        events = mutableListOf()
        provider.eventLog = events
        writeGuard.eventLog = events
        val cacheDir = java.io.File(context.cacheDir, "res_test_${System.nanoTime()}").apply { mkdirs() }
        repository = EmailRepository(
            database = db, providerFactory = { provider },
            pdfCacheManager = PdfCacheManager(cacheDir), writeGuard = writeGuard
        )
    }

    @After fun tearDown() { db.close() }

    // ── helpers ────────────────────────────────────────────────

    private suspend fun seed(id: String, folder: EmailFolder = EmailFolder.Inbox) {
        db.emailDao().upsertAll(
            listOf(EmailEntity.fromDomain(testEmail(id = id, folder = folder), folder))
        )
    }

    private suspend fun seedRich(
        id: String, folder: EmailFolder = EmailFolder.Inbox,
        body: String = "", cleanBody: String = "",
        rfcMessageId: String? = null, rfcReferences: String? = null,
        pdfs: List<PdfAttachmentMetadata> = emptyList()
    ) {
        val email = testEmail(id = id, folder = folder).copy(
            body = body, cleanBody = cleanBody,
            rfcMessageId = rfcMessageId, rfcReferences = rfcReferences,
            pdfAttachments = pdfs, pdfMetadataScanned = pdfs.isNotEmpty()
        )
        db.emailDao().upsertAll(listOf(EmailEntity.fromDomain(email, folder)))
    }

    private suspend fun get(id: String) = db.emailDao().getEntitiesByIdsSync(listOf(id)).firstOrNull()

    private fun EmailResolutionResult.failureReason() =
        (this as EmailResolutionResult.Failure).reason

    // ═══════════════════════════════════════════════════════════════
    // Resolution and errors
    // ═══════════════════════════════════════════════════════════════

    @Test fun blankId_returns_invalidId_without_db_or_network_access() = runTest {
        for (blank in listOf("", "   ")) {
            val result = repository.resolveEmailById(blank)
            assertEquals(EmailResolutionFailureReason.INVALID_ID, result.failureReason())
        }
        assertEquals("no provider access", 0, events.count { it.contains("fetchEmailById") })
    }

    @Test fun cacheHit_returns_found_without_remote_call() = runTest {
        seed("e1", EmailFolder.Inbox)
        val result = repository.resolveEmailById("e1")

        assertTrue(result is EmailResolutionResult.Found)
        assertEquals("e1", (result as EmailResolutionResult.Found).email.id)
        assertEquals("zero remote calls", 0, provider.fetchEmailByIdCalls)
    }

    @Test fun cacheMiss_remote_found_persists_and_returns_email() = runTest {
        provider.fetchEmailByIdResult = EmailLookupResult.Found(
            testEmail("e2", folder = EmailFolder.Inbox)
        )
        val result = repository.resolveEmailById("e2")

        assertTrue(result is EmailResolutionResult.Found)
        val entity = checkNotNull(get("e2"))
        assertEquals("Inbox persisted", "inbox", entity.folder)
        assertEquals(1, provider.fetchEmailByIdCalls)
    }

    @Test fun secondResolution_uses_cache_not_remote() = runTest {
        seed("e3", EmailFolder.Inbox)
        repository.resolveEmailById("e3")
        val result = repository.resolveEmailById("e3")

        assertTrue(result is EmailResolutionResult.Found)
        assertEquals("no provider calls", 0, provider.fetchEmailByIdCalls)
    }

    @Test fun remoteResolution_thenReopen_usesCacheWithoutSecondRemoteCall() = runTest {
        provider.fetchEmailByIdResult = EmailLookupResult.Found(
            testEmail("reopen", folder = EmailFolder.Other, subject = "Remote then cached")
        )

        val first = repository.resolveEmailById("reopen")
        val second = repository.resolveEmailById("reopen")

        assertTrue(first is EmailResolutionResult.Found)
        assertTrue(second is EmailResolutionResult.Found)
        assertEquals("Remote then cached", (second as EmailResolutionResult.Found).email.subject)
        assertEquals("only the first opening reaches Gmail", 1, provider.fetchEmailByIdCalls)
        assertEquals("other", checkNotNull(get("reopen")).folder)
    }

    @Test fun remote_notFound_returns_notFound_and_creates_no_rows() = runTest {
        provider.fetchEmailByIdResult = EmailLookupResult.NotFound
        val result = repository.resolveEmailById("ne")

        assertEquals(EmailResolutionResult.NotFound, result)
        assertNull("no row created", get("ne"))
    }

    @Test fun eachLookupFailureReason_maps_correctly() = runTest {
        val expected = mapOf(
            EmailLookupFailureReason.NO_CONNECTION to EmailResolutionFailureReason.NO_CONNECTION,
            EmailLookupFailureReason.SESSION_EXPIRED to EmailResolutionFailureReason.SESSION_EXPIRED,
            EmailLookupFailureReason.TEMPORARY_REMOTE to EmailResolutionFailureReason.TEMPORARY_REMOTE,
            EmailLookupFailureReason.REMOTE_REJECTED to EmailResolutionFailureReason.REMOTE_REJECTED,
            EmailLookupFailureReason.INVALID_RESPONSE to EmailResolutionFailureReason.INVALID_RESPONSE
        )
        for ((lookup, expectedResolution) in expected) {
            provider.fetchEmailByIdResult = EmailLookupResult.Failure(lookup)
            val result = repository.resolveEmailById("f1")

            assertEquals("$lookup → $expectedResolution", expectedResolution, result.failureReason())
            assertNull("no row for failure", get("f1"))
        }
    }

    @Test fun absentProvider_returns_noActiveAccount() = runTest {
        val tempDir = java.io.File(context.cacheDir, "pdf_resolve_np_${System.nanoTime()}").apply { mkdirs() }
        val noProviderRepo = EmailRepository(
            database = db, providerFactory = { null },
            pdfCacheManager = PdfCacheManager(tempDir), writeGuard = writeGuard
        )
        val result = noProviderRepo.resolveEmailById("x")
        assertEquals(EmailResolutionFailureReason.NO_ACTIVE_ACCOUNT, result.failureReason())
    }

    @Test fun inactiveGuard_returns_noActiveAccount() = runTest {
        writeGuard.captureResult = null
        val result = repository.resolveEmailById("x")
        assertEquals(EmailResolutionFailureReason.NO_ACTIVE_ACCOUNT, result.failureReason())
    }

    @Test fun defensiveExceptions_converted_per_contract() = runTest {
        provider.fetchEmailByIdError = OAuthSessionExpiredException("expired")
        assertEquals(EmailResolutionFailureReason.SESSION_EXPIRED,
            repository.resolveEmailById("x").failureReason())

        provider.fetchEmailByIdError = IOException("network")
        assertEquals(EmailResolutionFailureReason.NO_CONNECTION,
            repository.resolveEmailById("y").failureReason())

        provider.fetchEmailByIdError = RuntimeException("unknown")
        assertEquals(EmailResolutionFailureReason.INVALID_RESPONSE,
            repository.resolveEmailById("z").failureReason())
    }

    @Test fun localReadFailure_differentiated() = runTest {
        // Inject a Room-level error through the write guard's commit error
        writeGuard.commitError = RuntimeException("sqlite disk I/O error")
        val result = repository.resolveEmailById("lr")
        assertEquals(EmailResolutionFailureReason.LOCAL_READ_FAILED, result.failureReason())
    }

    @Test fun localWriteFailure_differentiated_from_other_failures() = runTest {
        val gate = CompletableDeferred<Unit>()
        provider.fetchEmailByIdResult = EmailLookupResult.Found(
            testEmail("we", folder = EmailFolder.Inbox)
        )
        provider.fetchEmailByIdDeferred = gate

        // Launch resolution — guarded read succeeds (no error yet), then blocks on provider
        val job = async { repository.resolveEmailById("we") }
        runCurrent()

        // Now inject the write error so only upsertWithMerge fails
        writeGuard.commitError = RuntimeException("disk full")
        gate.complete(Unit)
        val result = job.await()

        assertEquals(EmailResolutionFailureReason.LOCAL_WRITE_FAILED, result.failureReason())
        assertNull("no partial row", get("we"))
    }

    @Test fun cancellation_propagates_not_converted_to_failure() = runTest {
        val sentinel = CancellationException("sentinel-repo")
        provider.fetchEmailByIdError = sentinel
        try {
            repository.resolveEmailById("cx")
            fail("Expected CancellationException")
        } catch (e: CancellationException) {
            assertEquals("sentinel-repo", e.message?.substringAfterLast(": ")?.trim())
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Cache and folders
    // ═══════════════════════════════════════════════════════════════

    @Test fun other_folder_survives_entity_to_room_to_domain_cycle() = runTest {
        seedRich("oe", folder = EmailFolder.Other)
        val entity = checkNotNull(get("oe"))
        assertEquals("other", entity.folder)
        val domain = entity.toDomain()
        assertEquals(EmailFolder.Other, domain.folder)
    }

    @Test fun otherEmails_doNotAppear_in_inbox_or_trash_Flow() = runTest {
        seed("oi", EmailFolder.Inbox)
        seed("ot", EmailFolder.Trash)
        seed("oo", EmailFolder.Other)

        val inbox = db.emailDao().getEntitiesByFolderSync("inbox")
        assertEquals(listOf("oi"), inbox.map { it.id })

        val trash = db.emailDao().getEntitiesByFolderSync("trash")
        assertEquals(listOf("ot"), trash.map { it.id })
    }

    @Test fun refreshInbox_preserves_other_emails() = runTest {
        seed("oo", EmailFolder.Other)
        provider.fetchInboxResult = PaginatedResult(
            listOf(testEmail("new-inbox")), null, isComplete = true
        )
        repository.refreshInbox(null)

        val other = checkNotNull(get("oo"))
        assertEquals("other", other.folder)
    }

    @Test fun refreshTrash_preserves_other_emails() = runTest {
        seed("oo", EmailFolder.Other)
        provider.fetchTrashResult = PaginatedResult(
            listOf(testEmail("new-trash", folder = EmailFolder.Trash)), null, isComplete = true
        )
        repository.refreshTrash(null)

        val other = checkNotNull(get("oo"))
        assertEquals("other", other.folder)
    }

    @Test fun other_to_inbox_transition_preserves_rich_data() = runTest {
        seedRich("cross", folder = EmailFolder.Other,
            body = "<html>B</html>", cleanBody = "B",
            rfcMessageId = "<m@x.com>", rfcReferences = "<r@x.com>",
            pdfs = listOf(PdfAttachmentMetadata("f.pdf", "application/pdf", "att1", 1024L))
        )
        provider.fetchInboxResult = PaginatedResult(
            listOf(testEmail("cross")), "p2", isComplete = true
        )
        repository.refreshInbox(null)

        val saved = checkNotNull(get("cross"))
        assertEquals("inbox", saved.folder)
        assertEquals("<html>B</html>", saved.body)
        assertEquals("B", saved.cleanBody)
        assertEquals("<m@x.com>", saved.rfcMessageId)
        assertEquals("<r@x.com>", saved.rfcReferences)
        assertTrue(saved.pdfMetadataScanned)
        assertTrue(saved.pdfAttachmentsJson.contains("f.pdf"))
    }

    @Test fun other_to_trash_transition_preserves_rich_data() = runTest {
        seedRich("cross-t", folder = EmailFolder.Other,
            body = "<html>T</html>", cleanBody = "T",
            rfcMessageId = "<t@x.com>", rfcReferences = "<tr@x.com>",
            pdfs = listOf(PdfAttachmentMetadata("t.pdf", "application/pdf", "att2", 512L))
        )
        provider.fetchTrashResult = PaginatedResult(
            listOf(testEmail("cross-t", folder = EmailFolder.Trash)), "tp2", isComplete = true
        )
        repository.refreshTrash(null)

        val saved = checkNotNull(get("cross-t"))
        assertEquals("trash", saved.folder)
        assertEquals("<html>T</html>", saved.body)
        assertEquals("T", saved.cleanBody)
        assertEquals("<t@x.com>", saved.rfcMessageId)
        assertEquals("<tr@x.com>", saved.rfcReferences)
        assertTrue(saved.pdfMetadataScanned)
        assertTrue(saved.pdfAttachmentsJson.contains("t.pdf"))
    }

    @Test fun lessComplete_remote_does_not_degrade_existing_rich_data() = runTest {
        // 1. Block the remote fetch with a gate
        val gate = CompletableDeferred<Unit>()
        provider.fetchEmailByIdResult = EmailLookupResult.Found(
            testEmail("rich", folder = EmailFolder.Other).copy(
                body = "", cleanBody = "", pdfAttachments = emptyList(), pdfMetadataScanned = false
            )
        )
        provider.fetchEmailByIdDeferred = gate

        // 2. Start resolution — it will block on the gate
        val job = async { repository.resolveEmailById("rich") }
        runCurrent()

        // 3. While the remote fetch is blocked, insert rich data directly
        seedRich("rich", folder = EmailFolder.Other,
            body = "<html>Pre-existing</html>", cleanBody = "Pre-existing",
            rfcMessageId = "<rich@x.com>", rfcReferences = "<rich-ref@x.com>",
            pdfs = listOf(PdfAttachmentMetadata("r.pdf", "application/pdf", "att-rich", 2048L))
        )

        // 4. Release the remote response (less complete: no body/PDF)
        gate.complete(Unit)
        val result = job.await()
        assertTrue(result is EmailResolutionResult.Found)

        // 5. Verify merged data preserves rich pre-existing content
        val cached = repository.resolveEmailById("rich")
        val email = (cached as EmailResolutionResult.Found).email
        assertEquals("<html>Pre-existing</html>", email.body)
        assertEquals("Pre-existing", email.cleanBody)
        assertEquals("<rich@x.com>", email.rfcMessageId)
        assertEquals("<rich-ref@x.com>", email.rfcReferences)
        assertTrue(email.pdfMetadataScanned)
        assertEquals(1, email.pdfAttachments.size)
        assertEquals("r.pdf", email.pdfAttachments[0].fileName)
    }

    // ═══════════════════════════════════════════════════════════════
    // Session and concurrency
    // ═══════════════════════════════════════════════════════════════

    @Test fun invalidateSession_during_request_prevents_write() = runTest {
        val gate = CompletableDeferred<Unit>()
        provider.fetchEmailByIdResult = EmailLookupResult.Found(
            testEmail("sess", folder = EmailFolder.Inbox)
        )
        provider.fetchEmailByIdDeferred = gate

        val job = async { repository.resolveEmailById("sess") }
        runCurrent()
        writeGuard.invalidate()
        writeGuard.commitReturnsNull = true
        gate.complete(Unit)

        val result = job.await()
        assertEquals(EmailResolutionFailureReason.SESSION_CHANGED, result.failureReason())
        assertNull("no row written", get("sess"))
    }

    @Test fun singleFlight_sameIdSingleRemoteCall_andBothReceiveSameResult() = runTest {
        val gate = CompletableDeferred<Unit>()
        provider.fetchEmailByIdResult = EmailLookupResult.Found(
            testEmail("sf", folder = EmailFolder.Other, subject = "shared")
        )
        provider.fetchEmailByIdDeferred = gate

        val r1 = async { repository.resolveEmailById("sf") }
        runCurrent()
        val r2 = async { repository.resolveEmailById("sf") }
        runCurrent()
        gate.complete(Unit)
        val result1 = r1.await()
        val result2 = r2.await()

        assertEquals("exactly one remote call", 1, provider.fetchEmailByIdCalls)
        assertTrue(result1 is EmailResolutionResult.Found)
        assertTrue(result2 is EmailResolutionResult.Found)
        assertEquals("same subject", "shared", (result1 as EmailResolutionResult.Found).email.subject)
        assertEquals("same subject", "shared", (result2 as EmailResolutionResult.Found).email.subject)
    }

    @Test fun differentIds_bothReachProvider_beforeGatesReleased() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val roomDb = Room.inMemoryDatabaseBuilder(context, MailDatabase::class.java)
            .setQueryExecutor(dispatcher.asExecutor())
            .setTransactionExecutor(dispatcher.asExecutor())
            .build()
        // Reset provider tracking (shared provider; assertions target this test only)
        provider.fetchEmailByIdCalls = 0
        provider.receivedFetchEmailByIdIds.clear()

        val localRepo = EmailRepository(
            database = roomDb, providerFactory = { provider },
            pdfCacheManager = PdfCacheManager(
                java.io.File(context.cacheDir, "pdf_diffids_${System.nanoTime()}").apply { mkdirs() }
            ),
            writeGuard = writeGuard
        )

        val gate = CompletableDeferred<Unit>()
        provider.fetchEmailByIdDeferredByCall = listOf(gate, gate)
        provider.fetchEmailByIdResultsByCall = listOf(
            EmailLookupResult.Found(testEmail("da", folder = EmailFolder.Other, subject = "A")),
            EmailLookupResult.Found(testEmail("db", folder = EmailFolder.Other, subject = "B"))
        )

        val r1 = async { localRepo.resolveEmailById("da") }
        val r2 = async { localRepo.resolveEmailById("db") }
        advanceUntilIdle()

        assertEquals("both IDs reached provider before gate release", 2, provider.fetchEmailByIdCalls)
        assertEquals("IDs tracked per call", listOf("da", "db"), provider.receivedFetchEmailByIdIds)

        gate.complete(Unit)
        val result1 = r1.await()
        val result2 = r2.await()

        assertTrue(result1 is EmailResolutionResult.Found)
        assertTrue(result2 is EmailResolutionResult.Found)
        assertEquals("da gets A", "A", (result1 as EmailResolutionResult.Found).email.subject)
        assertEquals("db gets B", "B", (result2 as EmailResolutionResult.Found).email.subject)
        assertEquals("total calls remains 2", 2, provider.fetchEmailByIdCalls)

        roomDb.close()
    }

    @Test fun cancelFollower_doesNotCancelLeader() = runTest {
        val gate = CompletableDeferred<Unit>()
        provider.fetchEmailByIdResult = EmailLookupResult.Found(testEmail("cf", folder = EmailFolder.Other))
        provider.fetchEmailByIdDeferred = gate

        val leader = async { repository.resolveEmailById("cf") }
        runCurrent()
        val follower = async { repository.resolveEmailById("cf") }
        runCurrent()

        follower.cancel()
        gate.complete(Unit)

        val leaderResult = leader.await()
        assertTrue("leader completed despite follower cancellation",
            leaderResult is EmailResolutionResult.Found)
    }

    @Test fun cancelLeader_cleansUp_singleFlight_andAllowsRetry() = runTest {
        val gate = CompletableDeferred<Unit>()
        provider.fetchEmailByIdResult = EmailLookupResult.Found(testEmail("cl", folder = EmailFolder.Other))
        provider.fetchEmailByIdDeferred = gate

        val leader = async { repository.resolveEmailById("cl") }
        runCurrent()

        leader.cancel()
        try {
            leader.await()
            fail("Expected CancellationException")
        } catch (_: CancellationException) {
            // Leader cancelled, single-flight entry cleaned up
        }

        // Retry from a clean state — must succeed
        provider.fetchEmailByIdDeferred = null
        val retry = repository.resolveEmailById("cl")
        assertTrue("retry after leader cancel succeeds", retry is EmailResolutionResult.Found)
    }

    @Test fun generationChange_newSession_doesNotJoin_orReceiveOldPendingResolution() = runTest {
        // Use a real SessionWriteGuard that validates generation on commit
        val realGuard = SessionWriteGuardImpl()
        realGuard.activate() // gen = 1

        val guardRepo = EmailRepository(
            database = db, providerFactory = { provider },
            pdfCacheManager = PdfCacheManager(
                java.io.File(context.cacheDir, "pdf_gen_test_${System.nanoTime()}").apply { mkdirs() }
            ),
            writeGuard = realGuard
        )

        val gate = CompletableDeferred<Unit>()
        provider.fetchEmailByIdDeferredByCall = listOf(gate, gate)
        provider.fetchEmailByIdResultsByCall = listOf(
            EmailLookupResult.Found(testEmail("gen", folder = EmailFolder.Other, subject = "old-session")),
            EmailLookupResult.Found(testEmail("gen", folder = EmailFolder.Other, subject = "new-session"))
        )

        // Start old-generation resolution
        val oldJob = async { guardRepo.resolveEmailById("gen") }
        advanceUntilIdle()

        // Change generation mid-flight: invalidate old, activate new
        realGuard.invalidate()
        realGuard.activate() // gen = 2

        // Start new-generation resolution — different flight key (gen=2 vs gen=1)
        val newJob = async { guardRepo.resolveEmailById("gen") }
        advanceUntilIdle()

        // Release both provider calls
        gate.complete(Unit)
        val oldResult = oldJob.await()
        val newResult = newJob.await()

        // Old-generation commit was rejected because gen changed to 2
        assertEquals(EmailResolutionFailureReason.SESSION_CHANGED, oldResult.failureReason())

        // New-generation commit succeeded
        assertTrue(newResult is EmailResolutionResult.Found)
        val email = (newResult as EmailResolutionResult.Found).email
        assertEquals("new-session", email.subject)

        // Room contains only the new-session email
        val entity = checkNotNull(db.emailDao().getEntitiesByIdsSync(listOf("gen")).firstOrNull())
        assertEquals("new-session", entity.toDomain().subject)

        // Both made independent remote calls (different flights)
        assertEquals("two remote calls across generations", 2, provider.fetchEmailByIdCalls)
    }

    @Test fun provider_is_resolved_fresh_for_each_cache_miss() = runTest {
        val firstProvider = FakeEmailProvider().apply {
            fetchEmailByIdResult = EmailLookupResult.Found(
                testEmail("fresh-a", folder = EmailFolder.Other, subject = "first-provider")
            )
        }
        val secondProvider = FakeEmailProvider().apply {
            fetchEmailByIdResult = EmailLookupResult.Found(
                testEmail("fresh-b", folder = EmailFolder.Other, subject = "second-provider")
            )
        }
        var currentProvider: FakeEmailProvider? = firstProvider
        val dynamicRepository = EmailRepository(
            database = db,
            providerFactory = { currentProvider },
            pdfCacheManager = PdfCacheManager(
                java.io.File(context.cacheDir, "pdf_fresh_${System.nanoTime()}").apply { mkdirs() }
            ),
            writeGuard = writeGuard
        )

        val first = dynamicRepository.resolveEmailById("fresh-a")
        currentProvider = secondProvider
        val second = dynamicRepository.resolveEmailById("fresh-b")

        assertEquals("first-provider", (first as EmailResolutionResult.Found).email.subject)
        assertEquals("second-provider", (second as EmailResolutionResult.Found).email.subject)
        assertEquals(listOf("fresh-a"), firstProvider.receivedFetchEmailByIdIds)
        assertEquals(listOf("fresh-b"), secondProvider.receivedFetchEmailByIdIds)
    }

    @Test fun non_cacheable_terminal_flights_are_removed_and_retried() = runTest {
        provider.fetchEmailByIdResultsByCall = listOf(
            EmailLookupResult.NotFound,
            EmailLookupResult.NotFound,
            EmailLookupResult.Failure(EmailLookupFailureReason.NO_CONNECTION),
            EmailLookupResult.Failure(EmailLookupFailureReason.NO_CONNECTION)
        )

        assertEquals(EmailResolutionResult.NotFound, repository.resolveEmailById("retry-not-found"))
        assertEquals(EmailResolutionResult.NotFound, repository.resolveEmailById("retry-not-found"))
        assertEquals(
            EmailResolutionFailureReason.NO_CONNECTION,
            repository.resolveEmailById("retry-failure").failureReason()
        )
        assertEquals(
            EmailResolutionFailureReason.NO_CONNECTION,
            repository.resolveEmailById("retry-failure").failureReason()
        )

        assertEquals(4, provider.fetchEmailByIdCalls)
        assertEquals(
            listOf("retry-not-found", "retry-not-found", "retry-failure", "retry-failure"),
            provider.receivedFetchEmailByIdIds
        )
    }

    @Test fun leader_cancellation_cancels_joined_follower_and_retry_starts_new_flight() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val roomDb = Room.inMemoryDatabaseBuilder(context, MailDatabase::class.java)
            .setQueryExecutor(dispatcher.asExecutor())
            .setTransactionExecutor(dispatcher.asExecutor())
            .build()
        val localRepository = EmailRepository(
            database = roomDb,
            providerFactory = { provider },
            pdfCacheManager = PdfCacheManager(
                java.io.File(context.cacheDir, "pdf_joined_cancel_${System.nanoTime()}").apply {
                    mkdirs()
                }
            ),
            writeGuard = writeGuard
        )
        val gate = CompletableDeferred<Unit>()
        provider.fetchEmailByIdDeferred = gate
        provider.fetchEmailByIdResult = EmailLookupResult.Found(
            testEmail("joined-cancel", folder = EmailFolder.Other, subject = "retry")
        )

        val leader = async { localRepository.resolveEmailById("joined-cancel") }
        advanceUntilIdle()
        val follower = async { localRepository.resolveEmailById("joined-cancel") }
        advanceUntilIdle()
        assertEquals(1, provider.fetchEmailByIdCalls)

        leader.cancel(CancellationException("leader sentinel"))
        val leaderCancellation = try {
            leader.await()
            null
        } catch (cancelled: CancellationException) {
            cancelled
        }
        val followerCancellation = try {
            follower.await()
            null
        } catch (cancelled: CancellationException) {
            cancelled
        }

        assertTrue(leaderCancellation is CancellationException)
        assertTrue(followerCancellation is CancellationException)

        provider.fetchEmailByIdDeferred = null
        val retry = localRepository.resolveEmailById("joined-cancel")

        assertTrue(retry is EmailResolutionResult.Found)
        assertEquals("retry", (retry as EmailResolutionResult.Found).email.subject)
        assertEquals(2, provider.fetchEmailByIdCalls)
        roomDb.close()
    }
}
