package com.david.mailapp.data.local.converter

import com.david.mailapp.domain.model.EmailInlineReference
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.serializer

internal object InlineContentReferenceCodec {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Serializable
    private data class InlineReferenceDto(
        val contentId: String,
        val attachmentId: String,
        val mimeType: String
    )

    fun encode(items: List<EmailInlineReference>): String {
        val dtos = items.map {
            InlineReferenceDto(
                contentId = it.contentId,
                attachmentId = it.attachmentId,
                mimeType = it.mimeType
            )
        }
        return json.encodeToString(serializer<List<InlineReferenceDto>>(), dtos)
    }

    fun decode(value: String): List<EmailInlineReference> {
        if (value.isBlank() || value == "null") return emptyList()
        return try {
            val element: JsonElement = json.parseToJsonElement(value)
            if (element !is JsonArray) return emptyList()
            val dtos: List<InlineReferenceDto> = json.decodeFromJsonElement(serializer<List<InlineReferenceDto>>(), element)
            dtos.map {
                EmailInlineReference(
                    contentId = it.contentId,
                    attachmentId = it.attachmentId,
                    mimeType = it.mimeType
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
