package com.david.mailapp.data.repository

import com.david.mailapp.data.local.dao.EmailDao
import com.david.mailapp.data.local.entity.EmailEntity
import com.david.mailapp.core.session.SessionWriteGuard
import com.david.mailapp.data.remote.provider.EmailProvider
import com.david.mailapp.domain.model.Email
import com.david.mailapp.domain.model.EmailFolder
import com.david.mailapp.domain.model.PaginatedResult
import com.david.mailapp.data.cleaner.EmailHtmlCleaner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Live Room-backed reads and refresh coordination for mailbox folders.
 * Delegates only to the DAO and provider factory; no cache, content or PDF
 * responsibilities.
 */
internal class EmailMailboxCoordinator(
    private val dao: EmailDao,
    private val providerFactory: () -> EmailProvider?,
    private val writeGuard: SessionWriteGuard
) {
    // ── Coordinator instances ────────────────────────────────────

    private val inboxCommitCoordinator = FolderCommitCoordinator()
    private val trashCommitCoordinator = FolderCommitCoordinator()

    // ── Reactive reads ───────────────────────────────────────────

    fun getInbox(): Flow<List<Email>> {
        return dao.observeSummariesByFolder("inbox").map { projections ->
            projections.map { it.toDomain() }
        }
    }

    fun getTrash(): Flow<List<Email>> {
        return dao.observeSummariesByFolder("trash").map { projections ->
            projections.map { it.toDomain() }
        }
    }

    fun getEmailById(emailId: String): Flow<Email?> {
        return dao.getById(emailId).map { entity -> entity?.toDomain() }
    }

    // ── Refresh ──────────────────────────────────────────────────

    /** Fetch from provider and persist to Room. Returns the paginated result for UI pagination. */
    suspend fun refreshInbox(pageToken: String?): PaginatedResult<Email> {
        val gen = if (pageToken == null) {
            inboxCommitCoordinator.nextGeneration()
        } else {
            inboxCommitCoordinator.currentGeneration()
        }

        val lease = writeGuard.capture() ?: return PaginatedResult(emptyList(), null)
        val p = providerFactory() ?: return PaginatedResult(emptyList(), null)
        val fetched = p.fetchInbox(pageToken)
        val result = if (fetched.isComplete) fetched else fetched.copy(nextPageToken = null)

        persistMailboxPage(
            folder = EmailFolder.Inbox,
            pageToken = pageToken,
            result = result,
            generation = gen,
            commitCoordinator = inboxCommitCoordinator,
            lease = lease
        )

        return result
    }

    suspend fun refreshTrash(pageToken: String?): PaginatedResult<Email> {
        val gen = if (pageToken == null) {
            trashCommitCoordinator.nextGeneration()
        } else {
            trashCommitCoordinator.currentGeneration()
        }

        val lease = writeGuard.capture() ?: return PaginatedResult(emptyList(), null)
        val p = providerFactory() ?: return PaginatedResult(emptyList(), null)
        val fetched = p.fetchTrash(pageToken)
        val result = if (fetched.isComplete) fetched else fetched.copy(nextPageToken = null)

        persistMailboxPage(
            folder = EmailFolder.Trash,
            pageToken = pageToken,
            result = result,
            generation = gen,
            commitCoordinator = trashCommitCoordinator,
            lease = lease
        )

        return result
    }

    private suspend fun persistMailboxPage(
        folder: EmailFolder,
        pageToken: String?,
        result: PaginatedResult<Email>,
        generation: Long,
        commitCoordinator: FolderCommitCoordinator,
        lease: com.david.mailapp.core.session.SessionWriteLease
    ) {
        val syncEmails = result.items.map(Email::materializeForMailboxSync)
        val entities = syncEmails.map { EmailEntity.fromDomain(it, folder) }
        val folderName = folder.name.lowercase()
        var sessionCommitted = false

        val generationAccepted = commitCoordinator.commitIfValid(generation) {
            sessionCommitted = writeGuard.commit(lease) {
                if (pageToken == null && result.isComplete) {
                    dao.replaceFolder(folderName, entities)
                } else {
                    dao.upsertPreservingCachedContent(entities)
                }
                dao.enforceContentBudget(EMAIL_CONTENT_CACHE_BUDGET_BYTES)
                true
            } == true
        }
        if (!generationAccepted || !sessionCommitted) return

        val cleanedContent = withContext(Dispatchers.Default) {
            syncEmails.mapNotNull { email ->
                email.toCleanedSyncContent(EmailHtmlCleaner::clean)
            }
        }
        if (cleanedContent.isEmpty()) return

        commitCoordinator.commitIfValid(generation) {
            writeGuard.commit(lease) {
                cleanedContent.forEach { cleaned ->
                    dao.updateCleanBodyIfCurrent(
                        emailId = cleaned.emailId,
                        expectedRawBody = cleaned.expectedRawBody,
                        cleanBody = cleaned.cleanBody,
                        cachedContentBytes = cleaned.cachedContentBytes
                    )
                }
                dao.enforceContentBudget(EMAIL_CONTENT_CACHE_BUDGET_BYTES)
            }
        }
    }
}
