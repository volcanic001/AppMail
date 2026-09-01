package com.david.mailapp.feature.emaildetail

import com.david.mailapp.data.pdf.PdfDownloadState
import com.david.mailapp.data.remote.provider.BodyFetchResult
import com.david.mailapp.data.repository.EmailActionResult
import com.david.mailapp.data.repository.EmailResolutionResult
import com.david.mailapp.domain.model.Email
import com.david.mailapp.domain.model.PdfAttachmentMetadata
import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * Abstract source for the detail-screen ViewModel — isolates the ViewModel from
 * [com.david.mailapp.data.repository.EmailRepository] so every facet of the
 * detail flow can be tested with a controllable fake.
 */
interface EmailDetailEmailSource {

    /** Observe a single email by ID — reactive Room Flow. */
    fun observe(emailId: String): Flow<Email?>

    /** Resolve cache → Gmail → Room. */
    suspend fun resolveById(emailId: String): EmailResolutionResult

    /** Mark as read, once. */
    suspend fun markAsRead(emailId: String): EmailActionResult

    /** Remote body fetch, cached to Room. Returns null on failure. */
    suspend fun fetchAndCacheBody(emailId: String): BodyFetchResult?

    /** Download inline images given the refs extracted from the body. */
    suspend fun downloadInlineImages(emailId: String, refs: List<com.david.mailapp.domain.model.EmailInlineReference>): Map<String, String>

    /** Inject base64 data URIs into the HTML body. */
    suspend fun injectInlineImages(html: String, images: Map<String, String>): String

    /** Check whether a PDF is already cached and valid. */
    suspend fun checkPdfCache(emailId: String, stablePartId: String): PdfDownloadState.Ready?

    /** Download and validate a PDF attachment. */
    suspend fun downloadPdf(emailId: String, metadata: PdfAttachmentMetadata): PdfDownloadState

    /** Get the validated cached PDF file for external viewing. */
    suspend fun getValidatedCachedPdf(emailId: String, stablePartId: String): File?
}
