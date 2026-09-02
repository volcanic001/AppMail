package com.david.mailapp.data.repository

import com.david.mailapp.core.session.SessionWriteGuardImpl
import com.david.mailapp.data.local.dao.EmailDao
import com.david.mailapp.data.local.entity.EmailEntity
import com.david.mailapp.data.local.entity.EmailSummaryProjection
import com.david.mailapp.data.remote.provider.EmailProvider
import com.david.mailapp.domain.model.PaginatedResult
import com.david.mailapp.data.remote.provider.EmailLookupResult
import com.david.mailapp.domain.model.Email
import com.david.mailapp.domain.model.EmailBodyKind
import com.david.mailapp.domain.model.EmailContentState
import com.david.mailapp.domain.model.PdfAttachmentMetadata
import com.david.mailapp.data.remote.provider.ReplyContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailContentCoordinatorBudgetTest {

    private class FakeDao : EmailDao {
        var lastUpdateBodyAndPdf: Map<String, Any?>? = null
        var lastApplyLru: Map<String, Any?>? = null

        override fun observeSummariesByFolder(folder: String): Flow<List<EmailSummaryProjection>> = emptyFlow()
        override fun getById(emailId: String): Flow<EmailEntity?> = emptyFlow()
        override suspend fun getByIdOnce(emailId: String): EmailEntity? = null
        override suspend fun upsertAll(emails: List<EmailEntity>) {}
        override suspend fun moveToFolder(emailId: String, newFolder: String) {}
        override suspend fun updateReadStatus(emailId: String, isRead: Boolean) {}
        override suspend fun deleteById(emailId: String) {}
        override suspend fun clearFolder(folder: String) {}
        override suspend fun getEntitiesByFolderSync(folder: String): List<EmailEntity> = emptyList()
        override suspend fun getEntitiesByIdsSync(ids: List<String>): List<EmailEntity> = emptyList()
        override suspend fun replaceFolder(folder: String, emails: List<EmailEntity>) {}
        override suspend fun upsertWithMerge(entity: EmailEntity): EmailEntity = entity
        override suspend fun upsertPreservingCachedContent(emails: List<EmailEntity>) {}
        override suspend fun sumReadyContentBytes(): Long? = 0L
        override suspend fun getLruEvictionCandidates(protectedEmailId: String): List<EmailEntity> = emptyList()
        override suspend fun getGlobalLruEvictionCandidates(): List<EmailEntity> = emptyList()
        override suspend fun clearContent(emailId: String) {}
        override suspend fun enforceContentBudget(maxBudgetBytes: Long) {}
        override suspend fun updateCleanBodyIfCurrent(
            emailId: String,
            expectedRawBody: String,
            cleanBody: String,
            cachedContentBytes: Long
        ): Int = 0
        override suspend fun updateContentLastAccess(emailId: String, newTimestamp: Long) {}
        override suspend fun getMaxContentLastAccess(): Long? = 0L
        override suspend fun recordContentAccess(emailId: String) {}

        override suspend fun updateBodyAndPdfMetadata(
            emailId: String,
            body: String,
            cleanBody: String,
            pdfAttachmentsJson: String,
            hasAttachments: Boolean,
            contentState: String,
            bodyKind: String,
            inlineReferencesJson: String,
            cachedContentBytes: Long
        ) {
            lastUpdateBodyAndPdf = mapOf(
                "emailId" to emailId,
                "body" to body,
                "cleanBody" to cleanBody,
                "pdfAttachmentsJson" to pdfAttachmentsJson,
                "hasAttachments" to hasAttachments,
                "contentState" to contentState,
                "bodyKind" to bodyKind,
                "inlineReferencesJson" to inlineReferencesJson,
                "cachedContentBytes" to cachedContentBytes
            )
        }

        override suspend fun applyLruAndSaveContent(
            emailId: String,
            body: String,
            cleanBody: String,
            pdfAttachmentsJson: String,
            hasAttachments: Boolean,
            contentState: String,
            bodyKind: String,
            inlineReferencesJson: String,
            cachedContentBytes: Long,
            maxBudgetBytes: Long
        ) {
            lastApplyLru = mapOf(
                "emailId" to emailId,
                "body" to body,
                "cleanBody" to cleanBody,
                "pdfAttachmentsJson" to pdfAttachmentsJson,
                "hasAttachments" to hasAttachments,
                "contentState" to contentState,
                "bodyKind" to bodyKind,
                "inlineReferencesJson" to inlineReferencesJson,
                "cachedContentBytes" to cachedContentBytes,
                "maxBudgetBytes" to maxBudgetBytes
            )
        }
    }

    private class FakeProvider(val result: Email) : EmailProvider {
        override suspend fun fetchInbox(pageToken: String?): PaginatedResult<Email> = throw NotImplementedError()
        override suspend fun fetchTrash(pageToken: String?): PaginatedResult<Email> = throw NotImplementedError()
        override suspend fun search(query: String, pageToken: String?): PaginatedResult<Email> = throw NotImplementedError()
        override suspend fun moveToTrash(emailId: String) {}
        override suspend fun restoreFromTrash(emailId: String) {}
        override suspend fun deletePermanently(emailId: String) {}
        override suspend fun markAsRead(emailId: String) {}
        override suspend fun fetchEmailById(emailId: String): EmailLookupResult = EmailLookupResult.Found(result)
        override suspend fun getUserEmail(): String? = null
        override suspend fun downloadAttachment(emailId: String, attachmentId: String): ByteArray = throw NotImplementedError()
        override suspend fun sendEmail(to: String, cc: String?, bcc: String?, subject: String, body: String, replyContext: ReplyContext?) {}
        override suspend fun downloadInlineImages(emailId: String, refs: List<com.david.mailapp.domain.model.EmailInlineReference>): Map<String, String> = emptyMap()
    }

    @Test
    fun oversizedPayload_returnsMemoryOnly_andClearsDatabaseContent() = runTest {
        val oversizedBody = "a".repeat(53 * 1024 * 1024)
        val fetchResult = fullEmail(
            id = "e1",
            body = oversizedBody,
            contentState = EmailContentState.READY,
            bodyKind = EmailBodyKind.HTML,
            pdfAttachments = listOf(PdfAttachmentMetadata("f1.pdf", "application/pdf", "att1", 1024L, "part1"))
        )
        
        val dao = FakeDao()
        val provider = FakeProvider(fetchResult)
        val guard = SessionWriteGuardImpl()
        guard.activate()
        
        val coordinator = EmailContentCoordinator(dao, { provider }, guard)

        val outcome = coordinator.fetchAndCacheBody("e1")
        
        assertTrue("Outcome should be MemoryOnly due to budget constraint", outcome is EmailContentFetchOutcome.MemoryOnly)
        outcome as EmailContentFetchOutcome.MemoryOnly
        assertEquals(oversizedBody, outcome.remote.body)
        assertEquals(oversizedBody, outcome.cleanBody)
        
        val updateArgs = dao.lastUpdateBodyAndPdf!!
        assertEquals("e1", updateArgs["emailId"])
        assertEquals("", updateArgs["body"])
        assertEquals("", updateArgs["cleanBody"])
        assertTrue((updateArgs["pdfAttachmentsJson"] as String).contains("f1.pdf"))
        assertEquals(true, updateArgs["hasAttachments"])
        assertEquals("NOT_FETCHED", updateArgs["contentState"])
        assertEquals("UNKNOWN", updateArgs["bodyKind"])
        assertEquals("[]", updateArgs["inlineReferencesJson"])
        assertEquals(0L, updateArgs["cachedContentBytes"])
    }

    @Test
    fun undersizedPayload_returnsPersisted_andAppliesLru() = runTest {
        val validBody = "a".repeat(10 * 1024 * 1024)
        val fetchResult = fullEmail(
            id = "e2",
            body = validBody,
            contentState = EmailContentState.READY,
            bodyKind = EmailBodyKind.HTML,
            pdfAttachments = emptyList()
        )
        
        val dao = FakeDao()
        val provider = FakeProvider(fetchResult)
        val guard = SessionWriteGuardImpl()
        guard.activate()
        
        val coordinator = EmailContentCoordinator(dao, { provider }, guard)

        val outcome = coordinator.fetchAndCacheBody("e2")
        
        assertTrue("Outcome should be Persisted", outcome is EmailContentFetchOutcome.Persisted)
        outcome as EmailContentFetchOutcome.Persisted
        assertEquals(validBody, outcome.remote.body)
        
        val lruArgs = dao.lastApplyLru!!
        assertEquals("e2", lruArgs["emailId"])
        assertEquals(validBody, lruArgs["body"])
        assertEquals(validBody, lruArgs["cleanBody"])
        assertEquals("[]", lruArgs["pdfAttachmentsJson"])
        assertEquals(false, lruArgs["hasAttachments"])
        assertEquals("READY", lruArgs["contentState"])
        assertEquals("HTML", lruArgs["bodyKind"])
        assertEquals("[]", lruArgs["inlineReferencesJson"])
        assertEquals(validBody.toByteArray(Charsets.UTF_8).size.toLong() * 2 + 2L, lruArgs["cachedContentBytes"])
        assertEquals(52_428_800L, lruArgs["maxBudgetBytes"])
    }

    private fun fullEmail(
        id: String,
        body: String,
        contentState: EmailContentState,
        bodyKind: EmailBodyKind,
        pdfAttachments: List<PdfAttachmentMetadata>
    ) = Email(
        id = id,
        threadId = "thread-$id",
        from = "sender@example.com",
        fromInitials = "S",
        to = "recipient@example.com",
        subject = "subject",
        snippet = "snippet",
        timestamp = 1L,
        isRead = true,
        isStarred = false,
        hasAttachments = pdfAttachments.isNotEmpty(),
        labels = listOf("INBOX"),
        folder = com.david.mailapp.domain.model.EmailFolder.Inbox,
        body = body,
        pdfAttachments = pdfAttachments,
        pdfMetadataScanned = true,
        contentState = contentState,
        bodyKind = bodyKind
    )
}
