package com.david.mailapp.data.cleaner

import com.david.mailapp.data.local.dao.EmailDao
import com.david.mailapp.data.local.entity.EmailEntity
import com.david.mailapp.domain.model.Email
import com.david.mailapp.domain.model.EmailBodyKind
import com.david.mailapp.domain.model.EmailContentState
import com.david.mailapp.domain.model.EmailFolder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlCleaningCoordinatorTest {

    private open class FakeEmailDao(
        var casReturnResult: Boolean = true
    ) : EmailDao {
        var updateCleanBodyCallCount = 0

        override suspend fun updateCleanBodyIfCurrentAndEnforceLru(
            emailId: String,
            expectedRawBody: String,
            cleanBody: String,
            cachedContentBytes: Long,
            maxBudgetBytes: Long
        ): Boolean {
            updateCleanBodyCallCount++
            return casReturnResult
        }

        override fun observeSummariesByFolder(folder: String): Flow<List<com.david.mailapp.data.local.entity.EmailSummaryProjection>> = throw NotImplementedError()
        override fun getById(emailId: String): Flow<EmailEntity?> = throw NotImplementedError()
        override suspend fun getByIdOnce(emailId: String): EmailEntity? = null
        override suspend fun upsertAll(emails: List<EmailEntity>) {}
        override suspend fun moveToFolder(emailId: String, newFolder: String) {}
        override suspend fun updateReadStatus(emailId: String, isRead: Boolean) {}
        override suspend fun deleteById(emailId: String) {}
        override suspend fun clearFolder(folder: String) {}
        override suspend fun getEntitiesByFolderSync(folder: String): List<EmailEntity> = emptyList()
        override suspend fun getEntitiesByIdsSync(ids: List<String>): List<EmailEntity> = emptyList()
        override suspend fun updateBodyAndPdfMetadata(emailId: String, body: String, cleanBody: String, pdfAttachmentsJson: String, hasAttachments: Boolean, contentState: String, bodyKind: String, inlineReferencesJson: String, cachedContentBytes: Long) {}
        override suspend fun updateCleanBodyIfCurrent(emailId: String, expectedRawBody: String, cleanBody: String, cachedContentBytes: Long): Int = 1
        override suspend fun sumReadyContentBytes(): Long? = 0L
        override suspend fun getLruEvictionCandidates(protectedEmailId: String): List<EmailEntity> = emptyList()
        override suspend fun getGlobalLruEvictionCandidates(): List<EmailEntity> = emptyList()
        override suspend fun clearContent(emailId: String) {}
        override suspend fun getMaxContentLastAccess(): Long? = 0L
        override suspend fun updateContentLastAccess(emailId: String, newTimestamp: Long) {}
    }

    private fun createEmail(
        id: String = "e1",
        body: String = "<p>Hello</p>",
        cleanBody: String = "",
        bodyKind: EmailBodyKind = EmailBodyKind.HTML,
        contentState: EmailContentState = EmailContentState.READY
    ) = Email(
        id = id,
        threadId = "t1",
        from = "sender@example.com",
        fromInitials = "S",
        to = "recipient@example.com",
        subject = "Subject",
        snippet = "Snippet",
        timestamp = 1000L,
        isRead = true,
        isStarred = false,
        hasAttachments = false,
        labels = emptyList(),
        folder = EmailFolder.Inbox,
        body = body,
        cleanBody = cleanBody,
        contentState = contentState,
        bodyKind = bodyKind
    )

    @Test
    fun `already clean email executes zero cleanings and returns Cleaned`() = runTest {
        val fakeDao = FakeEmailDao()
        var sessionGen = 1L
        val coordinator = HtmlCleaningCoordinator(fakeDao, { sessionGen })

        val cleanEmail = createEmail(cleanBody = "<div style=\"margin:0 16px;\"><p>Hello</p></div>")
        val result = coordinator.cleanAndPersist(cleanEmail)

        assertTrue(result is HtmlCleanResult.Cleaned)
        assertEquals(cleanEmail.cleanBody, (result as HtmlCleanResult.Cleaned).displayBody)
        assertEquals(0, fakeDao.updateCleanBodyCallCount)
    }

    @Test
    fun `concurrent calls for same email and body share single cleaning pass`() = runTest {
        val fakeDao = FakeEmailDao(casReturnResult = true)
        var sessionGen = 1L
        val coordinator = HtmlCleaningCoordinator(fakeDao, { sessionGen })

        val email = createEmail()

        // Launch 5 concurrent cleaning requests for the exact same email version
        val jobs = List(5) {
            async { coordinator.cleanAndPersist(email) }
        }

        val results = jobs.awaitAll()
        results.forEach { result ->
            assertTrue(result is HtmlCleanResult.Cleaned || result is HtmlCleanResult.Fallback)
        }
        assertEquals(1, fakeDao.updateCleanBodyCallCount)
    }

    @Test
    fun `session generation change makes cleaning stale`() = runTest {
        val fakeDao = FakeEmailDao()
        var callCount = 0
        val coordinator = HtmlCleaningCoordinator(fakeDao, {
            callCount++
            if (callCount == 1) 1L else 2L
        })

        val email = createEmail()

        val result = coordinator.cleanAndPersist(email)
        assertEquals(HtmlCleanResult.Stale, result)
    }

    @Test
    fun `cas update failure returns stale`() = runTest {
        val fakeDao = FakeEmailDao(casReturnResult = false)
        var sessionGen = 1L
        val coordinator = HtmlCleaningCoordinator(fakeDao, { sessionGen })

        val email = createEmail()
        val result = coordinator.cleanAndPersist(email)

        assertEquals(HtmlCleanResult.Stale, result)
    }
}
