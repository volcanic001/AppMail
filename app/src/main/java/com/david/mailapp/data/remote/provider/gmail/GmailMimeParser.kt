package com.david.mailapp.data.remote.provider.gmail

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
        val pdfs = mutableListOf<PdfAttachmentMetadata>()
        val seenPdfAttIds = mutableSetOf<String>()

        fun walk(node: Payload) {
            // 1. Process inline references (CID)
            val cid = node.contentId
            val attId = node.body?.attachmentId
            val mime = node.mimeType
            
            if (cid != null && attId != null && mime != null) {
                val normalizedCid = cid.removePrefix("<").removeSuffix(">")
                inlineRefs.add(
                    EmailInlineReference(
                        contentId = normalizedCid,
                        attachmentId = attId,
                        mimeType = mime
                    )
                )
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

        // "Seleccionar HTML sobre texto plano. Si ninguno es válido, devolver EMPTY, UNKNOWN, cuerpo nulo..."
        val (finalBody, finalKind) = when {
            firstValidHtml != null -> firstValidHtml to EmailBodyKind.HTML
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
}
