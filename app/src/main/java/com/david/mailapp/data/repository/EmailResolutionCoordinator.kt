package com.david.mailapp.data.repository

import android.util.Log
import com.david.mailapp.core.session.SessionWriteGuard
import com.david.mailapp.data.local.dao.EmailDao
import com.david.mailapp.data.local.entity.EmailEntity
import kotlinx.coroutines.CancellationException

internal class EmailResolutionCoordinator(
    private val dao: EmailDao,
    private val remoteRecovery: EmailRemoteRecoveryCoordinator,
    private val writeGuard: SessionWriteGuard
) {
    private data class CachedRead(val entity: EmailEntity?)

    suspend fun resolveEmailById(emailId: String): EmailResolutionResult =
        com.david.mailapp.core.perf.MailOpenPerformanceTrace.traceAsyncSection(
            com.david.mailapp.core.perf.MailOpenPerformanceTrace.SECTION_RESOLVE,
            emailId
        ) {
            val startedAt = RepositoryTrace.now()
            if (emailId.isBlank()) {
                return@traceAsyncSection failure(emailId, startedAt, EmailResolutionFailureReason.INVALID_ID)
            }
            val lease = writeGuard.capture()
                ?: return@traceAsyncSection failure(emailId, startedAt, EmailResolutionFailureReason.NO_ACTIVE_ACCOUNT)
            val read = try {
                writeGuard.commit(lease) { CachedRead(dao.getByIdOnce(emailId)) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                return@traceAsyncSection failure(emailId, startedAt, EmailResolutionFailureReason.LOCAL_READ_FAILED)
            } ?: return@traceAsyncSection failure(emailId, startedAt, EmailResolutionFailureReason.SESSION_CHANGED)

            read.entity?.let {
                log(emailId, startedAt, "FOUND_CACHE")
                return@traceAsyncSection EmailResolutionResult.Found(it.toDomain())
            }

            when (val recovered = remoteRecovery.recover(emailId, lease)) {
                is EmailContentRecoveryResult.Found -> EmailResolutionResult.Found(recovered.email)
                EmailContentRecoveryResult.NotFound -> EmailResolutionResult.NotFound
                is EmailContentRecoveryResult.Failure -> EmailResolutionResult.Failure(recovered.reason)
            }
        }

    private fun failure(emailId: String, startedAt: Long, reason: EmailResolutionFailureReason): EmailResolutionResult.Failure {
        log(emailId, startedAt, reason.name)
        return EmailResolutionResult.Failure(reason)
    }

    private fun log(emailId: String, startedAt: Long, category: String) {
        Log.d(RepositoryTrace.RESOLVE_TAG, "[RESOLVE] RESULT id=$emailId durationMs=${RepositoryTrace.now() - startedAt} category=$category")
    }
}
