package com.david.mailapp.data.local.converter

import com.david.mailapp.domain.model.EmailInlineReference
import org.junit.Assert.assertEquals
import org.junit.Test

class InlineContentReferenceCodecTest {

    @Test
    fun `encode and decode empty or blank returns empty list`() {
        assertEquals(emptyList<EmailInlineReference>(), InlineContentReferenceCodec.decode(""))
        assertEquals(emptyList<EmailInlineReference>(), InlineContentReferenceCodec.decode("   "))
        assertEquals(emptyList<EmailInlineReference>(), InlineContentReferenceCodec.decode("null"))
    }

    @Test
    fun `decode corrupt json returns empty list safely`() {
        assertEquals(emptyList<EmailInlineReference>(), InlineContentReferenceCodec.decode("{corrupt-json]"))
        assertEquals(emptyList<EmailInlineReference>(), InlineContentReferenceCodec.decode("""{"contentId":"only_object_not_array"}"""))
    }

    @Test
    fun `roundtrip multiple references preserves order, escaped characters and unicode`() {
        val refs = listOf(
            EmailInlineReference(
                contentId = "id-1@foo.com",
                attachmentId = "att-1",
                mimeType = "image/png"
            ),
            EmailInlineReference(
                contentId = "id_2\"quotes\"<tag>",
                attachmentId = "att/2\\esc",
                mimeType = "image/jpeg"
            ),
            EmailInlineReference(
                contentId = "id-3-ñá-😊",
                attachmentId = "att-3-日本",
                mimeType = "image/gif"
            )
        )

        val encoded = InlineContentReferenceCodec.encode(refs)
        val decoded = InlineContentReferenceCodec.decode(encoded)

        assertEquals(refs.size, decoded.size)
        assertEquals(refs, decoded)
        
        // Assert order
        assertEquals("id-1@foo.com", decoded[0].contentId)
        assertEquals("id_2\"quotes\"<tag>", decoded[1].contentId)
        assertEquals("id-3-ñá-😊", decoded[2].contentId)
    }
}
