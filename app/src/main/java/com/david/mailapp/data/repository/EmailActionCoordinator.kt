package com.david.mailapp.data.repository

import android.util.Log
import com.david.mailapp.core.localization.UiErrorReason
import com.david.mailapp.core.localization.toUiErrorReason
import com.david.mailapp.core.session.SessionWriteGuard
import com.david.mailapp.core.session.SessionWriteLease
import com.david.mailapp.data.local.dao.EmailDao
import com.david.mailapp.data.local.entity.EmailEntity
import com.david.mailapp.data.remote.provider.EmailProvider
import com.david.mailapp.domain.model.EmailFolder
import kotlinx.coroutines.CancellationException

internal class EmailActionCoordinator(
    private val dao: EmailDao,
    private val providerFactory: () -> EmailProvider?,
    private val writeGuard: SessionWriteGuard
) {
    suspend fun moveToTrash(emailId: String): EmailActionResult {
        val lease = writeGuard.capture() ?: return EmailActionResult.Failure(
            UiErrorReason.NO_ACTIVE_ACCOUNT, remoteApplied = false)
        val p = providerFactory() ?: return EmailActionResult.Failure(
            UiErrorReason.NO_ACTIVE_ACCOUNT, remoteApplied = false)

        // 1. Remote first
        try {
            p.moveToTrash(emailId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return EmailActionResult.Failure(e.toUiErrorReason(), remoteApplied = false)
        }

        // 2. Local write with exception/rejection handling
        return commitWithReconcile(lease, p, folders = listOf("inbox", "trash")) {
            dao.moveToFolder(emailId, "trash")
        }
    }

    suspend fun restoreFromTrash(emailId: String): EmailActionResult {
        val lease = writeGuard.capture() ?: return EmailActionResult.Failure(
            UiErrorReason.NO_ACTIVE_ACCOUNT, remoteApplied = false)
        val p = providerFactory() ?: return EmailActionResult.Failure(
            UiErrorReason.NO_ACTIVE_ACCOUNT, remoteApplied = false)

        try {
            p.restoreFromTrash(emailId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return EmailActionResult.Failure(e.toUiErrorReason(), remoteApplied = false)
        }

        return commitWithReconcile(lease, p, folders = listOf("trash", "inbox")) {
            dao.moveToFolder(emailId, "inbox")
        }
    }

    suspend fun deletePermanently(emailId: String): EmailActionResult {
        val lease = writeGuard.capture() ?: return EmailActionResult.Failure(
            UiErrorReason.NO_ACTIVE_ACCOUNT, remoteApplied = false)
        val p = providerFactory() ?: return EmailActionResult.Failure(
            UiErrorReason.NO_ACTIVE_ACCOUNT, remoteApplied = false)

        try {
            p.deletePermanently(emailId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return EmailActionResult.Failure(e.toUiErrorReason(), remoteApplied = false)
        }

        return commitWithReconcile(lease, p, folders = listOf("trash")) {
            dao.deleteById(emailId)
        }
    }

    suspend fun markAsRead(emailId: String): EmailActionResult {
        val lease = writeGuard.capture() ?: return EmailActionResult.Failure(
            UiErrorReason.NO_ACTIVE_ACCOUNT, remoteApplied = false)
        val p = providerFactory() ?: return EmailActionResult.Failure(
            UiErrorReason.NO_ACTIVE_ACCOUNT, remoteApplied = false)

        try {
            p.markAsRead(emailId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return EmailActionResult.Failure(e.toUiErrorReason(), remoteApplied = false)
        }

        return commitWithReconcile(lease, p, folders = listOf("inbox", "trash")) {
            dao.updateReadStatus(emailId, isRead = true)
        }
    }

    // ── Commit helper (best-effort reconciliation on local failure) ──

    /**
     * Attempts the local [block] via [writeGuard.commit].
     *
     * - Successful commit → [EmailActionResult.Success].
     * - Null/exception commit → reconciliation for [folders] → Failure(UNKNOWN, true).
     * - CancellationException during commit → rethrown (not reconciled).
     * - CancellationException during reconciliation → rethrown.
     */
    private suspend fun commitWithReconcile(
        lease: SessionWriteLease,
        provider: EmailProvider,
        folders: List<String>,
        block: suspend () -> Unit
    ): EmailActionResult {
        val commitResult = try {
            writeGuard.commit(lease) { block(); true }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(RepositoryTrace.MAIL_PERF_TAG, "Local commit failed after remote success", e)
            null
        }

        if (commitResult == true) return EmailActionResult.Success

        // Reconcile in folder order; each folder in its own try
        for (folder in folders) {
            try {
                reconcileFolder(provider, lease, folder)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(RepositoryTrace.MAIL_PERF_TAG, "Reconcile $folder failed after remote success", e)
            }
        }

        return EmailActionResult.Failure(UiErrorReason.UNKNOWN, remoteApplied = true)
    }

    private suspend fun reconcileFolder(
        p: EmailProvider,
        lease: SessionWriteLease,
        folder: String
    ) {
        val result = when (folder) {
            "inbox" -> p.fetchInbox(null)
            "trash" -> p.fetchTrash(null)
            else -> return
        }
        writeGuard.commit(lease) {
            val entities = result.items.map {
                EmailEntity.fromDomain(it, if (folder == "inbox") EmailFolder.Inbox else EmailFolder.Trash)
            }
            if (result.isComplete) {
                dao.replaceFolder(folder, entities)
            } else {
                dao.upsertPreservingCachedContent(entities)
            }
        }
    }
}
