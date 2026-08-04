package com.david.mailapp.feature.emaildetail

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.david.mailapp.data.local.MailDatabase
import com.david.mailapp.data.local.entity.EmailEntity
import com.david.mailapp.data.pdf.PdfCacheManager
import com.david.mailapp.data.remote.provider.EmailLookupResult
import com.david.mailapp.data.repository.EmailRepository
import com.david.mailapp.domain.model.EmailFolder
import com.david.mailapp.testhelpers.FakeEmailProvider
import com.david.mailapp.testhelpers.FakeSessionWriteGuard
import com.david.mailapp.testhelpers.testEmail
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
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

    @After fun tearDown() { db.close() }

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
}
