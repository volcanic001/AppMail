package com.david.mailapp.data.local.converter

import com.david.mailapp.domain.model.PdfAttachmentMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [PdfAttachmentMetadataCodec].
 */
class PdfAttachmentMetadataCodecTest {

    @Test
    fun `encodes and decodes empty list`() {
        val result = PdfAttachmentMetadataCodec.encode(emptyList())
        assertEquals("[]", result)

        val decoded = PdfAttachmentMetadataCodec.decode(result)
        assertTrue(decoded.isEmpty())
    }

    @Test
    fun `encodes and decodes single PDF`() {
        val items = listOf(
            PdfAttachmentMetadata(
                "doc.pdf",
                "application/pdf",
                "att_1",
                4096L,
                partId = "part_1"
            )
        )
        val json = PdfAttachmentMetadataCodec.encode(items)
        val decoded = PdfAttachmentMetadataCodec.decode(json)

        assertEquals(1, decoded.size)
        with(decoded[0]) {
            assertEquals("doc.pdf", fileName)
            assertEquals("application/pdf", mimeType)
            assertEquals("att_1", attachmentId)
            assertEquals(4096L, sizeBytes)
            assertEquals("part_1", partId)
            assertEquals("part_1", stableId)
        }
    }

    @Test
    fun `legacy JSON without partId remains readable`() {
        val legacyJson = """
            [{"fileName":"old.pdf","mimeType":"application/pdf","attachmentId":"legacy_att","sizeBytes":12}]
        """.trimIndent()

        val decoded = PdfAttachmentMetadataCodec.decode(legacyJson).single()

        assertEquals(null, decoded.partId)
        assertEquals("legacy_att", decoded.stableId)
    }

    @Test
    fun `stable identity does not change with refreshed attachmentId`() {
        val first = PdfAttachmentMetadata(
            "doc.pdf", "application/pdf", "att_old", 100L, partId = "part_7"
        )
        val refreshed = first.copy(attachmentId = "att_new")

        assertEquals(first.stableId, refreshed.stableId)
    }

    @Test
    fun `encodes and decodes multiple PDFs`() {
        val items = listOf(
            PdfAttachmentMetadata("a.pdf", "application/pdf", "att_a", 1024L),
            PdfAttachmentMetadata("b.pdf", "application/pdf", "att_b", 2048L)
        )
        val json = PdfAttachmentMetadataCodec.encode(items)
        val decoded = PdfAttachmentMetadataCodec.decode(json)

        assertEquals(2, decoded.size)
        assertEquals("a.pdf", decoded[0].fileName)
        assertEquals("b.pdf", decoded[1].fileName)
    }

    @Test
    fun `encodes and decodes with null sizeBytes`() {
        val items = listOf(
            PdfAttachmentMetadata("nosize.pdf", "application/pdf", "att_nosize", null)
        )
        val json = PdfAttachmentMetadataCodec.encode(items)
        val decoded = PdfAttachmentMetadataCodec.decode(json)

        assertEquals(1, decoded.size)
        assertEquals(null, decoded[0].sizeBytes)
    }

    @Test
    fun `corrupt JSON returns empty list`() {
        assertTrue(PdfAttachmentMetadataCodec.decode("not json").isEmpty())
        assertTrue(PdfAttachmentMetadataCodec.decode("").isEmpty())
        assertTrue(PdfAttachmentMetadataCodec.decode("null").isEmpty())
        assertTrue(PdfAttachmentMetadataCodec.decode("{}").isEmpty())
        assertTrue(PdfAttachmentMetadataCodec.decode("{broken}").isEmpty())
    }
}
