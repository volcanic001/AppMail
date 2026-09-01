package com.david.mailapp.data.repository

import android.util.Log
import com.david.mailapp.core.session.SessionWriteGuard
import com.david.mailapp.data.local.converter.PdfAttachmentMetadataCodec
import com.david.mailapp.data.local.dao.EmailDao
import com.david.mailapp.data.remote.provider.BodyFetchResult
import com.david.mailapp.data.remote.provider.EmailProvider
import com.david.mailapp.feature.emaildetail.components.EmailHtmlCleaner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class EmailContentCoordinator(
    private val dao: EmailDao,
    private val providerFactory: () -> EmailProvider?,
    private val writeGuard: SessionWriteGuard
) {
    /** Fetch the full HTML body along with inline image refs and PDF metadata from the provider,
     * then persist everything atomically to Room. Metadata is persisted even when the body is empty. */
    suspend fun fetchAndCacheBody(emailId: String): com.david.mailapp.data.repository.EmailContentFetchOutcome? =
        com.david.mailapp.core.perf.MailOpenPerformanceTrace.traceAsyncSection(
            com.david.mailapp.core.perf.MailOpenPerformanceTrace.SECTION_BODY_FETCH,
            emailId
        ) {
            val t0 = RepositoryTrace.now()
            Log.d(RepositoryTrace.MAIL_PERF_TAG, "[REPO_BODY] START emailId=$emailId")
            val lease = writeGuard.capture() ?: run {
                Log.d(RepositoryTrace.MAIL_PERF_TAG, "[REPO_BODY] GUARD_INVALIDATED emailId=$emailId")
                return@traceAsyncSection null
            }
            val result = providerFactory()?.fetchBodyWithRefs(emailId) ?: run {
                Log.d(RepositoryTrace.MAIL_PERF_TAG, "[REPO_BODY] NO_PROVIDER_OR_FAILED emailId=$emailId")
                return@traceAsyncSection null
            }
            val rawBody = result.rawBody.orEmpty()
            val tFetch = RepositoryTrace.now()

            val cleanBody = if (rawBody.isNotBlank()) {
                withContext(Dispatchers.Default) {
                    EmailHtmlCleaner.clean(rawBody)
                }
            } else ""

            val pdfJson = PdfAttachmentMetadataCodec.encode(result.pdfAttachments)
            val hasAtt = result.pdfAttachments.isNotEmpty()
            val inlineRefsJson = com.david.mailapp.data.local.converter.InlineContentReferenceCodec.encode(result.inlineRefs)
            
            val isRemoteEmpty = result.contentState == com.david.mailapp.domain.model.EmailContentState.EMPTY
            val cachedContentBytes = if (isRemoteEmpty) 0L else {
                rawBody.toByteArray(Charsets.UTF_8).size.toLong() +
                cleanBody.toByteArray(Charsets.UTF_8).size.toLong() +
                inlineRefsJson.toByteArray(Charsets.UTF_8).size.toLong()
            }

            val MAX_BUDGET_BYTES = 52_428_800L
            val isOversized = cachedContentBytes > MAX_BUDGET_BYTES

            var commitSuccess = false
            withContext(Dispatchers.IO) {
                if (isOversized) {
                    val result = writeGuard.commit(lease) {
                        dao.updateBodyAndPdfMetadata(
                            emailId = emailId,
                            body = "",
                            cleanBody = "",
                            pdfAttachmentsJson = pdfJson,
                            hasAttachments = hasAtt,
                            contentState = com.david.mailapp.domain.model.EmailContentState.NOT_FETCHED.name,
                            bodyKind = com.david.mailapp.domain.model.EmailBodyKind.UNKNOWN.name,
                            inlineReferencesJson = "[]",
                            cachedContentBytes = 0L
                        )
                    }
                    commitSuccess = result != null
                } else {
                    val result = writeGuard.commit(lease) {
                        dao.applyLruAndSaveContent(
                            emailId = emailId,
                            body = rawBody,
                            cleanBody = cleanBody,
                            pdfAttachmentsJson = pdfJson,
                            hasAttachments = hasAtt,
                            contentState = result.contentState.name,
                            bodyKind = result.bodyKind.name,
                            inlineReferencesJson = inlineRefsJson,
                            cachedContentBytes = cachedContentBytes,
                            maxBudgetBytes = MAX_BUDGET_BYTES
                        )
                    }
                    commitSuccess = result != null
                }
            }

            if (!commitSuccess) {
                Log.d(RepositoryTrace.MAIL_PERF_TAG, "[REPO_BODY] COMMIT_REJECTED emailId=$emailId")
                return@traceAsyncSection null
            }

            Log.d(RepositoryTrace.MAIL_PERF_TAG, "[REPO_BODY] CACHED emailId=$emailId oversized=$isOversized")
            if (isOversized) {
                com.david.mailapp.data.repository.EmailContentFetchOutcome.MemoryOnly(result, cleanBody)
            } else {
                com.david.mailapp.data.repository.EmailContentFetchOutcome.Persisted(result)
            }
        }

    suspend fun downloadInlineImages(emailId: String, refs: List<com.david.mailapp.domain.model.EmailInlineReference>): Map<String, String> {
        if (refs.isEmpty()) return emptyMap()
        // DEBUG_PERF
        val t0 = RepositoryTrace.now()
        Log.d(RepositoryTrace.MAIL_PERF_TAG, "[REPO_INLINE] START emailId=$emailId count=${refs.size}")
        val result = providerFactory()?.downloadInlineImages(emailId, refs) ?: emptyMap()
        Log.d(RepositoryTrace.MAIL_PERF_TAG, "[REPO_INLINE] DONE emailId=$emailId count=${result.size} totalMs=${RepositoryTrace.now() - t0}")
        return result
    }

    fun injectInlineImages(html: String, inlineImages: Map<String, String>): String {
        if (inlineImages.isEmpty()) {
            // DEBUG_PERF
            Log.d(RepositoryTrace.MAIL_PERF_TAG, "[REPO_INJECT] SKIP reason=no_inline_images htmlLen=${html.length}")
            return html
        }
        // DEBUG_PERF
        val t0 = RepositoryTrace.now()
        Log.d(RepositoryTrace.MAIL_PERF_TAG, "[REPO_INJECT] START htmlLen=${html.length} imageCount=${inlineImages.size}")
        var result = html
        for ((cid, dataUri) in inlineImages) {
            result = result
                .replace("cid:$cid", dataUri)
                .replace("cid:&lt;$cid&gt;", dataUri)
                .replace("cid:<$cid>", dataUri)
        }
        Log.d(RepositoryTrace.MAIL_PERF_TAG, "[REPO_INJECT] DONE outputLen=${result.length} durationMs=${RepositoryTrace.now() - t0}")
        return result
    }

    suspend fun recordContentAccess(emailId: String) {
        withContext(Dispatchers.IO) {
            val lease = writeGuard.capture() ?: return@withContext
            writeGuard.commit(lease) {
                dao.recordContentAccess(emailId)
            }
        }
    }
}
