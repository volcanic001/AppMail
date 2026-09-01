package com.david.mailapp.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.david.mailapp.core.di.SessionCoordinator
import com.david.mailapp.core.session.SessionWriteGuard
import com.david.mailapp.data.local.MailDatabase
import com.david.mailapp.data.local.entity.EmailEntity
import com.david.mailapp.data.pdf.PdfCacheManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class EmailRepositoryAccountIsolationTest {

    private lateinit var db: MailDatabase
    private lateinit var writeGuard: SessionWriteGuard
    private lateinit var pdfCacheManager: PdfCacheManager
    private lateinit var sessionCoordinator: SessionCoordinator
    private lateinit var cacheDir: File
    
    private var isAuth = true
    private var revoked = false
    private var credsCleared = false

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, MailDatabase::class.java).build()
        
        cacheDir = File(context.cacheDir, "test_pdfs")
        cacheDir.mkdirs()
        pdfCacheManager = PdfCacheManager(cacheDir)
        
        writeGuard = com.david.mailapp.core.session.SessionWriteGuardImpl()
        
        sessionCoordinator = SessionCoordinator(
            clearProvider = {},
            clearDatabase = { db.clearAllTables() },
            clearPdfCache = { pdfCacheManager.clearAll() },
            clearSearchHistory = {},
            clearCredentials = { credsCleared = true },
            isAuthenticated = { isAuth },
            reactivateProvider = { writeGuard.activate() },
            writeGuard = writeGuard,
            setPendingPdfCleanup = {},
            readRefreshToken = { null },
            revocationService = null
        )
    }

    @After
    fun teardown() {
        db.close()
        cacheDir.deleteRecursively()
    }

    @Test
    fun oldOperationCannotReinsertAfterLogout() = runTest {
        // 1. Arrange: Authenticated user downloads a PDF and fetches body
        writeGuard.activate()
        val lease = writeGuard.capture()!!
        
        val pdfFile = pdfCacheManager.store("msg1", "att1", "dummy content".toByteArray())
        
        val entity = EmailEntity(
            id = "msg1", threadId = "t1", from = "A", fromInitials = "A", to = "B",
            subject = "S", snippet = "S", timestamp = 1L, isRead = false,
            isStarred = false, hasAttachments = true, labels = "", folder = "inbox",
            body = "old_body", cleanBody = "old", contentState = "READY",
            bodyKind = "HTML", inlineReferencesJson = "[]", cachedContentBytes = 10,
            contentLastAccessEpochMs = 1L, pdfAttachmentsJson = "[]",
            pdfMetadataScanned = true, rfcMessageId = null, rfcReferences = null
        )
        db.emailDao().upsertWithMerge(entity)
        
        // Ensure it's there
        assertEquals("READY", db.emailDao().getById("msg1").first()?.contentState)
        assertTrue(pdfFile.exists())
        
        // 2. Act: User signs out
        sessionCoordinator.signOut()
        
        // 3. Assert stores are empty
        assertNull(db.emailDao().getById("msg1").first())
        assertTrue(java.io.File(cacheDir, "pdf_attachments").listFiles().isNullOrEmpty())
        
        // 4. Act: Old operation tries to write with OLD lease
        val commitResult = writeGuard.commit(lease) {
            db.emailDao().upsertWithMerge(entity)
            pdfCacheManager.store("msg1", "att1", "dummy content".toByteArray())
            true
        }
        
        // 5. Assert: commit failed and stores remain empty
        assertNull(commitResult)
        assertNull(db.emailDao().getById("msg1").first())
        assertTrue(java.io.File(cacheDir, "pdf_attachments").listFiles().isNullOrEmpty())
    }
}
