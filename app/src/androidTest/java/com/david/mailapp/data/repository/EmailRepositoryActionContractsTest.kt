package com.david.mailapp.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.david.mailapp.data.local.MailDatabase
import com.david.mailapp.data.local.entity.EmailEntity
import com.david.mailapp.data.pdf.PdfCacheManager
import com.david.mailapp.domain.model.EmailFolder
import com.david.mailapp.testhelpers.FakeEmailProvider
import com.david.mailapp.testhelpers.FakeSessionWriteGuard
import com.david.mailapp.testhelpers.testEmail
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

class EmailRepositoryActionContractsTest {

    private lateinit var db: MailDatabase
    private lateinit var fakeProvider: FakeEmailProvider
    private lateinit var fakeWriteGuard: FakeSessionWriteGuard
    private lateinit var repository: EmailRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, MailDatabase::class.java).build()
        fakeProvider = FakeEmailProvider()
        fakeWriteGuard = FakeSessionWriteGuard()
        val cacheDir = java.io.File(context.cacheDir, "pdf_test_${System.nanoTime()}")
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

    @Ignore("Contrato pendiente: Fase 2.4")
    @Test fun c1_moveToTrash_Gmail_fails_Room_preserves_inbox() = runTest {
        seed("e1", EmailFolder.Inbox)
        fakeProvider.moveToTrashError = IOException("Gmail error")
        repository.moveToTrash("e1")
        assertEquals("inbox", get("e1")?.folder)
        assertEquals(1, fakeProvider.moveToTrashCalls)
    }

    @Ignore("Contrato pendiente: Fase 2.4")
    @Test fun c1_restoreFromTrash_Gmail_fails_Room_preserves_trash() = runTest {
        seed("e1", EmailFolder.Trash)
        fakeProvider.restoreFromTrashError = IOException("Gmail error")
        repository.restoreFromTrash("e1")
        assertEquals("trash", get("e1")?.folder)
        assertEquals(1, fakeProvider.restoreFromTrashCalls)
    }

    @Ignore("Contrato pendiente: Fase 2.4")
    @Test fun c1_deletePermanently_Gmail_fails_Room_preserves_email() = runTest {
        seed("e1", EmailFolder.Trash)
        fakeProvider.deletePermanentlyError = IOException("Gmail error")
        repository.deletePermanently("e1")
        assertTrue(get("e1") != null)
        assertEquals(1, fakeProvider.deletePermanentlyCalls)
    }

    @Ignore("Contrato pendiente: Fase 2.4")
    @Test fun c1_markAsRead_Gmail_fails_Room_preserves_unread() = runTest {
        seed("e1", EmailFolder.Inbox, isRead = false)
        fakeProvider.markAsReadError = IOException("Gmail error")
        repository.markAsRead("e1")
        assertFalse(get("e1")?.isRead ?: true)
        assertEquals(1, fakeProvider.markAsReadCalls)
    }

    @Ignore("Contrato pendiente: Fase 2.4")
    @Test fun c2_Gmail_OK_Room_fails_no_success_reported() = runTest {
        seed("e1", EmailFolder.Trash)
        fakeWriteGuard.commitReturnsNull = true
        repository.deletePermanently("e1")
        assertEquals("Gmail must be confirmed before the local write", 1, fakeProvider.deletePermanentlyCalls)
        assertEquals("A failed local commit must trigger reconciliation", 1, fakeProvider.fetchTrashCalls)
        assertTrue(get("e1") != null)
    }
}
