package com.david.mailapp.domain.model

data class PdfAttachmentMetadata(
    val fileName: String,
    val mimeType: String,
    val attachmentId: String,
    val sizeBytes: Long?,
    val partId: String? = null
) {
    /**
     * Gmail documents partId as immutable. attachmentId is only a retrieval
     * token and may change between messages.get responses.
     */
    val stableId: String
        get() = partId?.trim()?.takeIf { it.isNotEmpty() } ?: attachmentId
}
