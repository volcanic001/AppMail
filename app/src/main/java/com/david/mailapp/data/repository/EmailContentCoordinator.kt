package com.david.mailapp.data.repository

import android.util.Log
import com.david.mailapp.core.session.SessionWriteGuard
import com.david.mailapp.data.local.dao.EmailDao
import com.david.mailapp.data.local.entity.EmailEntity
import com.david.mailapp.data.remote.provider.EmailProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class EmailContentCoordinator(
    private val dao: EmailDao,
    private val providerFactory: () -> EmailProvider?,
    private val remoteRecovery: EmailRemoteRecoveryCoordinator,
    private val writeGuard: SessionWriteGuard
) {
    private data class CachedRead(val entity: EmailEntity?)

    suspend fun recoverContentById(emailId: String): EmailContentRecoveryResult =
        com.david.mailapp.core.perf.MailOpenPerformanceTrace.traceAsyncSection<EmailContentRecoveryResult>(
            com.david.mailapp.core.perf.MailOpenPerformanceTrace.SECTION_BODY_FETCH,
            emailId
        ) {
            if (emailId.isBlank()) {
                return@traceAsyncSection EmailContentRecoveryResult.Failure(EmailResolutionFailureReason.INVALID_ID)
            }
            val lease = writeGuard.capture() ?: return@traceAsyncSection EmailContentRecoveryResult.Failure(
                EmailResolutionFailureReason.NO_ACTIVE_ACCOUNT
            )
            val read = try {
                writeGuard.commit(lease) { CachedRead(dao.getByIdOnce(emailId)) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                return@traceAsyncSection EmailContentRecoveryResult.Failure(EmailResolutionFailureReason.LOCAL_READ_FAILED)
            } ?: return@traceAsyncSection EmailContentRecoveryResult.Failure(EmailResolutionFailureReason.SESSION_CHANGED)

            val cached = read.entity?.toDomain()
            if (cached?.hasCompleteCachedContent() == true) {
                return@traceAsyncSection EmailContentRecoveryResult.Found(cached, EmailContentStorage.PERSISTED)
            }
            remoteRecovery.recover(emailId, lease)
        }

    suspend fun downloadInlineImages(
        emailId: String,
        refs: List<com.david.mailapp.domain.model.EmailInlineReference>
    ): Map<String, String> {
        if (refs.isEmpty()) return emptyMap()
        val deduplicatedRefs = refs.distinctBy { it.attachmentId to it.contentId }
        val startedAt = RepositoryTrace.now()
        Log.d(RepositoryTrace.MAIL_PERF_TAG, "[REPO_INLINE] START emailId=$emailId count=${deduplicatedRefs.size}")
        val result = providerFactory()?.downloadInlineImages(emailId, deduplicatedRefs) ?: emptyMap()
        Log.d(RepositoryTrace.MAIL_PERF_TAG, "[REPO_INLINE] DONE emailId=$emailId count=${result.size} totalMs=${RepositoryTrace.now() - startedAt}")
        return result
    }

    fun injectInlineImages(html: String, inlineImages: Map<String, String>): String {
        if (inlineImages.isEmpty()) return html
        var result = html
        for ((cid, dataUri) in inlineImages) {
            result = result
                .replace("cid:&lt;$cid&gt;", dataUri, ignoreCase = true)
                .replace("cid:<$cid>", dataUri, ignoreCase = true)
                .replace("cid:$cid", dataUri, ignoreCase = true)
        }
        return result
    }

    suspend fun recordContentAccess(emailId: String) {
        withContext(Dispatchers.IO) {
            val lease = writeGuard.capture() ?: return@withContext
            writeGuard.commit(lease) { dao.recordContentAccess(emailId) }
        }
    }
}
