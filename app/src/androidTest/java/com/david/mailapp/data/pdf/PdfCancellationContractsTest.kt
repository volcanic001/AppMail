package com.david.mailapp.data.pdf

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.david.mailapp.data.local.MailDatabase
import com.david.mailapp.data.repository.EmailRepository
import com.david.mailapp.domain.model.PdfAttachmentMetadata
import com.david.mailapp.testhelpers.FakeEmailProvider
import com.david.mailapp.testhelpers.FakeSessionWriteGuard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File

class PdfCancellationContractsTest {

    private lateinit var db: MailDatabase
    private lateinit var cacheDir: File

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, MailDatabase::class.java).build()
        cacheDir = File(context.cacheDir, "pdf_cancel_test_${System.nanoTime()}")
        cacheDir.mkdirs()
    }

    @After
    fun tearDown() {
        db.close()
        cacheDir.deleteRecursively()
    }

    private fun assertCacheHasNoFiles() {
        val cacheFiles = cacheDir.walkTopDown()
            .filter { it.isFile }
            .toList()
        assertTrue("No cache files should be created: $cacheFiles", cacheFiles.isEmpty())
    }

    @Test fun c3_cancellation_propagates_not_converted_to_error() = runTest {
        val fakeProvider = FakeEmailProvider()
        val sentinel = CancellationException("Download cancelled from network")
        fakeProvider.downloadAttachmentError = sentinel
        val repository = EmailRepository(
            database = db, providerFactory = { fakeProvider },
            pdfCacheManager = PdfCacheManager(cacheDir), writeGuard = FakeSessionWriteGuard()
        )
        val metadata = PdfAttachmentMetadata(
            fileName = "test.pdf", mimeType = "application/pdf",
            attachmentId = "att_1", sizeBytes = 1024L, partId = "1"
        )
        try {
            repository.downloadPdf("email_1", metadata)
            fail("Expected CancellationException to propagate")
        } catch (e: CancellationException) {
            assertSame("Must propagate exact sentinel instance", sentinel, e)
        } catch (e: Exception) {
            fail("Expected CancellationException, got ${e.javaClass.simpleName}")
        }
        assertCacheHasNoFiles()
    }

    @Test fun c3_cancellation_during_commit_propagates() = runTest {
        val fakeProvider = FakeEmailProvider()
        fakeProvider.downloadAttachmentResult =
            byteArrayOf(0x25, 0x50, 0x44, 0x46, 0x2D)

        val sentinel = CancellationException("Commit cancelled")
        val failingGuard = FakeSessionWriteGuard().apply {
            commitError = sentinel
        }

        val repository = EmailRepository(
            database = db, providerFactory = { fakeProvider },
            pdfCacheManager = PdfCacheManager(cacheDir), writeGuard = failingGuard
        )
        val metadata = PdfAttachmentMetadata(
            fileName = "test.pdf", mimeType = "application/pdf",
            attachmentId = "att_1", sizeBytes = 1024L, partId = "1"
        )
        try {
            repository.downloadPdf("email_1", metadata)
            fail("Expected CancellationException to propagate from commit")
        } catch (e: CancellationException) {
            assertSame("Must propagate exact sentinel instance", sentinel, e)
        } catch (e: Exception) {
            fail("Expected CancellationException, got ${e.javaClass.simpleName}")
        }
        assertCacheHasNoFiles()
    }
}
