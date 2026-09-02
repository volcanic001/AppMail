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
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred

internal class EmailResolutionCoordinator(
    private val dao: EmailDao,
    private val providerFactory: () -> EmailProvider?,
    private val writeGuard: SessionWriteGuard
) {
    /** Single-flight pending resolutions keyed by (sessionGeneration, emailId). Cleaned up on completion, cancellation, or session change. */
    private val pendingResolutions = ConcurrentHashMap<Pair<Long, String>, CompletableDeferred<EmailResolutionResult>>()

    /** Wrapper so writeGuard.commit(null) ≠ commit(read=null) — distinguishes session-changed from no-row. */
    private data class CachedRead(val entity: EmailEntity?)

    /**
     * Resolves an email by id: cache-first, then remote via [EmailProvider.fetchEmailById],
     * then persistence to Room.  The local read is executed inside [writeGuard.commit]
     * so stale cache can never be delivered after a session change.
     *
     * Single-flight per (sessionGeneration, id): concurrent calls for the same id
     * within the same session share one resolution. Cancellation of a follower does
     * not cancel the leader; cancellation of the leader cleans the flight entry and
     * allows a later retry. A new session never joins a flight from a prior session.
     */
    suspend fun resolveEmailById(emailId: String): EmailResolutionResult =
        com.david.mailapp.core.perf.MailOpenPerformanceTrace.traceAsyncSection(
            com.david.mailapp.core.perf.MailOpenPerformanceTrace.SECTION_RESOLVE,
            emailId
        ) {
            val t0 = RepositoryTrace.now()

            if (emailId.isBlank()) {
                logResolve(emailId, null, t0, "INVALID_ID")
                return@traceAsyncSection EmailResolutionResult.Failure(EmailResolutionFailureReason.INVALID_ID)
            }

            val lease = writeGuard.capture()
            if (lease == null) {
                logResolve(emailId, null, t0, "NO_ACTIVE_ACCOUNT")
                return@traceAsyncSection EmailResolutionResult.Failure(EmailResolutionFailureReason.NO_ACTIVE_ACCOUNT)
            }

            val flightKey = lease.generation to emailId

            // Single-flight: atomically register or join an existing flight
            val newDeferred = CompletableDeferred<EmailResolutionResult>()
            val existing = pendingResolutions.putIfAbsent(flightKey, newDeferred)

            if (existing != null) {
                // Follower — wait on the leader's deferred (own cancellation does not cancel the leader)
                logResolve(emailId, null, t0, "JOIN_SINGLE_FLIGHT")
                return@traceAsyncSection try {
                    existing.await()
                } catch (e: CancellationException) {
                    throw e
                }
            }

            // Leader
            try {
                val result = resolveInternal(emailId, lease, t0)
                newDeferred.complete(result)
                result
            } catch (e: CancellationException) {
                newDeferred.cancel(e)
                throw e
            } catch (e: Exception) {
                val failure = EmailResolutionResult.Failure(EmailResolutionFailureReason.INVALID_RESPONSE)
                newDeferred.complete(failure)
                failure
            } finally {
                pendingResolutions.remove(flightKey, newDeferred)
            }
        }

    private suspend fun resolveInternal(
        emailId: String,
        lease: SessionWriteLease,
        t0: Long
    ): EmailResolutionResult {
        // Guarded local read: commit validates the lease; returns null → session changed
        val read = try {
            writeGuard.commit(lease) {
                CachedRead(dao.getByIdOnce(emailId))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logResolve(emailId, null, t0, "LOCAL_READ_FAILED")
            return EmailResolutionResult.Failure(EmailResolutionFailureReason.LOCAL_READ_FAILED)
        }

        if (read == null) {
            logResolve(emailId, null, t0, "SESSION_CHANGED")
            return EmailResolutionResult.Failure(EmailResolutionFailureReason.SESSION_CHANGED)
        }

        val cached = read.entity
        if (cached != null) {
            logResolve(emailId, "cache", t0, "FOUND")
            return EmailResolutionResult.Found(cached.toDomain())
        }

        // Cache miss → remote
        val provider = providerFactory()
        if (provider == null) {
            logResolve(emailId, "remote", t0, "NO_ACTIVE_ACCOUNT")
            return EmailResolutionResult.Failure(EmailResolutionFailureReason.NO_ACTIVE_ACCOUNT)
        }

        val lookupResult = try {
            provider.fetchEmailById(emailId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: OAuthSessionExpiredException) {
            EmailLookupResult.Failure(EmailLookupFailureReason.SESSION_EXPIRED)
        } catch (e: IOException) {
            EmailLookupResult.Failure(EmailLookupFailureReason.NO_CONNECTION)
        } catch (e: Exception) {
            EmailLookupResult.Failure(EmailLookupFailureReason.INVALID_RESPONSE)
        }

        return when (lookupResult) {
            is EmailLookupResult.NotFound -> {
                logResolve(emailId, "remote", t0, "NOT_FOUND")
                EmailResolutionResult.NotFound
            }
            is EmailLookupResult.Failure -> {
                val reason = mapLookupFailure(lookupResult.reason)
                logResolve(emailId, "remote", t0, lookupResult.reason.name)
                EmailResolutionResult.Failure(reason)
            }
            is EmailLookupResult.Found -> {
                val email = lookupResult.email.asSummaryOnly()
                val entity = EmailEntity.fromDomain(email, email.folder)
                try {
                    val persisted = writeGuard.commit(lease) {
                        dao.upsertWithMerge(entity)
                    }
                    if (persisted == null) {
                        logResolve(emailId, "remote", t0, "SESSION_CHANGED")
                        EmailResolutionResult.Failure(EmailResolutionFailureReason.SESSION_CHANGED)
                    } else {
                        logResolve(emailId, "remote", t0, "FOUND")
                        EmailResolutionResult.Found(persisted.toDomain())
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logResolve(emailId, "remote", t0, "LOCAL_WRITE_FAILED")
                    EmailResolutionResult.Failure(EmailResolutionFailureReason.LOCAL_WRITE_FAILED)
                }
            }
        }
    }

    private fun mapLookupFailure(reason: EmailLookupFailureReason): EmailResolutionFailureReason = when (reason) {
        EmailLookupFailureReason.NO_CONNECTION -> EmailResolutionFailureReason.NO_CONNECTION
        EmailLookupFailureReason.SESSION_EXPIRED -> EmailResolutionFailureReason.SESSION_EXPIRED
        EmailLookupFailureReason.TEMPORARY_REMOTE -> EmailResolutionFailureReason.TEMPORARY_REMOTE
        EmailLookupFailureReason.REMOTE_REJECTED -> EmailResolutionFailureReason.REMOTE_REJECTED
        EmailLookupFailureReason.INVALID_RESPONSE -> EmailResolutionFailureReason.INVALID_RESPONSE
    }

    private fun logResolve(emailId: String, source: String?, t0: Long, category: String) {
        Log.d(RepositoryTrace.RESOLVE_TAG, "[RESOLVE] RESULT id=$emailId source=${source ?: "-"} durationMs=${RepositoryTrace.now() - t0} category=$category")
    }
}
