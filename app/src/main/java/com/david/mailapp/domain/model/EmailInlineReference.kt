package com.david.mailapp.domain.model

data class EmailInlineReference(
    val contentId: String,
    val attachmentId: String,
    val mimeType: String
)
