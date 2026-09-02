package com.david.mailapp.data.repository

import com.david.mailapp.data.local.converter.InlineContentReferenceCodec
import com.david.mailapp.domain.model.Email
import com.david.mailapp.domain.model.EmailBodyKind
import com.david.mailapp.domain.model.EmailContentState

internal data class CleanedSyncContent(
    val emailId: String,
    val expectedRawBody: String,
    val cleanBody: String,
    val cachedContentBytes: Long
)

/** Prepares a complete provider response for the first, pre-Jsoup Room commit. */
internal fun Email.materializeForMailboxSync(
    maxBudgetBytes: Long = EMAIL_CONTENT_CACHE_BUDGET_BYTES
): Email {
    if (!pdfMetadataScanned) return withoutCachedContent()

    return when (contentState) {
        EmailContentState.NOT_FETCHED -> withoutCachedContent()
        EmailContentState.EMPTY -> copy(
            body = "",
            cleanBody = "",
            bodyKind = EmailBodyKind.UNKNOWN,
            inlineReferences = emptyList(),
            cachedContentBytes = 0L,
            contentLastAccessEpochMs = 0L
        )
        EmailContentState.READY -> {
            if (body.isBlank() || bodyKind == EmailBodyKind.UNKNOWN) {
                return withoutCachedContent()
            }

            val initialCleanBody = if (bodyKind == EmailBodyKind.PLAIN_TEXT) body else ""
            val cachedBytes = contentBytes(body, initialCleanBody, inlineReferences)
            if (cachedBytes > maxBudgetBytes) {
                withoutCachedContent()
            } else {
                copy(
                    cleanBody = initialCleanBody,
                    cachedContentBytes = cachedBytes,
                    contentLastAccessEpochMs = 0L
                )
            }
        }
    }
}

internal fun Email.toCleanedSyncContent(cleanHtml: (String) -> String): CleanedSyncContent? {
    if (contentState != EmailContentState.READY || bodyKind != EmailBodyKind.HTML || body.isBlank()) {
        return null
    }
    val cleaned = cleanHtml(body)
    return CleanedSyncContent(
        emailId = id,
        expectedRawBody = body,
        cleanBody = cleaned,
        cachedContentBytes = contentBytes(body, cleaned, inlineReferences)
    )
}

private fun contentBytes(
    body: String,
    cleanBody: String,
    inlineReferences: List<com.david.mailapp.domain.model.EmailInlineReference>
): Long = body.toByteArray(Charsets.UTF_8).size.toLong() +
    cleanBody.toByteArray(Charsets.UTF_8).size.toLong() +
    InlineContentReferenceCodec.encode(inlineReferences).toByteArray(Charsets.UTF_8).size.toLong()
