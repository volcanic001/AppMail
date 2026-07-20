package com.david.mailapp.data.pdf

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Unit tests for [PdfCacheManager].
 *
 * Uses a temporary directory as cacheDir so tests don't pollute
 * the real device cache and are fully deterministic on JVM.
 */
class PdfCacheManagerTest {

    private lateinit var tempDir: File
    private lateinit var cacheManager: PdfCacheManager

    @Before
    fun setup() {
        tempDir = createTempDir("pdf_cache_test_")
        cacheManager = PdfCacheManager(tempDir)
    }

    @After
    fun teardown() {
        tempDir.deleteRecursively()
    }

    // ── Store & Retrieve ─────────────────────────────────────────

    @Test
    fun `store and getCachedFile roundtrip`() {
        val emailId = "msg_001"
        val stablePartId = "part_abc"
        val bytes = pdfBytes(1024) // %PDF- header + padding

        val storedFile = cacheManager.store(emailId, stablePartId, bytes)
        assertTrue("stored file must exist", storedFile.exists())
        assertEquals("stored file size must match bytes length", bytes.size.toLong(), storedFile.length())

        val cached = cacheManager.getCachedFile(emailId, stablePartId)
        assertNotNull("getCachedFile must return the file after store", cached)
        assertEquals("cached file must be the same as stored", storedFile.absolutePath, cached!!.absolutePath)
        assertContentEquals(bytes, cached)
    }

    @Test
    fun `new manager instance restores file after process recreation`() {
        val emailId = "msg_001"
        val stablePartId = "mime_part_4"
        val bytes = pdfBytes(256)
        cacheManager.store(emailId, stablePartId, bytes)

        val recreatedManager = PdfCacheManager(tempDir)
        val restored = recreatedManager.getCachedFile(emailId, stablePartId)

        assertNotNull(restored)
        assertContentEquals(bytes, restored!!)
    }

    @Test
    fun `getCachedFile returns null for non-existent attachment`() {
        val result = cacheManager.getCachedFile("msg_001", "att_nonexistent")
        assertNull("getCachedFile must return null for non-existent attachment", result)
    }

    @Test
    fun `getCachedFile returns null when cache dir does not exist`() {
        // Delete the tempDir to simulate no cache directory
        tempDir.deleteRecursively()
        val result = cacheManager.getCachedFile("msg_001", "att_xyz")
        assertNull("getCachedFile must return null when cache dir is missing", result)
    }

    @Test
    fun `store creates file at hash-based path`() {
        val emailId = "msg_001"
        val attachmentId = "att_abc"
        val bytes = pdfBytes(100)

        cacheManager.store(emailId, attachmentId, bytes)

        val hash = cacheManager.hashKey(emailId, attachmentId)
        val expectedFile = File(tempDir, "pdf_attachments/$hash.pdf")
        assertTrue("file must exist at hash-based path", expectedFile.exists())
    }

    // ── Delete ───────────────────────────────────────────────────

    @Test
    fun `delete removes stored file`() {
        val emailId = "msg_001"
        val attachmentId = "att_abc"
        cacheManager.store(emailId, attachmentId, pdfBytes(64))

        assertNotNull("file must exist before delete", cacheManager.getCachedFile(emailId, attachmentId))

        cacheManager.delete(emailId, attachmentId)

        assertNull("file must be null after delete", cacheManager.getCachedFile(emailId, attachmentId))
    }

    @Test
    fun `delete is idempotent`() {
        // Should not throw when deleting non-existent
        cacheManager.delete("msg_001", "att_nonexistent")
        cacheManager.delete("msg_001", "att_nonexistent")
    }

    @Test
    fun `delete removes temp residue`() {
        val emailId = "msg_001"
        val attachmentId = "att_abc"
        val hash = cacheManager.hashKey(emailId, attachmentId)

        // Create a stale .tmp file manually
        val pdfDir = File(tempDir, "pdf_attachments")
        pdfDir.mkdirs()
        val tempFile = File(pdfDir, "$hash.tmp")
        tempFile.writeBytes(pdfBytes(32))

        assertTrue("temp file must exist before delete", tempFile.exists())

        cacheManager.delete(emailId, attachmentId)

        assertFalse("temp file must be removed by delete", tempFile.exists())
    }

    // ── Hash key consistency ─────────────────────────────────────

    @Test
    fun `same inputs produce same hash`() {
        val hash1 = cacheManager.hashKey("msg_001", "att_abc")
        val hash2 = cacheManager.hashKey("msg_001", "att_abc")
        assertEquals("same inputs must produce identical hash", hash1, hash2)
    }

    @Test
    fun `different attachmentIds produce different hashes`() {
        val hash1 = cacheManager.hashKey("msg_001", "att_abc")
        val hash2 = cacheManager.hashKey("msg_001", "att_def")
        assertTrue("different attachmentIds must produce different hashes", hash1 != hash2)
    }

    @Test
    fun `different emailIds produce different hashes`() {
        val hash1 = cacheManager.hashKey("msg_001", "att_abc")
        val hash2 = cacheManager.hashKey("msg_002", "att_abc")
        assertTrue("different emailIds must produce different hashes", hash1 != hash2)
    }

    @Test
    fun `hash does not depend on fileName`() {
        // hashKey only uses emailId + attachmentId, not filename
        val hash = cacheManager.hashKey("msg_001", "att_abc")
        assertTrue("hash must be a 64-char hex string", hash.matches(Regex("[0-9a-f]{64}")))
    }

    // ── Multiple stores ──────────────────────────────────────────

