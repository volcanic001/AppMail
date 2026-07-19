package com.david.mailapp.data.local.converter

import com.david.mailapp.domain.model.PdfAttachmentMetadata
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.serializer

/**
 * Codec for serializing [PdfAttachmentMetadata] lists to/from JSON strings
 * for Room storage. Uses a private DTO so the domain model doesn't need
 * a kotlinx.serialization annotation.
 */
internal object PdfAttachmentMetadataCodec {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Serializable
    private data class PdfAttachmentDto(
        val fileName: String,
        val mimeType: String,
        val attachmentId: String,
        val sizeBytes: Long? = null,
        val partId: String? = null
    )

    fun encode(items: List<PdfAttachmentMetadata>): String {
        val dtos = items.map {
            PdfAttachmentDto(
                fileName = it.fileName,
                mimeType = it.mimeType,
                attachmentId = it.attachmentId,
                sizeBytes = it.sizeBytes,
                partId = it.partId
            )
        }
        return json.encodeToString(serializer<List<PdfAttachmentDto>>(), dtos)
    }

    fun decode(value: String): List<PdfAttachmentMetadata> {
        if (value.isBlank() || value == "null") return emptyList()
        return try {
            val element: JsonElement = json.parseToJsonElement(value)
            if (element !is JsonArray) return emptyList()
            val dtos: List<PdfAttachmentDto> = json.decodeFromJsonElement(serializer<List<PdfAttachmentDto>>(), element)
            dtos.map {
                PdfAttachmentMetadata(
                    fileName = it.fileName,
                    mimeType = it.mimeType,
                    attachmentId = it.attachmentId,
                    sizeBytes = it.sizeBytes,
                    partId = it.partId
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
