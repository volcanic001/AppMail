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
import com.david.mailapp.data.remote.provider.gmail.GmailProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailContentCoordinatorBudgetTest {

    private class FakeDao : EmailDao {
        var recovered: EmailEntity? = null
        var recoveryBudget: Long? = null
        var recoveryCommits = 0
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
        override suspend fun upsertRecoveredEmailAndEnforceBudget(entity: EmailEntity, maxBudgetBytes: Long): EmailEntity {
            recoveryCommits++
            recovered = entity
            recoveryBudget = maxBudgetBytes
            return entity
        }
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
        
        val remoteRecovery = EmailRemoteRecoveryCoordinator(dao, { provider }, guard)
        val coordinator = EmailContentCoordinator(dao, { provider }, remoteRecovery, guard)

        val outcome = coordinator.recoverContentById("e1")
        
        assertTrue("Outcome should be MemoryOnly due to budget constraint", outcome is EmailContentRecoveryResult.Found)
        outcome as EmailContentRecoveryResult.Found
        assertEquals(EmailContentStorage.MEMORY_ONLY, outcome.storage)
        assertEquals(oversizedBody, outcome.email.body)
        assertEquals(oversizedBody, outcome.email.cleanBody)
        
        val persisted = dao.recovered!!
        assertEquals("e1", persisted.id)
        assertEquals("", persisted.body)
        assertEquals("", persisted.cleanBody)
        assertTrue(persisted.pdfAttachmentsJson.contains("f1.pdf"))
        assertEquals("NOT_FETCHED", persisted.contentState)
        assertEquals("UNKNOWN", persisted.bodyKind)
        assertEquals("[]", persisted.inlineReferencesJson)
        assertEquals(0L, persisted.cachedContentBytes)
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
        
        val remoteRecovery = EmailRemoteRecoveryCoordinator(dao, { provider }, guard)
        val coordinator = EmailContentCoordinator(dao, { provider }, remoteRecovery, guard)

        val outcome = coordinator.recoverContentById("e2")
        
        assertTrue("Outcome should be Persisted", outcome is EmailContentRecoveryResult.Found)
        outcome as EmailContentRecoveryResult.Found
        assertEquals(EmailContentStorage.PERSISTED, outcome.storage)
        assertEquals(validBody, outcome.email.body)
        
        val persisted = dao.recovered!!
        assertEquals("e2", persisted.id)
        assertEquals(validBody, persisted.body)
        assertEquals(validBody, persisted.cleanBody)
        assertEquals("READY", persisted.contentState)
        assertEquals("HTML", persisted.bodyKind)
        assertEquals(validBody.toByteArray(Charsets.UTF_8).size.toLong() * 2 + 2L, persisted.cachedContentBytes)
        assertEquals(52_428_800L, dao.recoveryBudget)
    }

    @Test
    fun concurrentRecovery_withKtorMockEngine_usesOneFullRequestAndOneCommit() = runTest {
        val requestStarted = CompletableDeferred<Unit>()
        val releaseResponse = CompletableDeferred<Unit>()
        var requestCount = 0
        val engine = MockEngine { request ->
            requestCount++
            assertEquals("full", request.url.parameters["format"])
            requestStarted.complete(Unit)
            releaseResponse.await()
            respond(
                content = """{
                    "id":"shared","threadId":"thread-shared","labelIds":["INBOX"],
                    "snippet":"snippet","internalDate":"1000",
                    "payload":{"mimeType":"text/plain","headers":[
                      {"name":"From","value":"sender@test.com"},
                      {"name":"To","value":"me@test.com"},
                      {"name":"Subject","value":"Shared"}
                    ],"body":{"size":5,"data":"aGVsbG8"}}
                }""".trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val dao = FakeDao()
        val guard = SessionWriteGuardImpl().also { it.activate() }
        val remoteRecovery = EmailRemoteRecoveryCoordinator(
            dao,
            { GmailProvider(client, lookupBackoffMillis = emptyList()) },
            guard
        )
        val resolution = EmailResolutionCoordinator(dao, remoteRecovery, guard)
        val content = EmailContentCoordinator(
            dao,
            { GmailProvider(client, lookupBackoffMillis = emptyList()) },
            remoteRecovery,
            guard
        )

        val first = async { resolution.resolveEmailById("shared") }
        requestStarted.await()
        val second = async { content.recoverContentById("shared") }
        yield()
        releaseResponse.complete(Unit)

        assertTrue(first.await() is EmailResolutionResult.Found)
        assertTrue(second.await() is EmailContentRecoveryResult.Found)
        assertEquals(1, requestCount)
        assertEquals(1, dao.recoveryCommits)
        assertEquals("shared", dao.recovered?.id)
        client.close()
    }

    @Test
    fun sameIdAcrossSessionGenerations_doesNotShareFlight() = runTest {
        val twoRequestsStarted = CompletableDeferred<Unit>()
        val releaseResponses = CompletableDeferred<Unit>()
        var requestCount = 0
        val engine = MockEngine {
            requestCount++
            if (requestCount == 2) twoRequestsStarted.complete(Unit)
            releaseResponses.await()
            respond(
                content = """{
                    "id":"session-mail","threadId":"thread-session","labelIds":["INBOX"],
                    "snippet":"snippet","internalDate":"1000",
                    "payload":{"mimeType":"text/plain","headers":[
                      {"name":"From","value":"sender@test.com"},
                      {"name":"To","value":"me@test.com"},
                      {"name":"Subject","value":"Session"}
                    ],"body":{"size":5,"data":"aGVsbG8"}}
                }""".trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val dao = FakeDao()
        val guard = SessionWriteGuardImpl().also { it.activate() }
        val coordinator = EmailRemoteRecoveryCoordinator(
            dao,
            { GmailProvider(client, lookupBackoffMillis = emptyList()) },
            guard
        )
        val oldLease = checkNotNull(guard.capture())
        val oldFlight = async { coordinator.recover("session-mail", oldLease) }
        yield()

        guard.invalidate()
        guard.activate()
        val newLease = checkNotNull(guard.capture())
        val newFlight = async { coordinator.recover("session-mail", newLease) }
        twoRequestsStarted.await()
        assertEquals(2, requestCount)
        releaseResponses.complete(Unit)

        val oldResult = oldFlight.await() as EmailContentRecoveryResult.Failure
        assertEquals(EmailResolutionFailureReason.SESSION_CHANGED, oldResult.reason)
        assertTrue(newFlight.await() is EmailContentRecoveryResult.Found)
        assertEquals(1, dao.recoveryCommits)
        client.close()
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
