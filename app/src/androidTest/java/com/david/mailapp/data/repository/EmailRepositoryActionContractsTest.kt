package com.david.mailapp.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.david.mailapp.core.localization.UiErrorReason
import com.david.mailapp.data.local.MailDatabase
import com.david.mailapp.data.local.entity.EmailEntity
import com.david.mailapp.data.pdf.PdfCacheManager
import com.david.mailapp.domain.model.EmailFolder
import com.david.mailapp.domain.model.PaginatedResult
import com.david.mailapp.testhelpers.FakeEmailProvider
import com.david.mailapp.testhelpers.FakeSessionWriteGuard
import com.david.mailapp.testhelpers.testEmail
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EmailRepositoryActionContractsTest {

    private lateinit var db: MailDatabase
    private lateinit var fakeProvider: FakeEmailProvider
    private lateinit var fakeWriteGuard: FakeSessionWriteGuard
    private lateinit var repository: EmailRepository
    private lateinit var cacheDir: java.io.File
    private lateinit var events: MutableList<String>

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, MailDatabase::class.java).build()
        fakeProvider = FakeEmailProvider()
        fakeWriteGuard = FakeSessionWriteGuard()
        events = mutableListOf()
        fakeProvider.eventLog = events
        fakeWriteGuard.eventLog = events
        cacheDir = java.io.File(context.cacheDir, "pdf_test_${System.nanoTime()}")
        cacheDir.mkdirs()
        repository = EmailRepository(
            database = db, providerFactory = { fakeProvider },
            pdfCacheManager = PdfCacheManager(cacheDir), writeGuard = fakeWriteGuard
        )
    }

    @After fun tearDown() { db.close() }

    private suspend fun seed(id: String, folder: EmailFolder = EmailFolder.Inbox, isRead: Boolean = false) {
        db.emailDao().upsertAll(listOf(EmailEntity.fromDomain(
            testEmail(id = id, folder = folder, isRead = isRead), folder)))
    }
    private suspend fun get(id: String) = db.emailDao().getEntitiesByIdsSync(listOf(id)).firstOrNull()

    // ═══════════════════════════════════════════════════════════════
    // C1 — Gmail failure → Failure, Room intact
    // ═══════════════════════════════════════════════════════════════

    @Test fun c1_moveToTrash_Gmail_fails_returns_Failure_room_unchanged() = runTest {
        seed("e1", EmailFolder.Inbox)
        fakeProvider.moveToTrashError = IOException("Gmail error")
        val result = repository.moveToTrash("e1")
        assertTrue(result is EmailActionResult.Failure)
        val failure = result as EmailActionResult.Failure
        assertFalse(failure.remoteApplied)
        assertEquals(UiErrorReason.NO_CONNECTION, failure.reason)
        assertEquals("inbox", get("e1")?.folder)
    }

    @Test fun c1_restoreFromTrash_Gmail_fails_returns_Failure_room_unchanged() = runTest {
        seed("e1", EmailFolder.Trash)
        fakeProvider.restoreFromTrashError = IOException("Gmail error")
        val result = repository.restoreFromTrash("e1")
        assertTrue(result is EmailActionResult.Failure)
        val failure = result as EmailActionResult.Failure
        assertFalse(failure.remoteApplied)
        assertEquals(UiErrorReason.NO_CONNECTION, failure.reason)
        assertEquals("trash", get("e1")?.folder)
    }

    @Test fun c1_deletePermanently_Gmail_fails_returns_Failure_room_unchanged() = runTest {
        seed("e1", EmailFolder.Trash)
        fakeProvider.deletePermanentlyError = IOException("Gmail error")
        val result = repository.deletePermanently("e1")
        assertTrue(result is EmailActionResult.Failure)
        val failure = result as EmailActionResult.Failure
        assertFalse(failure.remoteApplied)
        assertEquals(UiErrorReason.NO_CONNECTION, failure.reason)
        assertTrue(get("e1") != null)
    }

    @Test fun c1_markAsRead_Gmail_fails_returns_Failure_room_unchanged() = runTest {
        seed("e1", EmailFolder.Inbox, isRead = false)
        fakeProvider.markAsReadError = IOException("Gmail error")
        val result = repository.markAsRead("e1")
        assertTrue(result is EmailActionResult.Failure)
        val failure = result as EmailActionResult.Failure
        assertFalse(failure.remoteApplied)
        assertEquals(UiErrorReason.NO_CONNECTION, failure.reason)
        assertFalse(get("e1")?.isRead ?: true)
    }

    // ═══════════════════════════════════════════════════════════════
    // Success: Gmail first, then Room
    // ═══════════════════════════════════════════════════════════════

    @Test fun c1_moveToTrash_success_remote_first_room_reflects() = runTest {
        seed("e1", EmailFolder.Inbox)
        val result = repository.moveToTrash("e1")
        assertTrue(result is EmailActionResult.Success)
        assertEquals("trash", get("e1")?.folder)
        assertEquals(1, fakeProvider.moveToTrashCalls)
        assertEquals(listOf("gmail.moveToTrash", "room.commit"), events)
    }

    @Test fun c1_restoreFromTrash_success_remote_first_room_reflects() = runTest {
        seed("e1", EmailFolder.Trash)
        val result = repository.restoreFromTrash("e1")
        assertTrue(result is EmailActionResult.Success)
        assertEquals("inbox", get("e1")?.folder)
        assertEquals(1, fakeProvider.restoreFromTrashCalls)
        assertEquals(listOf("gmail.restoreFromTrash", "room.commit"), events)
    }

    @Test fun c1_deletePermanently_success_remote_first_room_reflects() = runTest {
        seed("e1", EmailFolder.Trash)
        val result = repository.deletePermanently("e1")
        assertTrue(result is EmailActionResult.Success)
        assertTrue(get("e1") == null)
        assertEquals(1, fakeProvider.deletePermanentlyCalls)
        assertEquals(listOf("gmail.deletePermanently", "room.commit"), events)
    }

    @Test fun c1_markAsRead_success_remote_first_room_reflects() = runTest {
        seed("e1", EmailFolder.Inbox, isRead = false)
        val result = repository.markAsRead("e1")
        assertTrue(result is EmailActionResult.Success)
        assertTrue(get("e1")?.isRead ?: false)
        assertEquals(1, fakeProvider.markAsReadCalls)
        assertEquals(listOf("gmail.markAsRead", "room.commit"), events)
    }

    @Test fun c1_markAsRead_from_trash_success_remote_first_room_reflects() = runTest {
        seed("e1", EmailFolder.Trash, isRead = false)

        val result = repository.markAsRead("e1")

        assertTrue(result is EmailActionResult.Success)
        assertTrue(get("e1")?.isRead ?: false)
        assertEquals("trash", get("e1")?.folder)
        assertEquals(1, fakeProvider.markAsReadCalls)
        assertEquals(listOf("gmail.markAsRead", "room.commit"), events)
    }

    // ═══════════════════════════════════════════════════════════════
    // C2 — Remote OK, local commit failed → Failure + reconcile
    // ═══════════════════════════════════════════════════════════════

    @Test fun c2_deletePermanently_commit_rejected_reconciles_trash() = runTest {
        seed("e1", EmailFolder.Trash)
        fakeWriteGuard.commitReturnsNull = true
        val result = repository.deletePermanently("e1")
        // Remote OK → Gmail called
        assertEquals(1, fakeProvider.deletePermanentlyCalls)
        // Commit rejected → Failure(UNKNOWN, remoteApplied=true)
        assertTrue(result is EmailActionResult.Failure)
        val failure = result as EmailActionResult.Failure
        assertTrue(failure.remoteApplied)
        assertEquals(UiErrorReason.UNKNOWN, failure.reason)
        // Reconciliation → trash fetched
        assertTrue(fakeProvider.fetchTrashCalls >= 1)
    }

    @Test fun c2_moveToTrash_commit_rejected_reconciles_inbox_and_trash() = runTest {
        seed("e1", EmailFolder.Inbox)
        fakeWriteGuard.commitReturnsNull = true
        val result = repository.moveToTrash("e1")
        assertEquals(1, fakeProvider.moveToTrashCalls)
        assertTrue(result is EmailActionResult.Failure)
        val failure = result as EmailActionResult.Failure
        assertTrue(failure.remoteApplied)
        assertEquals(UiErrorReason.UNKNOWN, failure.reason)
        assertTrue(fakeProvider.fetchInboxCalls >= 1)
        assertTrue(fakeProvider.fetchTrashCalls >= 1)
        assertEquals(
            listOf("gmail.fetch.inbox", "gmail.fetch.trash"),
            events.filter { it.startsWith("gmail.fetch") }
        )
    }

    @Test fun c2_restore_commit_rejected_reconciles_trash_then_inbox() = runTest {
        seed("e1", EmailFolder.Trash)
        fakeWriteGuard.commitReturnsNull = true

        val result = repository.restoreFromTrash("e1")

        assertTrue(result is EmailActionResult.Failure)
        val failure = result as EmailActionResult.Failure
        assertTrue(failure.remoteApplied)
        assertEquals(UiErrorReason.UNKNOWN, failure.reason)
        assertEquals(
            listOf("gmail.fetch.trash", "gmail.fetch.inbox"),
            events.filter { it.startsWith("gmail.fetch") }
        )
    }

    @Test fun c2_markAsRead_commit_rejected_reconciles_inbox_and_trash() = runTest {
        seed("e1", EmailFolder.Trash, isRead = false)
        fakeWriteGuard.commitReturnsNull = true

        val result = repository.markAsRead("e1")

        assertTrue(result is EmailActionResult.Failure)
        assertTrue((result as EmailActionResult.Failure).remoteApplied)
        assertEquals(
            listOf("gmail.fetch.inbox", "gmail.fetch.trash"),
            events.filter { it.startsWith("gmail.fetch") }
        )
    }

    @Test fun c2_first_reconciliation_failure_does_not_block_second_folder() = runTest {
        seed("e1", EmailFolder.Inbox)
        fakeWriteGuard.commitReturnsNull = true
        fakeProvider.fetchInboxError = IOException("Inbox unavailable")

        val result = repository.moveToTrash("e1")

        assertTrue(result is EmailActionResult.Failure)
        assertEquals(
            listOf("gmail.fetch.inbox", "gmail.fetch.trash"),
            events.filter { it.startsWith("gmail.fetch") }
        )
    }

    @Test fun c2_local_exception_returns_failure_and_attempts_reconciliation() = runTest {
        seed("e1", EmailFolder.Trash)
        fakeWriteGuard.commitError = IllegalStateException("Room write failed")

        val result = repository.deletePermanently("e1")

        assertTrue(result is EmailActionResult.Failure)
        val failure = result as EmailActionResult.Failure
        assertTrue(failure.remoteApplied)
        assertEquals(UiErrorReason.UNKNOWN, failure.reason)
        assertEquals(1, fakeProvider.fetchTrashCalls)
    }

    // ═══════════════════════════════════════════════════════════════
    // Session / cancellation contracts
    // ═══════════════════════════════════════════════════════════════

    @Test fun no_lease_returns_no_active_account_without_remote_or_local() = runTest {
        fakeWriteGuard.captureResult = null
        seed("e1", EmailFolder.Inbox)
        val result = repository.moveToTrash("e1")
        assertTrue(result is EmailActionResult.Failure)
        val failure = result as EmailActionResult.Failure
        assertFalse(failure.remoteApplied)
        assertEquals(UiErrorReason.NO_ACTIVE_ACCOUNT, failure.reason)
        assertEquals(0, fakeProvider.moveToTrashCalls)
        assertEquals("inbox", get("e1")?.folder)
    }

    @Test fun no_provider_returns_no_active_account_without_local_commit() = runTest {
        val repositoryWithoutProvider = EmailRepository(
            database = db,
            providerFactory = { null },
            pdfCacheManager = PdfCacheManager(cacheDir),
            writeGuard = fakeWriteGuard
        )
        seed("e1", EmailFolder.Inbox)

        val result = repositoryWithoutProvider.moveToTrash("e1")

        assertTrue(result is EmailActionResult.Failure)
        val failure = result as EmailActionResult.Failure
        assertEquals(UiErrorReason.NO_ACTIVE_ACCOUNT, failure.reason)
        assertFalse(failure.remoteApplied)
        assertTrue(events.isEmpty())
        assertEquals("inbox", get("e1")?.folder)
    }

    @Test fun remote_cancellation_is_rethrown_and_room_is_unchanged() = runTest {
        seed("e1", EmailFolder.Trash)
        val cancellation = CancellationException("remote cancelled")
        fakeProvider.deletePermanentlyError = cancellation

        val thrown = try {
            repository.deletePermanently("e1")
            null
        } catch (e: CancellationException) {
            e
        }

        assertSame(cancellation, thrown)
        assertTrue(get("e1") != null)
        assertFalse(events.contains("room.commit"))
    }

    @Test fun local_commit_cancellation_is_rethrown_without_reconciliation() = runTest {
        seed("e1", EmailFolder.Trash)
        val cancellation = CancellationException("commit cancelled")
        fakeWriteGuard.commitError = cancellation

        val thrown = try {
            repository.deletePermanently("e1")
            null
        } catch (e: CancellationException) {
            e
        }

        assertSame(cancellation, thrown)
        assertEquals(0, fakeProvider.fetchTrashCalls)
    }

    @Test fun reconciliation_cancellation_is_rethrown_as_same_instance() = runTest {
        seed("e1", EmailFolder.Trash)
        val cancellation = CancellationException("reconciliation cancelled")
        fakeWriteGuard.commitReturnsNull = true
        fakeProvider.fetchTrashError = cancellation

        val thrown = try {
            repository.deletePermanently("e1")
            null
        } catch (e: CancellationException) {
            e
        }

        assertSame(cancellation, thrown)
    }

    @Test fun remaining_actions_without_lease_return_no_active_account_without_side_effects() =
        runTest {
            seed("restore-no-lease", EmailFolder.Trash)
            seed("delete-no-lease", EmailFolder.Trash)
            seed("read-no-lease", EmailFolder.Inbox, isRead = false)
            fakeWriteGuard.captureResult = null

            val results = listOf(
                repository.restoreFromTrash("restore-no-lease"),
                repository.deletePermanently("delete-no-lease"),
                repository.markAsRead("read-no-lease")
            )

            results.forEach { result ->
                assertTrue(result is EmailActionResult.Failure)
                result as EmailActionResult.Failure
                assertEquals(UiErrorReason.NO_ACTIVE_ACCOUNT, result.reason)
                assertFalse(result.remoteApplied)
            }
            assertEquals(0, fakeProvider.restoreFromTrashCalls)
            assertEquals(0, fakeProvider.deletePermanentlyCalls)
            assertEquals(0, fakeProvider.markAsReadCalls)
            assertEquals("trash", get("restore-no-lease")?.folder)
            assertTrue(get("delete-no-lease") != null)
            assertFalse(get("read-no-lease")?.isRead ?: true)
        }

    @Test fun remaining_actions_without_provider_return_no_active_account_without_local_commit() =
        runTest {
            seed("restore-no-provider", EmailFolder.Trash)
            seed("delete-no-provider", EmailFolder.Trash)
            seed("read-no-provider", EmailFolder.Inbox, isRead = false)
            val repositoryWithoutProvider = EmailRepository(
                database = db,
                providerFactory = { null },
                pdfCacheManager = PdfCacheManager(cacheDir),
                writeGuard = fakeWriteGuard
            )

            val results = listOf(
                repositoryWithoutProvider.restoreFromTrash("restore-no-provider"),
                repositoryWithoutProvider.deletePermanently("delete-no-provider"),
                repositoryWithoutProvider.markAsRead("read-no-provider")
            )

            results.forEach { result ->
                assertTrue(result is EmailActionResult.Failure)
                result as EmailActionResult.Failure
                assertEquals(UiErrorReason.NO_ACTIVE_ACCOUNT, result.reason)
                assertFalse(result.remoteApplied)
            }
            assertTrue(events.isEmpty())
            assertEquals("trash", get("restore-no-provider")?.folder)
            assertTrue(get("delete-no-provider") != null)
            assertFalse(get("read-no-provider")?.isRead ?: true)
        }

    @Test fun remaining_remote_cancellations_propagate_same_instance_without_room_changes() =
        runTest {
            seed("move-cancel", EmailFolder.Inbox)
            seed("restore-cancel", EmailFolder.Trash)
            seed("read-cancel", EmailFolder.Inbox, isRead = false)

            val moveCancellation = CancellationException("cancel move")
            fakeProvider.moveToTrashError = moveCancellation
            val thrownMove = try {
                repository.moveToTrash("move-cancel")
                null
            } catch (cancelled: CancellationException) {
                cancelled
            }
            assertSame(moveCancellation, thrownMove)
            fakeProvider.moveToTrashError = null

            val restoreCancellation = CancellationException("cancel restore")
            fakeProvider.restoreFromTrashError = restoreCancellation
            val thrownRestore = try {
                repository.restoreFromTrash("restore-cancel")
                null
            } catch (cancelled: CancellationException) {
                cancelled
            }
            assertSame(restoreCancellation, thrownRestore)
            fakeProvider.restoreFromTrashError = null

            val readCancellation = CancellationException("cancel read")
            fakeProvider.markAsReadError = readCancellation
            val thrownRead = try {
                repository.markAsRead("read-cancel")
                null
            } catch (cancelled: CancellationException) {
                cancelled
            }
            assertSame(readCancellation, thrownRead)

            assertEquals("inbox", get("move-cancel")?.folder)
            assertEquals("trash", get("restore-cancel")?.folder)
            assertFalse(get("read-cancel")?.isRead ?: true)
            assertFalse(events.contains("room.commit"))
        }

    @Test fun rejected_action_commit_accepts_complete_reconciliation_in_folder_order() = runTest {
        seed("stale-inbox", EmailFolder.Inbox)
        seed("stale-trash", EmailFolder.Trash)
        fakeWriteGuard.commitReturnsNullByCall = listOf(true, false, false)
        fakeProvider.fetchInboxResult = PaginatedResult(
            listOf(testEmail("server-inbox", folder = EmailFolder.Inbox)),
            null,
            isComplete = true
        )
        fakeProvider.fetchTrashResult = PaginatedResult(
            listOf(testEmail("server-trash", folder = EmailFolder.Trash)),
            null,
            isComplete = true
        )

        val result = repository.moveToTrash("remote-action")

        assertTrue(result is EmailActionResult.Failure)
        result as EmailActionResult.Failure
        assertEquals(UiErrorReason.UNKNOWN, result.reason)
        assertTrue(result.remoteApplied)
        assertEquals(listOf("server-inbox"), db.emailDao().getEntitiesByFolderSync("inbox").map { it.id })
        assertEquals(listOf("server-trash"), db.emailDao().getEntitiesByFolderSync("trash").map { it.id })
        assertEquals(
            listOf(
                "gmail.moveToTrash",
                "room.commit",
                "gmail.fetch.inbox",
                "room.commit",
                "gmail.fetch.trash",
                "room.commit"
            ),
            events
        )
        assertEquals(3, fakeWriteGuard.commitCalls)
    }

    @Test fun rejected_action_commit_merges_partial_reconciliation_without_deleting_cache() =
        runTest {
            seed("cached-inbox", EmailFolder.Inbox)
            seed("cached-trash", EmailFolder.Trash)
            fakeWriteGuard.commitReturnsNullByCall = listOf(true, false, false)
            fakeProvider.fetchInboxResult = PaginatedResult(
                listOf(testEmail("partial-inbox", folder = EmailFolder.Inbox)),
                "ignored-inbox-token",
                isComplete = false
            )
            fakeProvider.fetchTrashResult = PaginatedResult(
                listOf(testEmail("partial-trash", folder = EmailFolder.Trash)),
                "ignored-trash-token",
                isComplete = false
            )

            val result = repository.moveToTrash("remote-action")

            assertTrue(result is EmailActionResult.Failure)
            result as EmailActionResult.Failure
            assertEquals(UiErrorReason.UNKNOWN, result.reason)
            assertTrue(result.remoteApplied)
            assertEquals(
                setOf("cached-inbox", "partial-inbox"),
                db.emailDao().getEntitiesByFolderSync("inbox").map { it.id }.toSet()
            )
            assertEquals(
                setOf("cached-trash", "partial-trash"),
                db.emailDao().getEntitiesByFolderSync("trash").map { it.id }.toSet()
            )
            assertEquals(
                listOf("gmail.fetch.inbox", "gmail.fetch.trash"),
                events.filter { it.startsWith("gmail.fetch") }
            )
            assertEquals(3, fakeWriteGuard.commitCalls)
        }
}
