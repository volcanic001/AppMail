package com.david.mailapp.feature.emaildetail

import com.david.mailapp.core.localization.UiErrorReason
import com.david.mailapp.data.pdf.PdfDownloadState
import com.david.mailapp.data.repository.EmailActionResult
import com.david.mailapp.data.repository.EmailRepository
import com.david.mailapp.data.repository.EmailResolutionResult
import com.david.mailapp.domain.model.Email
import com.david.mailapp.domain.model.PdfAttachmentMetadata
import kotlinx.coroutines.flow.Flow
import java.io.File

/** Adapter from [EmailRepository] to [EmailDetailEmailSource]. */
class RepositoryEmailDetailSource(
    private val repository: EmailRepository
) : EmailDetailEmailSource {

    override fun observe(emailId: String): Flow<Email?> =
        repository.getEmailById(emailId)

    override suspend fun resolveById(emailId: String): EmailResolutionResult =
        repository.resolveEmailById(emailId)

    override suspend fun markAsRead(emailId: String): EmailActionResult =
        repository.markAsRead(emailId)

    override suspend fun prepareHtmlBody(email: Email): com.david.mailapp.data.cleaner.HtmlCleanResult =
        repository.prepareHtmlBody(email)

    override suspend fun recoverContentById(emailId: String): com.david.mailapp.data.repository.EmailContentRecoveryResult =
        repository.recoverContentById(emailId)

    override suspend fun downloadInlineImages(emailId: String, refs: List<com.david.mailapp.domain.model.EmailInlineReference>): Map<String, String> =
        repository.downloadInlineImages(emailId, refs)

    override suspend fun injectInlineImages(html: String, images: Map<String, String>): String =
        repository.injectInlineImages(html, images)

    override suspend fun recordContentAccess(emailId: String) {
        repository.recordContentAccess(emailId)
    }

    override suspend fun checkPdfCache(emailId: String, stablePartId: String): PdfDownloadState.Ready? =
        repository.checkPdfCache(emailId, stablePartId)

    override suspend fun downloadPdf(emailId: String, metadata: PdfAttachmentMetadata): PdfDownloadState =
        repository.downloadPdf(emailId, metadata)

    override suspend fun getValidatedCachedPdf(emailId: String, stablePartId: String): File? =
        repository.getValidatedCachedPdf(emailId, stablePartId)
}