    @Test
    fun `store multiple different attachments`() {
        cacheManager.store("msg_001", "att_a", pdfBytes(64))
        cacheManager.store("msg_001", "att_b", pdfBytes(128))
        cacheManager.store("msg_002", "att_a", pdfBytes(256))

        assertNotNull("msg_001/att_a", cacheManager.getCachedFile("msg_001", "att_a"))
        assertNotNull("msg_001/att_b", cacheManager.getCachedFile("msg_001", "att_b"))
        assertNotNull("msg_002/att_a", cacheManager.getCachedFile("msg_002", "att_a"))
    }

    @Test
    fun `store overwrites existing file`() {
        val emailId = "msg_001"
        val attachmentId = "att_abc"

        val small = pdfBytes(64)
        val large = pdfBytes(128)

        cacheManager.store(emailId, attachmentId, small)
        val firstFile = cacheManager.getCachedFile(emailId, attachmentId)!!

        cacheManager.store(emailId, attachmentId, large)
        val secondFile = cacheManager.getCachedFile(emailId, attachmentId)!!

        assertEquals("file must be overwritten with new size", large.size.toLong(), secondFile.length())
        assertContentEquals(large, secondFile)
        // The old file path should be the same (same hash) but content replaced
        assertEquals(firstFile.absolutePath, secondFile.absolutePath)
    }

    // ── clearAll ──────────────────────────────────────────────────

    @Test
    fun `clearAll removes all pdf and tmp files`() {
        cacheManager.store("msg_001", "att_a", pdfBytes(64))
        cacheManager.store("msg_001", "att_b", pdfBytes(128))
        cacheManager.store("msg_002", "att_a", pdfBytes(256))

        // Also create a stray .tmp file
        val pdfDir = File(tempDir, "pdf_attachments")
        val strayTmp = File(pdfDir, "stray.tmp")
        strayTmp.writeBytes(pdfBytes(32))

        val errors = cacheManager.clearAll()

        assertTrue("clearAll must report no errors", errors.isEmpty())
        assertNull("msg_001/att_a must be gone", cacheManager.getCachedFile("msg_001", "att_a"))
        assertNull("msg_001/att_b must be gone", cacheManager.getCachedFile("msg_001", "att_b"))
        assertNull("msg_002/att_a must be gone", cacheManager.getCachedFile("msg_002", "att_a"))
        assertFalse("stray .tmp must be gone", strayTmp.exists())
    }

    @Test
    fun `clearAll does not delete pdfDir itself`() {
        cacheManager.store("msg_001", "att_a", pdfBytes(64))
        val pdfDir = File(tempDir, "pdf_attachments")

        cacheManager.clearAll()

        assertTrue("pdfDir must still exist after clearAll", pdfDir.exists())
    }

    @Test
    fun `clearAll does not delete files outside pdf_attachments`() {
        cacheManager.store("msg_001", "att_a", pdfBytes(64))
        val outsideFile = File(tempDir, "keep_me.txt")
        outsideFile.writeText("this should survive")

        cacheManager.clearAll()

        assertTrue("files outside pdf_attachments must survive clearAll", outsideFile.exists())
    }

    @Test
    fun `clearAll does not delete non-pdf non-tmp files inside pdfDir`() {
        val pdfDir = File(tempDir, "pdf_attachments")
        pdfDir.mkdirs()
        val metadataFile = File(pdfDir, "metadata.json")
        metadataFile.writeText("{\"key\": \"value\"}")

        // Also store a real PDF
        cacheManager.store("msg_001", "att_a", pdfBytes(64))

        cacheManager.clearAll()

        // The JSON file must survive
        assertTrue("non-pdf/non-tmp files inside pdfDir must survive clearAll", metadataFile.exists())
        // The PDF must be gone
        assertNull("PDF must be removed", cacheManager.getCachedFile("msg_001", "att_a"))
    }

    @Test
    fun `clearAll returns empty list when pdfDir does not exist`() {
        pdfDirDoesNotExist()

        val errors = cacheManager.clearAll()

        assertTrue("clearAll must return empty list when pdfDir is missing", errors.isEmpty())
    }

    @Test
    fun `clearAll returns empty list when pdfDir is empty`() {
        val pdfDir = File(tempDir, "pdf_attachments")
        pdfDir.mkdirs()

        val errors = cacheManager.clearAll()

        assertTrue("clearAll must return empty list on empty dir", errors.isEmpty())
    }

    // ── Helpers ──────────────────────────────────────────────────

    /** Simula que el directorio pdf_attachments no existe. */
    private fun pdfDirDoesNotExist() {
        File(tempDir, "pdf_attachments").deleteRecursively()
    }

    companion object {
        /** Crea bytes con firma %PDF- válida seguido de [payloadSize] bytes de relleno. */
        private fun pdfBytes(payloadSize: Int): ByteArray {
            val header = byteArrayOf(0x25, 0x50, 0x44, 0x46, 0x2D) // %PDF-
            val payload = ByteArray(payloadSize)
            // Fill with a simple PDF-like structure
            val sample = "1 0 obj<</Type/Catalog>>endobj".toByteArray()
            for (i in payload.indices) {
                payload[i] = sample[i % sample.size]
            }
            return header + payload
        }

        private fun assertContentEquals(expected: ByteArray, file: File) {
            val actual = file.readBytes()
            assertEquals("file content length mismatch", expected.size, actual.size)
            for (i in expected.indices) {
                assertEquals("byte mismatch at index $i", expected[i].toInt(), actual[i].toInt())
            }
        }

        private fun createTempDir(prefix: String): File {
            val dir = File(System.getProperty("java.io.tmpdir"), "${prefix}_${System.nanoTime()}")
            dir.mkdirs()
            return dir
        }
    }
}
