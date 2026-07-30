package com.david.mailapp.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.david.mailapp.data.local.MailDatabase
import com.david.mailapp.data.local.entity.EmailEntity
import com.david.mailapp.data.pdf.PdfCacheManager
import com.david.mailapp.domain.model.EmailFolder
import com.david.mailapp.domain.model.PaginatedResult
import com.david.mailapp.testhelpers.FakeEmailProvider
import com.david.mailapp.testhelpers.FakeSessionWriteGuard
import com.david.mailapp.testhelpers.testEmail
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Fase 2.3: Safe refresh semantics — replace, merge, token handling.
 */
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
}

// Provide runTest import context
private fun runTest(block: suspend kotlinx.coroutines.test.TestScope.() -> Unit) {
    kotlinx.coroutines.test.runTest { block() }
}
