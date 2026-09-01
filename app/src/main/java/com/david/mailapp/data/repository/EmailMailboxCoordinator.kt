package com.david.mailapp.data.repository

import com.david.mailapp.data.local.dao.EmailDao
import com.david.mailapp.data.local.entity.EmailEntity
import com.david.mailapp.core.session.SessionWriteGuard
import com.david.mailapp.data.remote.provider.EmailProvider
import com.david.mailapp.domain.model.Email
import com.david.mailapp.domain.model.EmailFolder
import com.david.mailapp.domain.model.PaginatedResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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

        val entities = result.items.map { EmailEntity.fromDomain(it, EmailFolder.Inbox) }
        inboxCommitCoordinator.commitIfValid(gen) {
            writeGuard.commit(lease) {
                // Replace folder only on a complete first page;
                // partial pages or pagination append/merge.
                if (pageToken == null && result.isComplete) {
                    dao.replaceFolder("inbox", entities)
                } else {
                    dao.upsertPreservingBodies(entities)
                }
            }
        }

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

        val entities = result.items.map { EmailEntity.fromDomain(it, EmailFolder.Trash) }
        trashCommitCoordinator.commitIfValid(gen) {
            writeGuard.commit(lease) {
                // Refresh replaces the paginated window only for a complete first page.
                // Partial pages and subsequent pages can only merge into the cache.
                if (pageToken == null && result.isComplete) {
                    dao.replaceFolder("trash", entities)
                } else {
                    dao.upsertPreservingBodies(entities)
                }
            }
        }

        return result
    }
}
