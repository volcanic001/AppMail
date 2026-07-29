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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Ignore
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

    @After fun tearDown() { db.close() }

    @Ignore("Contrato pendiente: Fase 3.1")
    @Test fun c3_cancellation_propagates_not_converted_to_error() = runTest {
        val fakeProvider = FakeEmailProvider()
        fakeProvider.downloadAttachmentError = CancellationException("Download cancelled")
        val repository = EmailRepository(
            database = db, providerFactory = { fakeProvider },
            pdfCacheManager = PdfCacheManager(cacheDir), writeGuard = FakeSessionWriteGuard()
        )
        val metadata = PdfAttachmentMetadata(
            fileName = "test.pdf", mimeType = "application/pdf",
            attachmentId = "att_1", sizeBytes = 1024L
        )
        try {
            repository.downloadPdf("email_1", metadata)
            fail("Expected CancellationException to propagate")
        } catch (e: CancellationException) {
            assertEquals("Download cancelled", e.message)
        } catch (e: Exception) {
            fail("Expected CancellationException, got ${e.javaClass.simpleName}")
        }
        val cacheFiles = cacheDir.listFiles() ?: emptyArray()
        assertTrue("No cache files should be created", cacheFiles.isEmpty())
    }
}
