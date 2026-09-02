package com.david.mailapp.data.repository

import com.david.mailapp.data.local.converter.InlineContentReferenceCodec
import com.david.mailapp.domain.model.Email
import com.david.mailapp.domain.model.EmailBodyKind
import com.david.mailapp.domain.model.EmailContentState

internal data class IndividualRecoveryMaterialization(
    val persistable: Email,
    val display: Email,
    val storage: EmailContentStorage
)

internal fun Email.materializeForIndividualRecovery(
    cleanHtml: (String) -> String,
    maxBudgetBytes: Long = EMAIL_CONTENT_CACHE_BUDGET_BYTES
): IndividualRecoveryMaterialization? {
    if (!pdfMetadataScanned) return null

    return when (contentState) {
        EmailContentState.NOT_FETCHED -> null
        EmailContentState.EMPTY -> {
            val empty = copy(
                body = "",
                cleanBody = "",
                bodyKind = EmailBodyKind.UNKNOWN,
                inlineReferences = emptyList(),
                cachedContentBytes = 0L,
                contentLastAccessEpochMs = 0L
            )
            IndividualRecoveryMaterialization(empty, empty, EmailContentStorage.PERSISTED)
        }
        EmailContentState.READY -> {
            if (body.isBlank() || bodyKind == EmailBodyKind.UNKNOWN) return null
            val cleaned = if (bodyKind == EmailBodyKind.HTML) cleanHtml(body) else body
            val display = copy(
                cleanBody = cleaned,
                cachedContentBytes = recoveryContentBytes(body, cleaned, inlineReferences),
                contentLastAccessEpochMs = 0L
            )
            if (display.cachedContentBytes > maxBudgetBytes) {
                IndividualRecoveryMaterialization(
                    persistable = display.withoutCachedContent(),
                    display = display,
                    storage = EmailContentStorage.MEMORY_ONLY
                )
            } else {
                IndividualRecoveryMaterialization(display, display, EmailContentStorage.PERSISTED)
            }
        }
    }
}

internal fun Email.hasCompleteCachedContent(): Boolean =
    pdfMetadataScanned &&
        pdfAttachments.all { !it.partId.isNullOrBlank() } &&
        when (contentState) {
            EmailContentState.EMPTY -> true
            EmailContentState.READY -> body.isNotBlank() &&
                bodyKind != EmailBodyKind.UNKNOWN &&
                !(inlineReferences.isEmpty() && body.contains("cid:", ignoreCase = true))
            EmailContentState.NOT_FETCHED -> false
        }

private fun recoveryContentBytes(
    body: String,
    cleanBody: String,
    inlineReferences: List<com.david.mailapp.domain.model.EmailInlineReference>
): Long = body.toByteArray(Charsets.UTF_8).size.toLong() +
    cleanBody.toByteArray(Charsets.UTF_8).size.toLong() +
    InlineContentReferenceCodec.encode(inlineReferences).toByteArray(Charsets.UTF_8).size.toLong()
