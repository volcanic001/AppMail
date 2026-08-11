package com.david.mailapp.data.repository

import android.os.SystemClock
import android.util.Log
import com.david.mailapp.core.session.SessionWriteGuard
import com.david.mailapp.data.local.converter.PdfAttachmentMetadataCodec
import com.david.mailapp.data.local.dao.EmailDao
import com.david.mailapp.data.remote.provider.BodyFetchResult
import com.david.mailapp.data.remote.provider.EmailProvider
import com.david.mailapp.feature.emaildetail.components.EmailHtmlCleaner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val REPO_TAG = "MailPerfTrace"
private fun repoNow() = SystemClock.elapsedRealtime()

internal class EmailContentCoordinator(
    private val dao: EmailDao,
    private val providerFactory: () -> EmailProvider?,
    private val writeGuard: SessionWriteGuard
) {
    /** Fetch the full HTML body along with inline image refs and PDF metadata from the provider,
     * then persist everything atomically to Room. Metadata is persisted even when the body is empty. */
    suspend fun fetchAndCacheBody(emailId: String): BodyFetchResult? {
        // DEBUG_PERF
        val t0 = repoNow()
        Log.d(REPO_TAG, "[REPO_BODY] START emailId=$emailId")
        val lease = writeGuard.capture() ?: run {
            Log.d(REPO_TAG, "[REPO_BODY] GUARD_INVALIDATED emailId=$emailId")
            return null
        }
        val result = providerFactory()?.fetchBodyWithRefs(emailId) ?: run {
            Log.d(REPO_TAG, "[REPO_BODY] NO_PROVIDER_OR_FAILED emailId=$emailId")
            return null
        }
        val rawBody = result.rawBody.orEmpty()
        val tFetch = repoNow()
        Log.d(REPO_TAG, "[REPO_BODY] FETCHED emailId=$emailId bodyLen=${rawBody.length} refs=${result.inlineRefs.size} pdfs=${result.pdfAttachments.size} fetchMs=${tFetch - t0}")

        // Clean HTML only when there's a body
        val cleanBody = if (rawBody.isNotBlank()) {
            withContext(Dispatchers.Default) {
                EmailHtmlCleaner.clean(rawBody)
            }
        } else ""

        val pdfJson = PdfAttachmentMetadataCodec.encode(result.pdfAttachments)
        val hasAtt = result.pdfAttachments.isNotEmpty()

        writeGuard.commit(lease) {
            dao.updateBodyAndPdfMetadata(
                emailId = emailId,
                body = rawBody,
                cleanBody = cleanBody,
                pdfAttachmentsJson = pdfJson,
                hasAttachments = hasAtt
            )
        }
        Log.d(REPO_TAG, "[REPO_BODY] CACHED emailId=$emailId roomMs=${repoNow() - tFetch} totalMs=${repoNow() - t0}")
        return result
    }
}
