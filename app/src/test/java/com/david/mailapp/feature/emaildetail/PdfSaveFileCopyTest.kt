package com.david.mailapp.feature.emaildetail

import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfSaveFileCopyTest {

    @Test
    fun `copy writes every source byte`() {
        val contents = ByteArray(16_777) { (it % 251).toByte() }
        val source = File.createTempFile("pdf-save-copy", ".pdf")
        try {
            source.writeBytes(contents)
            val destination = ByteArrayOutputStream()

            assertTrue(copyFileToStream(source, destination))
            assertArrayEquals(contents, destination.toByteArray())
        } finally {
            source.delete()
        }
    }

    @Test
    fun `copy fails when the SAF provider supplies no output stream`() {
        val source = File.createTempFile("pdf-save-null-stream", ".pdf")
        try {
            source.writeBytes(byteArrayOf(0x25, 0x50, 0x44, 0x46))

            assertFalse(copyFileToStream(source, null))
        } finally {
            source.delete()
        }
    }
}
