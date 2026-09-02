package com.david.mailapp.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.david.mailapp.data.local.MailDatabase
import com.david.mailapp.data.local.entity.EmailEntity
import com.david.mailapp.data.pdf.PdfCacheManager
import com.david.mailapp.domain.model.EmailFolder
import com.david.mailapp.domain.model.EmailBodyKind
import com.david.mailapp.domain.model.EmailContentState
import com.david.mailapp.domain.model.EmailInlineReference
import com.david.mailapp.domain.model.PaginatedResult
import com.david.mailapp.domain.model.PdfAttachmentMetadata
import com.david.mailapp.feature.emaildetail.components.EmailHtmlCleaner
import com.david.mailapp.testhelpers.FakeEmailProvider
import com.david.mailapp.testhelpers.FakeSessionWriteGuard
import com.david.mailapp.testhelpers.testEmail
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Fase 2.3: Safe refresh semantics — replace, merge, token handling.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SafeRefreshContractsTest {

    private lateinit var db: MailDatabase
    private lateinit var provider: FakeEmailProvider
    private lateinit var repository: EmailRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, MailDatabase::class.java).build()
        provider = FakeEmailProvider()
        val cacheDir = java.io.File(context.cacheDir, "pdf_test_${System.nanoTime()}").apply { mkdirs() }
        repository = EmailRepository(db, { provider }, PdfCacheManager(cacheDir), FakeSessionWriteGuard())
    }

    @After fun tearDown() { db.close() }

    private suspend fun seedInbox(emails: List<com.david.mailapp.domain.model.Email>) {
        db.emailDao().upsertAll(emails.map { EmailEntity.fromDomain(it, EmailFolder.Inbox) })
    }

    private suspend fun seedTrash(emails: List<com.david.mailapp.domain.model.Email>) {
        db.emailDao().upsertAll(emails.map { EmailEntity.fromDomain(it, EmailFolder.Trash) })
    }

    @Test
    fun full_plain_text_sync_persists_ready_content_without_individual_fetch() = runTest {
        val body = "first line\nsecond line"
        provider.fetchInboxResult = PaginatedResult(
            listOf(
                testEmail("plain-sync").copy(
                    body = body,
                    pdfMetadataScanned = true,
                    contentState = EmailContentState.READY,
                    bodyKind = EmailBodyKind.PLAIN_TEXT
                )
            ),
            null,
            isComplete = true
        )

        repository.refreshInbox(null)

        val saved = db.emailDao().getByIdOnce("plain-sync")!!
        assertEquals(body, saved.body)
        assertEquals(body, saved.cleanBody)
        assertEquals(EmailContentState.READY.name, saved.contentState)
        assertEquals(EmailBodyKind.PLAIN_TEXT.name, saved.bodyKind)
        assertTrue(saved.cachedContentBytes > 0L)
        assertEquals(0, provider.fetchEmailByIdCalls)
    }

    @Test
    fun full_html_sync_persists_clean_body_cid_and_pdf() = runTest {
        val raw = """<p style="color:red">body<img src="cid:image-1"></p>"""
        val refs = listOf(EmailInlineReference("image-1", "attachment-1", "image/png"))
        val pdf = PdfAttachmentMetadata("report.pdf", "application/pdf", "pdf-1", 100L, "part-1")
        provider.fetchInboxResult = PaginatedResult(
            listOf(
                testEmail("html-sync").copy(
                    body = raw,
                    pdfAttachments = listOf(pdf),
                    pdfMetadataScanned = true,
                    contentState = EmailContentState.READY,
                    bodyKind = EmailBodyKind.HTML,
                    inlineReferences = refs
                )
            ),
            null,
            isComplete = true
        )

        repository.refreshInbox(null)

        val saved = db.emailDao().getByIdOnce("html-sync")!!
        assertEquals(raw, saved.body)
        assertEquals(EmailHtmlCleaner.clean(raw), saved.cleanBody)
        assertEquals(refs, saved.toDomain().inlineReferences)
        assertEquals(listOf(pdf), saved.toDomain().pdfAttachments)
        assertEquals(EmailContentState.READY.name, saved.contentState)
    }

    @Test
    fun payload_incomplete_sync_preserves_existing_ready_content() = runTest {
        val existing = testEmail("partial-content").copy(
            body = "<p>cached</p>",
            cleanBody = "<p>cached</p>",
            pdfMetadataScanned = true,
            contentState = EmailContentState.READY,
            bodyKind = EmailBodyKind.HTML,
            cachedContentBytes = 30L
        )
        seedInbox(listOf(existing))
        provider.fetchInboxResult = PaginatedResult(
            listOf(
                testEmail("partial-content").copy(
                    contentState = EmailContentState.EMPTY,
                    bodyKind = EmailBodyKind.UNKNOWN,
                    pdfMetadataScanned = false
                )
            ),
            null,
            isComplete = false
        )

        repository.refreshInbox(null)

        val saved = db.emailDao().getByIdOnce("partial-content")!!
        assertEquals(existing.body, saved.body)
        assertEquals(existing.cleanBody, saved.cleanBody)
        assertEquals(EmailContentState.READY.name, saved.contentState)
    }

    // ── Inbox: first page complete → replace ────────────────────

    @Test fun inbox_first_page_complete_replaces_folder() = runTest {
        seedInbox(listOf(testEmail("old", subject = "Old")))
        provider.fetchInboxResult = PaginatedResult(
            listOf(testEmail("new", subject = "New")), "page-2", isComplete = true
        )
        repository.refreshInbox(null)
        val inbox = db.emailDao().getEntitiesByFolderSync("inbox")
        assertEquals("replaced: only new email", 1, inbox.size)
        assertEquals("new", inbox[0].id)
    }

    // ── Inbox: first page partial → merge ───────────────────────

    @Test fun inbox_first_page_partial_merges_not_replaces() = runTest {
        seedInbox(listOf(testEmail("old", subject = "Old")))
        provider.fetchInboxResult = PaginatedResult(
            listOf(testEmail("partial", subject = "Partial")),
            nextPageToken = "must-not-advance", isComplete = false
        )
        val result = repository.refreshInbox(null)
        assertNull("partial page → no token exposed", result.nextPageToken)
        val inbox = db.emailDao().getEntitiesByFolderSync("inbox")
        assertTrue("old email preserved", inbox.any { it.id == "old" })
        assertTrue("partial email merged", inbox.any { it.id == "partial" })
        assertEquals(2, inbox.size)
    }

    // ── Inbox: pagination → merge (never replace) ────────────────

    @Test fun inbox_pagination_merges_never_replaces() = runTest {
        seedInbox(listOf(testEmail("page1")))
        provider.fetchInboxResult = PaginatedResult(
            listOf(testEmail("page2")), "page-3", isComplete = true
        )
        repository.refreshInbox("page-2")
        val inbox = db.emailDao().getEntitiesByFolderSync("inbox")
        assertTrue("page1 preserved", inbox.any { it.id == "page1" })
        assertTrue("page2 merged", inbox.any { it.id == "page2" })
        assertEquals(2, inbox.size)
    }

    // ── Replace preserves body, cleanBody, PDF meta, RFC ────────

    @Test fun complete_replace_preserves_existing_body_pdf_rfc_in_both_folders() = runTest {
        val rich = EmailEntity.fromDomain(
            testEmail("rich").copy(body = "<html>B</html>", cleanBody = "B",
                rfcMessageId = "<m@x.com>", rfcReferences = "<r@x.com>",
                pdfAttachments = listOf(com.david.mailapp.domain.model.PdfAttachmentMetadata(
                    "f.pdf", "application/pdf", "att1", 1024L)),
                pdfMetadataScanned = true),
            EmailFolder.Inbox
        )
        val richTrash = rich.copy(id = "rich-trash", folder = "trash")
        db.emailDao().upsertAll(listOf(rich, richTrash))

        provider.fetchInboxResult = PaginatedResult(
            listOf(testEmail("rich")),
            "page-2", isComplete = true
        )
        provider.fetchTrashResult = PaginatedResult(
            listOf(testEmail("rich-trash", folder = EmailFolder.Trash)),
            "trash-page-2", isComplete = true
        )
        repository.refreshInbox(null)
        repository.refreshTrash(null)

        db.emailDao().getEntitiesByIdsSync(listOf("rich", "rich-trash")).forEach { saved ->
            assertEquals("<html>B</html>", saved.body)
            assertEquals("B", saved.cleanBody)
            assertTrue(saved.pdfMetadataScanned)
            assertTrue(saved.pdfAttachmentsJson.contains("f.pdf"))
            assertEquals("<m@x.com>", saved.rfcMessageId)
            assertEquals("<r@x.com>", saved.rfcReferences)
        }
    }

    // ── Trash: same semantics as Inbox ───────────────────────────

    @Test fun trash_first_page_complete_replaces() = runTest {
        seedTrash(listOf(testEmail("old")))
        provider.fetchTrashResult = PaginatedResult(
            listOf(testEmail("new")), "t2", isComplete = true
        )
        repository.refreshTrash(null)
        assertEquals(1, db.emailDao().getEntitiesByFolderSync("trash").size)
    }

    @Test fun trash_first_page_partial_merges() = runTest {
        seedTrash(listOf(testEmail("old")))
        provider.fetchTrashResult = PaginatedResult(
            listOf(testEmail("partial")), "must-not-advance", isComplete = false
        )
        val result = repository.refreshTrash(null)
        assertNull("partial page must not expose a token", result.nextPageToken)
        assertEquals(2, db.emailDao().getEntitiesByFolderSync("trash").size)
    }

    @Test fun trash_pagination_merges_never_replaces() = runTest {
        seedTrash(listOf(testEmail("page1", folder = EmailFolder.Trash)))
        provider.fetchTrashResult = PaginatedResult(
            listOf(testEmail("page2", folder = EmailFolder.Trash)),
            "page-3", isComplete = true
        )

        repository.refreshTrash("page-2")

        val trash = db.emailDao().getEntitiesByFolderSync("trash")
        assertTrue(trash.any { it.id == "page1" })
        assertTrue(trash.any { it.id == "page2" })
        assertEquals(2, trash.size)
    }

    // ── Instrumented coordination tests ──────────────────────────

    @Test fun inbox_obsolete_slow_refresh_rejected() = runTest {
        val gateA = CompletableDeferred<Unit>()
        provider.enqueueInbox(
            PaginatedResult(listOf(testEmail("A")), "page-A", isComplete = true),
            gateA,
            ignoreCancellation = true
        )

        val jobA = launch {
            repository.refreshInbox(null)
        }
        runCurrent()

        provider.enqueueInbox(
            PaginatedResult(listOf(testEmail("B")), "page-B", isComplete = true)
        )
        repository.refreshInbox(null)

        gateA.complete(Unit)
        jobA.join()

        val inbox = db.emailDao().getEntitiesByFolderSync("inbox")
        assertEquals(1, inbox.size)
        assertEquals("B", inbox[0].id)
    }

    @Test fun inbox_active_pagination_obsoleted_by_refresh_rejected() = runTest {
        seedInbox(listOf(testEmail("initial")))

        val gateA = CompletableDeferred<Unit>()
        provider.enqueueInbox(
            PaginatedResult(listOf(testEmail("paginated-A")), "page-3", isComplete = true),
            gateA,
            ignoreCancellation = true
        )

        val jobA = launch {
            repository.refreshInbox("page-2")
        }
        runCurrent()

        provider.enqueueInbox(
            PaginatedResult(listOf(testEmail("B")), "page-B", isComplete = true)
        )
        repository.refreshInbox(null)

        gateA.complete(Unit)
        jobA.join()

        val inbox = db.emailDao().getEntitiesByFolderSync("inbox")
        assertEquals(1, inbox.size)
        assertEquals("B", inbox[0].id)
    }

    @Test fun trash_obsolete_slow_refresh_rejected() = runTest {
        val gateA = CompletableDeferred<Unit>()
        provider.enqueueTrash(
            PaginatedResult(listOf(testEmail("A", folder = EmailFolder.Trash)), "page-A", isComplete = true),
            gateA,
            ignoreCancellation = true
        )

        val jobA = launch {
            repository.refreshTrash(null)
        }
        runCurrent()

        provider.enqueueTrash(
            PaginatedResult(listOf(testEmail("B", folder = EmailFolder.Trash)), "page-B", isComplete = true)
        )
        repository.refreshTrash(null)

        gateA.complete(Unit)
        jobA.join()

        val trash = db.emailDao().getEntitiesByFolderSync("trash")
        assertEquals(1, trash.size)
        assertEquals("B", trash[0].id)
    }

    @Test fun trash_active_pagination_obsoleted_by_refresh_rejected() = runTest {
        seedTrash(listOf(testEmail("initial", folder = EmailFolder.Trash)))

        val gateA = CompletableDeferred<Unit>()
        provider.enqueueTrash(
            PaginatedResult(listOf(testEmail("paginated-A", folder = EmailFolder.Trash)), "page-3", isComplete = true),
            gateA,
            ignoreCancellation = true
        )

        val jobA = launch {
            repository.refreshTrash("page-2")
        }
        runCurrent()

        provider.enqueueTrash(
            PaginatedResult(listOf(testEmail("B", folder = EmailFolder.Trash)), "page-B", isComplete = true)
        )
        repository.refreshTrash(null)

        gateA.complete(Unit)
        jobA.join()

        val trash = db.emailDao().getEntitiesByFolderSync("trash")
        assertEquals(1, trash.size)
        assertEquals("B", trash[0].id)
    }
}

// Provide runTest import context
private fun runTest(block: suspend kotlinx.coroutines.test.TestScope.() -> Unit) {
    kotlinx.coroutines.test.runTest { block() }
}
