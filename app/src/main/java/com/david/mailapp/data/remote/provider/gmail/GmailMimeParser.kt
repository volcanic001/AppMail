package com.david.mailapp.data.remote.provider.gmail

import com.david.mailapp.core.html.appendEmbeddedImageTags
import com.david.mailapp.core.html.ensureInlineImageTags
import com.david.mailapp.domain.model.EmailBodyKind
import com.david.mailapp.domain.model.EmailContentState
import com.david.mailapp.domain.model.EmailInlineReference
import com.david.mailapp.domain.model.PdfAttachmentMetadata
import java.util.Base64

internal data class ParsedMimeContent(
    val body: String?,
    val bodyKind: EmailBodyKind,
    val contentState: EmailContentState,
    val inlineReferences: List<EmailInlineReference>,
    val pdfAttachments: List<PdfAttachmentMetadata>
)

internal object GmailMimeParser {

    fun parse(message: MessageResponse): ParsedMimeContent {
        var firstValidHtml: String? = null
        var firstValidPlain: String? = null
        
        val inlineRefs = mutableListOf<EmailInlineReference>()
        val seenInlineAttachmentIds = mutableSetOf<String>()
        val embeddedCidImages = linkedMapOf<String, String>()
        val standaloneEmbeddedImages = mutableListOf<String>()
        val pdfs = mutableListOf<PdfAttachmentMetadata>()
        val seenPdfAttIds = mutableSetOf<String>()

        fun walk(node: Payload) {
            // 1. Process inline references (CID)
            val cid = node.contentId
            val attId = node.body?.attachmentId
            val mime = node.mimeType
            
            val normalizedCid = cid?.removePrefix("<")?.removeSuffix(">")?.trim()
                ?.takeIf { it.isNotEmpty() }
            val isImage = mime?.startsWith("image/", ignoreCase = true) == true

            if (isImage && !attId.isNullOrBlank() && seenInlineAttachmentIds.add(attId)) {
                inlineRefs.add(
                    EmailInlineReference(
                        contentId = normalizedCid ?: "mailapp-inline-image-${inlineRefs.size + 1}",
                        attachmentId = attId,
                        mimeType = mime
                    )
                )
            } else if (isImage && attId.isNullOrBlank()) {
                node.body?.data
                    ?.takeIf { it.isNotBlank() }
                    ?.let { data -> imageDataUri(mime, data) }
                    ?.let { dataUri ->
                        if (normalizedCid != null) {
                            embeddedCidImages[normalizedCid] = dataUri
                        } else {
                            standaloneEmbeddedImages += dataUri
                        }
                    }
            }

            // 2. Process PDF attachments
            val fname = node.filename
            if (mime == "application/pdf" && !fname.isNullOrBlank() && fname.endsWith(".pdf", ignoreCase = true)) {
                val pdfAttId = attId?.trim()
                if (!pdfAttId.isNullOrBlank()) {
                    val disposition = node.headers?.headerValue("Content-Disposition")
                    val isExplicitAttachment = disposition != null && disposition.startsWith("attachment", ignoreCase = true)
                    val isInlineImage = disposition != null && disposition.startsWith("inline", ignoreCase = true)
                    
                    if (!isInlineImage) {
                        if (isExplicitAttachment || cid.isNullOrBlank()) {
                            if (seenPdfAttIds.add(pdfAttId)) {
                                pdfs.add(
                                    PdfAttachmentMetadata(
                                        fileName = fname,
                                        mimeType = "application/pdf",
                                        attachmentId = pdfAttId,
                                        sizeBytes = node.body?.size?.toLong(),
                                        partId = node.partId?.trim()?.takeIf { it.isNotEmpty() }
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // 3. Process Body Candidates
            // "No seleccionar como cuerpo partes con nombre de archivo o attachmentId"
            val isBodyCandidate = attId == null && fname.isNullOrEmpty()
            if (isBodyCandidate) {
                val data = node.body?.data
                if (!data.isNullOrBlank()) {
                    if (mime?.equals("text/html", ignoreCase = true) == true && firstValidHtml == null) {
                        try {
                            val decoded = decodeBase64UrlSafe(data)
                            if (decoded.isNotBlank()) {
                                firstValidHtml = decoded
                            }
                        } catch (e: Exception) {
                            // "Continuar buscando si una parte candidata tiene Base64 inválido."
                        }
                    } else if (mime?.equals("text/plain", ignoreCase = true) == true && firstValidPlain == null) {
                        try {
                            val decoded = decodeBase64UrlSafe(data)
                            if (decoded.isNotBlank()) {
                                firstValidPlain = decoded
                            }
                        } catch (e: Exception) {
                            // Ignore invalid base64
                        }
                    }
                }
            }

            // 4. Recurse
            node.parts?.forEach { walk(it) }
        }

        message.payload?.let { walk(it) }

        val imageFallbackHtml = if (
            inlineRefs.isNotEmpty() ||
            embeddedCidImages.isNotEmpty() ||
            standaloneEmbeddedImages.isNotEmpty()
        ) {
            buildImageFallbackHtml(
                plainText = firstValidPlain,
                attachmentRefs = inlineRefs,
                embeddedDataUris = embeddedCidImages.values + standaloneEmbeddedImages
            )
        } else {
            null
        }

        // Prefer the sender's HTML. If it does not exist, image MIME parts are
        // still visible content and get a minimal HTML document of their own.
        val (finalBody, finalKind) = when {
            firstValidHtml != null -> {
                val withEmbeddedCidImages = injectEmbeddedCidImages(firstValidHtml!!, embeddedCidImages)
                val withAttachmentImages = ensureInlineImageTags(withEmbeddedCidImages, inlineRefs)
                appendEmbeddedImageTags(
                    withAttachmentImages,
                    embeddedCidImages.values + standaloneEmbeddedImages
                ) to EmailBodyKind.HTML
            }
            imageFallbackHtml != null -> imageFallbackHtml to EmailBodyKind.HTML
            firstValidPlain != null -> firstValidPlain to EmailBodyKind.PLAIN_TEXT
            else -> null to EmailBodyKind.UNKNOWN
        }

        val state = if (finalBody == null) EmailContentState.EMPTY else EmailContentState.READY

        return ParsedMimeContent(
            body = finalBody,
            bodyKind = finalKind,
            contentState = state,
            inlineReferences = inlineRefs,
            pdfAttachments = pdfs
        )
    }

    private fun decodeBase64UrlSafe(data: String): String {
        val clean = data.filter { !it.isWhitespace() }
        val bytes = Base64.getUrlDecoder().decode(clean)
        return String(bytes, Charsets.UTF_8)
    }

    private fun imageDataUri(mimeType: String, data: String): String? = try {
        val validatedMimeType = validateMimeType(mimeType)
        val clean = data.filter { !it.isWhitespace() }
        val bytes = Base64.getUrlDecoder().decode(clean)
        "data:$validatedMimeType;base64,${Base64.getEncoder().encodeToString(bytes)}"
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun validateMimeType(mimeType: String): String {
        return if (mimeType.matches(Regex("^[a-zA-Z0-9/+.-]+$"))) {
            mimeType
        } else {
            "image/png"
        }
    }

    private fun buildImageFallbackHtml(
        plainText: String?,
        attachmentRefs: List<EmailInlineReference>,
        embeddedDataUris: List<String>
    ): String = buildString {
        append("<div>")
        if (!plainText.isNullOrBlank()) {
            append("<div style=\"white-space:pre-wrap\">")
            append(escapeHtml(plainText))
            append("</div>")
        }
        attachmentRefs.forEach { ref ->
            append("<img src=\"cid:")
            append(escapeHtml(ref.contentId))
            append("\" alt=\"\">")
        }
        embeddedDataUris.forEach { dataUri ->
            append("<img src=\"")
            append(dataUri)
            append("\" alt=\"\">")
        }
        append("</div>")
    }

    private fun injectEmbeddedCidImages(
        html: String,
        images: Map<String, String>
    ): String {
        var result = html
        images.entries.sortedByDescending { it.key.length }.forEach { (cid, dataUri) ->
            result = result
                .replace("cid:&lt;$cid&gt;", dataUri, ignoreCase = true)
                .replace("cid:<$cid>", dataUri, ignoreCase = true)
                .replace("cid:$cid", dataUri, ignoreCase = true)
        }
        return result
    }

    private fun escapeHtml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
