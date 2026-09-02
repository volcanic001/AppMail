package com.david.mailapp.data.repository

import android.util.Log
import com.david.mailapp.core.network.OAuthSessionExpiredException
import com.david.mailapp.core.session.SessionWriteGuard
import com.david.mailapp.core.session.SessionWriteLease
import com.david.mailapp.data.local.dao.EmailDao
import com.david.mailapp.data.local.entity.EmailEntity
import com.david.mailapp.data.remote.provider.EmailLookupFailureReason
import com.david.mailapp.data.remote.provider.EmailLookupResult
import com.david.mailapp.data.remote.provider.EmailProvider
import com.david.mailapp.data.cleaner.EmailHtmlCleaner
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class EmailRemoteRecoveryCoordinator(
    private val dao: EmailDao,
    private val providerFactory: () -> EmailProvider?,
    private val writeGuard: SessionWriteGuard
) {
    private val pending = ConcurrentHashMap<Pair<Long, String>, CompletableDeferred<EmailContentRecoveryResult>>()
    private data class CachedRead(val entity: EmailEntity?)

    suspend fun recover(emailId: String, lease: SessionWriteLease): EmailContentRecoveryResult {
        val key = lease.generation to emailId
        val leader = CompletableDeferred<EmailContentRecoveryResult>()
        val active = pending.putIfAbsent(key, leader)
        if (active != null) return active.await()

        return try {
            val result = recoverAsLeader(emailId, lease)
            leader.complete(result)
            result
        } catch (error: CancellationException) {
            leader.cancel(error)
            throw error
        } catch (error: Exception) {
            val result = EmailContentRecoveryResult.Failure(EmailResolutionFailureReason.INVALID_RESPONSE)
            leader.complete(result)
            result
        } finally {
            pending.remove(key, leader)
        }
    }

    private suspend fun recoverAsLeader(emailId: String, lease: SessionWriteLease): EmailContentRecoveryResult {
        val startedAt = RepositoryTrace.now()
        val provider = providerFactory()
            ?: return failure(emailId, startedAt, EmailResolutionFailureReason.NO_ACTIVE_ACCOUNT)
        val lookup = try {
            provider.fetchEmailById(emailId)
        } catch (error: CancellationException) {
            throw error
        } catch (error: OAuthSessionExpiredException) {
            EmailLookupResult.Failure(EmailLookupFailureReason.SESSION_EXPIRED)
        } catch (error: IOException) {
            EmailLookupResult.Failure(EmailLookupFailureReason.NO_CONNECTION)
        } catch (error: Exception) {
            EmailLookupResult.Failure(EmailLookupFailureReason.INVALID_RESPONSE)
        }

        return when (lookup) {
            EmailLookupResult.NotFound -> {
                log(emailId, startedAt, "NOT_FOUND")
                EmailContentRecoveryResult.NotFound
            }
            is EmailLookupResult.Failure -> failure(emailId, startedAt, lookup.reason.toResolutionReason())
            is EmailLookupResult.Found -> persistFound(emailId, lease, lookup.email, startedAt)
        }
    }

    private suspend fun persistFound(
        emailId: String,
        lease: SessionWriteLease,
        remote: com.david.mailapp.domain.model.Email,
        startedAt: Long
    ): EmailContentRecoveryResult {
        if (remote.id != emailId) {
            return failure(emailId, startedAt, EmailResolutionFailureReason.INVALID_RESPONSE)
        }
        if (!remote.pdfMetadataScanned) {
            return recoverConcurrentCommit(emailId, lease, startedAt)
        }
        val materialized = withContext(Dispatchers.Default) {
            remote.materializeForIndividualRecovery(EmailHtmlCleaner::clean)
        } ?: return failure(emailId, startedAt, EmailResolutionFailureReason.INVALID_RESPONSE)

        return try {
            val persisted = writeGuard.commit(lease) {
                dao.upsertRecoveredEmailAndEnforceBudget(
                    EmailEntity.fromDomain(materialized.persistable, materialized.persistable.folder),
                    EMAIL_CONTENT_CACHE_BUDGET_BYTES
                )
            } ?: return failure(emailId, startedAt, EmailResolutionFailureReason.SESSION_CHANGED)

            log(emailId, startedAt, "FOUND_${materialized.storage.name}")
            EmailContentRecoveryResult.Found(
                email = if (materialized.storage == EmailContentStorage.MEMORY_ONLY) materialized.display else persisted.toDomain(),
                storage = materialized.storage
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            failure(emailId, startedAt, EmailResolutionFailureReason.LOCAL_WRITE_FAILED)
        }
    }

    private suspend fun recoverConcurrentCommit(
        emailId: String,
        lease: SessionWriteLease,
        startedAt: Long
    ): EmailContentRecoveryResult {
        return try {
            val read = writeGuard.commit(lease) { CachedRead(dao.getByIdOnce(emailId)) }
                ?: return failure(emailId, startedAt, EmailResolutionFailureReason.SESSION_CHANGED)
            val cached = read.entity
                ?: return failure(emailId, startedAt, EmailResolutionFailureReason.INVALID_RESPONSE)
            val email = cached.toDomain()
            if (email.hasCompleteCachedContent()) {
                log(emailId, startedAt, "FOUND_CONCURRENT_CACHE")
                EmailContentRecoveryResult.Found(email, EmailContentStorage.PERSISTED)
            } else {
                failure(emailId, startedAt, EmailResolutionFailureReason.INVALID_RESPONSE)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            failure(emailId, startedAt, EmailResolutionFailureReason.LOCAL_READ_FAILED)
        }
    }

    private fun failure(emailId: String, startedAt: Long, reason: EmailResolutionFailureReason): EmailContentRecoveryResult.Failure {
        log(emailId, startedAt, reason.name)
        return EmailContentRecoveryResult.Failure(reason)
    }

    private fun log(emailId: String, startedAt: Long, category: String) {
        Log.d(RepositoryTrace.RESOLVE_TAG, "[REMOTE_RECOVERY] RESULT id=$emailId durationMs=${RepositoryTrace.now() - startedAt} category=$category")
    }
}

internal fun EmailLookupFailureReason.toResolutionReason(): EmailResolutionFailureReason = when (this) {
    EmailLookupFailureReason.NO_CONNECTION -> EmailResolutionFailureReason.NO_CONNECTION
    EmailLookupFailureReason.SESSION_EXPIRED -> EmailResolutionFailureReason.SESSION_EXPIRED
    EmailLookupFailureReason.TEMPORARY_REMOTE -> EmailResolutionFailureReason.TEMPORARY_REMOTE
    EmailLookupFailureReason.REMOTE_REJECTED -> EmailResolutionFailureReason.REMOTE_REJECTED
    EmailLookupFailureReason.INVALID_RESPONSE -> EmailResolutionFailureReason.INVALID_RESPONSE
}
