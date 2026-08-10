package com.david.mailapp.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.david.mailapp.core.session.SessionWriteGuardImpl
import com.david.mailapp.data.local.MailDatabase
import com.david.mailapp.data.pdf.PdfCacheManager
import com.david.mailapp.data.pdf.PdfDownloadFailure
import com.david.mailapp.data.pdf.PdfDownloadState
import com.david.mailapp.domain.model.PdfAttachmentMetadata
import com.david.mailapp.testhelpers.FakeEmailProvider
import com.david.mailapp.testhelpers.FakeSessionWriteGuard
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import kotlinx.coroutines.async
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Contratos de [EmailRepository.downloadPdf]: prevalidación, límite declarado,
 * cache hit y consultas de caché (subfase 4.1); descarga remota, postvalidación,
 * persistencia, limpieza de caché inválida y los seis [PdfDownloadFailure]
 * (subfase 4.2); sesión ausente, limpieza rechazada, cambio real de sesión y
 * refuerzo de cancelación (subfase 4.3).
 */
class EmailRepositoryPdfContractsTest {

    private lateinit var db: MailDatabase
    private lateinit var fakeProvider: FakeEmailProvider
    private lateinit var fakeWriteGuard: FakeSessionWriteGuard
    private lateinit var cacheDir: File
    private lateinit var pdfCacheManager: PdfCacheManager
    private lateinit var repository: EmailRepository
    private var providerFactoryReads = 0
    private lateinit var events: MutableList<String>

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, MailDatabase::class.java).build()
        fakeProvider = FakeEmailProvider()
        fakeWriteGuard = FakeSessionWriteGuard()
        cacheDir = File(context.cacheDir, "pdf_contract_test_${System.nanoTime()}")
        cacheDir.mkdirs()
        pdfCacheManager = PdfCacheManager(cacheDir)
        events = mutableListOf()
        fakeProvider.eventLog = events
        fakeWriteGuard.eventLog = events
        repository = EmailRepository(
            database = db,
            providerFactory = {
                providerFactoryReads++
                fakeProvider
            },
            pdfCacheManager = pdfCacheManager,
            writeGuard = fakeWriteGuard
        )
    }

    @After
    fun tearDown() {
        db.close()
        cacheDir.deleteRecursively()
    }

    // ═══════════════════════════════════════════════════════════════
    // Prevalidación: sin provider, sin red, sin commit y sin archivos
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun c1_downloadPdf_wrong_mime_returns_invalid_pdf_without_side_effects() = runTest {
        val metadata = PdfAttachmentMetadata("doc.pdf", "text/plain", "att-1", 1_024L)

        val state = repository.downloadPdf("e1", metadata)

        assertEquals(PdfDownloadFailure.INVALID_PDF, (state as PdfDownloadState.Error).reason)
        assertNoSideEffects()
    }

    @Test
    fun c2_downloadPdf_missing_pdf_extension_returns_invalid_pdf_without_side_effects() = runTest {
        val metadata = PdfAttachmentMetadata("report.docx", "application/pdf", "att-1", 1_024L)

        val state = repository.downloadPdf("e2", metadata)

        assertEquals(PdfDownloadFailure.INVALID_PDF, (state as PdfDownloadState.Error).reason)
        assertNoSideEffects()
    }

    @Test
    fun c3_downloadPdf_blank_attachmentId_returns_invalid_pdf_without_side_effects() = runTest {
        val metadata = PdfAttachmentMetadata("a.pdf", "application/pdf", "   ", 1_024L)

        val state = repository.downloadPdf("e3", metadata)

        assertEquals(PdfDownloadFailure.INVALID_PDF, (state as PdfDownloadState.Error).reason)
        assertNoSideEffects()
    }

    @Test
    fun c4_downloadPdf_declared_size_over_limit_returns_too_large_without_side_effects() = runTest {
        val metadata = PdfAttachmentMetadata(
            "big.pdf", "application/pdf", "att-1", EmailRepository.MAX_PDF_SIZE + 1
        )

        val state = repository.downloadPdf("e4", metadata)

        assertEquals(PdfDownloadFailure.TOO_LARGE, (state as PdfDownloadState.Error).reason)
        assertNoSideEffects()
    }

    // ═══════════════════════════════════════════════════════════════
    // Límite exacto y cache hit
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun c5_downloadPdf_exact_max_size_and_cache_hit_ready_without_network() = runTest {
        val metadata = PdfAttachmentMetadata(
            fileName = "report.PDF",
            mimeType = "application/pdf",
            attachmentId = "att-1",
            sizeBytes = EmailRepository.MAX_PDF_SIZE,
            partId = "0.1"
        )
        val bytes = validPdfBytes(512)
        // Cacheado por stableId = "0.1" (partId), no por attachmentId
        pdfCacheManager.store("e5", "0.1", bytes)

        val state = repository.downloadPdf("e5", metadata)

        assertTrue("Expected Ready, got $state", state is PdfDownloadState.Ready)
        assertEquals(bytes.size.toLong(), (state as PdfDownloadState.Ready).sizeBytes)
        assertEquals("Cache hit must not download", 0, fakeProvider.downloadAttachmentCalls)
        assertEquals("Cache hit must not resolve the provider", 0, providerFactoryReads)
        assertEquals("Cache hit must not attempt a commit", 0, fakeWriteGuard.commitCalls)
        // El archivo en caché permanece intacto
        val cached = pdfCacheManager.getCachedFile("e5", "0.1")!!
        assertEquals(bytes.size.toLong(), cached.length())
        assertArrayEquals(bytes, cached.readBytes())
    }

    // ═══════════════════════════════════════════════════════════════
    // Consultas de caché: archivo ausente
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun c6_cached_queries_missing_file_all_reject() = runTest {
        assertFalse(repository.isPdfCached("e6", "0.1"))
        assertNull(repository.checkPdfCache("e6", "0.1"))
        assertNull(repository.getValidatedCachedPdf("e6", "0.1"))
        assertNoRemoteOrCommit()
    }

    // ═══════════════════════════════════════════════════════════════
    // Consultas de caché: archivo vacío
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun c7_cached_queries_empty_file_all_reject() = runTest {
        pdfCacheManager.store("e7", "0.1", ByteArray(0))

        assertFalse(repository.isPdfCached("e7", "0.1"))
        assertNull(repository.checkPdfCache("e7", "0.1"))
        assertNull(repository.getValidatedCachedPdf("e7", "0.1"))
        assertNoRemoteOrCommit()
    }

    // ═══════════════════════════════════════════════════════════════
    // Consultas de caché: firma truncada menor de cinco bytes
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun c8_cached_queries_truncated_signature_all_reject() = runTest {
        pdfCacheManager.store("e8", "0.1", byteArrayOf(0x25, 0x50))

        assertFalse(repository.isPdfCached("e8", "0.1"))
        assertNull(repository.checkPdfCache("e8", "0.1"))
        assertNull(repository.getValidatedCachedPdf("e8", "0.1"))
        assertNoRemoteOrCommit()
    }

    // ═══════════════════════════════════════════════════════════════
    // Consultas de caché: archivo sobredimensionado (MAX + 1)
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun c9_cached_queries_oversized_file_all_reject() = runTest {
        val file = pdfCacheManager.store("e9", "0.1", validPdfBytes(64))
        // Extiende sin reservar 25 MiB en memoria
        RandomAccessFile(file, "rw").use { it.setLength(EmailRepository.MAX_PDF_SIZE + 1) }

        assertFalse(repository.isPdfCached("e9", "0.1"))
        assertNull(repository.checkPdfCache("e9", "0.1"))
        assertNull(repository.getValidatedCachedPdf("e9", "0.1"))
        assertNoRemoteOrCommit()
    }

    // ═══════════════════════════════════════════════════════════════
    // Consultas de caché: archivo válido
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun c10_cached_queries_valid_file_all_accept() = runTest {
        val bytes = validPdfBytes(4_096)
        pdfCacheManager.store("e10", "0.1", bytes)
        val cached = pdfCacheManager.getCachedFile("e10", "0.1")!!

        assertTrue(repository.isPdfCached("e10", "0.1"))
        assertEquals(bytes.size.toLong(), repository.checkPdfCache("e10", "0.1")?.sizeBytes)
        assertEquals(cached.absolutePath, repository.getValidatedCachedPdf("e10", "0.1")?.absolutePath)
        assertNoRemoteOrCommit()
    }

    // ═══════════════════════════════════════════════════════════════
    // C11 — Descarga válida con stableId
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun c11_downloadPdf_valid_download_uses_stableId_persists_and_returns_ready() = runTest {
        val metadata = PdfAttachmentMetadata(
            fileName = "report.pdf",
            mimeType = "application/pdf",
            attachmentId = "att-ignored",
            sizeBytes = 10_000L,
            partId = " 0.1 "
        )
        val pdfBytes = validPdfBytes(2_048)
        fakeProvider.downloadAttachmentResult = pdfBytes

        val state = repository.downloadPdf("e11", metadata)

        assertTrue("Expected Ready, got $state", state is PdfDownloadState.Ready)
        assertEquals(pdfBytes.size.toLong(), (state as PdfDownloadState.Ready).sizeBytes)
        assertEquals(
            listOf("e11" to "att-ignored"),
            fakeProvider.receivedDownloadAttachmentRequests
        )
        assertEquals(1, fakeWriteGuard.commitCalls)
        assertEquals(
            listOf("gmail.downloadAttachment", "room.commit"),
            events
        )

        // Archivo almacenado por stableId (partId recortado), no por attachmentId
        val cached = pdfCacheManager.getCachedFile("e11", "0.1")
        assertTrue("File must be cached under stableId", cached?.exists() == true)
        assertArrayEquals(pdfBytes, cached!!.readBytes())
        assertNoTmpResidues()
    }

    // ═══════════════════════════════════════════════════════════════
    // C12 — Contenido vacío
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun c12_downloadPdf_empty_content_returns_empty_content_without_commit() = runTest {
        val metadata = PdfAttachmentMetadata("e.pdf", "application/pdf", "att-1", null)
        fakeProvider.downloadAttachmentResult = ByteArray(0)

        val state = repository.downloadPdf("e12", metadata)

        assertEquals(PdfDownloadFailure.EMPTY_CONTENT, (state as PdfDownloadState.Error).reason)
        assertEquals(0, fakeWriteGuard.commitCalls)
        assertEquals(listOf("gmail.downloadAttachment"), events)
        assertCacheHasNoFiles()
    }

    // ═══════════════════════════════════════════════════════════════
    // C13 — Tamaño real excesivo
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun c13_downloadPdf_actual_size_too_large_returns_too_large_without_commit() = runTest {
        val metadata = PdfAttachmentMetadata("big.pdf", "application/pdf", "att-1", null)
        val oversized = ByteArray((EmailRepository.MAX_PDF_SIZE + 1).toInt())
        // Escribe la firma válida en los primeros cinco bytes
        oversized[0] = 0x25; oversized[1] = 0x50; oversized[2] = 0x44
        oversized[3] = 0x46; oversized[4] = 0x2D
        fakeProvider.downloadAttachmentResult = oversized

        val state = repository.downloadPdf("e13", metadata)

        assertEquals(PdfDownloadFailure.TOO_LARGE, (state as PdfDownloadState.Error).reason)
        assertEquals(0, fakeWriteGuard.commitCalls)
        assertEquals(listOf("gmail.downloadAttachment"), events)
        assertCacheHasNoFiles()
    }

    // ═══════════════════════════════════════════════════════════════
    // C14 — Firma inválida
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun c14_downloadPdf_invalid_signature_returns_invalid_pdf_without_commit() = runTest {
        val metadata = PdfAttachmentMetadata("m.pdf", "application/pdf", "att-1", null)
        fakeProvider.downloadAttachmentResult = "NOTPDF".toByteArray()

        val state = repository.downloadPdf("e14", metadata)

        assertEquals(PdfDownloadFailure.INVALID_PDF, (state as PdfDownloadState.Error).reason)
        assertEquals(0, fakeWriteGuard.commitCalls)
        assertEquals(listOf("gmail.downloadAttachment"), events)
        assertCacheHasNoFiles()
    }

    // ═══════════════════════════════════════════════════════════════
    // C15 — Provider ausente (sin caché)
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun c15_downloadPdf_no_provider_returns_no_provider_without_download() = runTest {
        val metadata = PdfAttachmentMetadata("a.pdf", "application/pdf", "att-1", null)
        val repositoryWithoutProvider = EmailRepository(
            database = db, providerFactory = { null },
            pdfCacheManager = pdfCacheManager, writeGuard = fakeWriteGuard
        )

        val state = repositoryWithoutProvider.downloadPdf("e15", metadata)

        assertEquals(PdfDownloadFailure.NO_PROVIDER, (state as PdfDownloadState.Error).reason)
        assertEquals(0, fakeProvider.downloadAttachmentCalls)
        assertEquals(0, fakeWriteGuard.commitCalls)
        assertTrue(events.isEmpty())
        assertCacheHasNoFiles()
    }

    // ═══════════════════════════════════════════════════════════════
    // C16 — Error remoto
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun c16_downloadPdf_remote_error_returns_network_without_propagation() = runTest {
        val metadata = PdfAttachmentMetadata("a.pdf", "application/pdf", "att-1", null)
        fakeProvider.downloadAttachmentError = IOException("network failure")

        val state = repository.downloadPdf("e16", metadata)

        assertEquals(PdfDownloadFailure.NETWORK, (state as PdfDownloadState.Error).reason)
        assertEquals(0, fakeWriteGuard.commitCalls)
        assertEquals(listOf("gmail.downloadAttachment"), events)
        assertCacheHasNoFiles()
    }

    // ═══════════════════════════════════════════════════════════════
    // C17 — Error de escritura en caché
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun c17_downloadPdf_cache_write_error_returns_cache_write_without_residues() = runTest {
        // PdfCacheManager cuya raíz es un archivo, impidiendo crear pdf_attachments
        val blockedDir = File(cacheDir, "blocked_${System.nanoTime()}")
        blockedDir.createNewFile()
        val blockedManager = PdfCacheManager(blockedDir)
        val repo = EmailRepository(
            database = db, providerFactory = { providerFactoryReads++; fakeProvider },
            pdfCacheManager = blockedManager, writeGuard = fakeWriteGuard
        )

        val metadata = PdfAttachmentMetadata("a.pdf", "application/pdf", "att-1", null)
        fakeProvider.downloadAttachmentResult = validPdfBytes(512)

        val state = repo.downloadPdf("e17", metadata)

        assertEquals(PdfDownloadFailure.CACHE_WRITE, (state as PdfDownloadState.Error).reason)
        assertEquals(1, fakeWriteGuard.commitCalls)
        assertEquals(listOf("gmail.downloadAttachment", "room.commit"), events)
        // Sin residuos .pdf ni .tmp dentro del directorio bloqueado
        val residues = blockedDir.walkTopDown().filter {
            it.isFile && (it.name.endsWith(".pdf") || it.name.endsWith(".tmp"))
        }.toList()
        assertTrue("No .pdf or .tmp residues: $residues", residues.isEmpty())
    }

    // ═══════════════════════════════════════════════════════════════
    // C18 — Caché inválida: limpieza → descarga → almacenamiento
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun c18_downloadPdf_invalid_cache_cleanup_download_and_store_ready() = runTest {
        val metadata = PdfAttachmentMetadata(
            "report.pdf", "application/pdf", "att-1", null, partId = "0.1"
        )
        // Archivo con magic inválido bajo el stableId
        pdfCacheManager.store("e18", "0.1", "NOTPDF".toByteArray())

        val validBytes = validPdfBytes(1_024)
        fakeProvider.downloadAttachmentResult = validBytes

        val state = repository.downloadPdf("e18", metadata)

        assertTrue("Expected Ready, got $state", state is PdfDownloadState.Ready)
        assertEquals(validBytes.size.toLong(), (state as PdfDownloadState.Ready).sizeBytes)
        assertEquals(1, fakeProvider.downloadAttachmentCalls)
        assertEquals(2, fakeWriteGuard.commitCalls)
        assertEquals(
            listOf("room.commit", "gmail.downloadAttachment", "room.commit"),
            events
        )

        // El archivo antiguo fue reemplazado por el PDF válido descargado
        val cached = pdfCacheManager.getCachedFile("e18", "0.1")!!
        assertArrayEquals(validBytes, cached.readBytes())
        assertNoTmpResidues()
    }

    // ═══════════════════════════════════════════════════════════════
    // C19 — Sesión ausente desde el inicio
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun c19_downloadPdf_session_absent_returns_no_provider_file_intact() = runTest {
        val metadata = PdfAttachmentMetadata("a.pdf", "application/pdf", "att-1", null, partId = "0.1")
        val invalidBytes = "NOTPDF_INVALID".toByteArray()
        pdfCacheManager.store("e19", "0.1", invalidBytes)
        fakeWriteGuard.captureResult = null

        val state = repository.downloadPdf("e19", metadata)

        assertEquals(PdfDownloadFailure.NO_PROVIDER, (state as PdfDownloadState.Error).reason)
        assertEquals(0, providerFactoryReads)
        assertEquals(0, fakeProvider.downloadAttachmentCalls)
        assertEquals(0, fakeWriteGuard.commitCalls)
        assertTrue(events.isEmpty())
        // Archivo inválido preexistente intacto byte por byte
        val cached = pdfCacheManager.getCachedFile("e19", "0.1")!!
        assertArrayEquals(invalidBytes, cached.readBytes())
        assertNoTmpResidues()
    }

    // ═══════════════════════════════════════════════════════════════
    // C20 — Limpieza de caché inválida rechazada
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun c20_downloadPdf_cleanup_commit_rejected_returns_no_provider_file_intact() = runTest {
        val metadata = PdfAttachmentMetadata("a.pdf", "application/pdf", "att-1", null, partId = "0.1")
        val invalidBytes = "NOTPDF_STALE".toByteArray()
        pdfCacheManager.store("e20", "0.1", invalidBytes)
        fakeWriteGuard.commitReturnsNullByCall = listOf(true)

        val state = repository.downloadPdf("e20", metadata)

        assertEquals(PdfDownloadFailure.NO_PROVIDER, (state as PdfDownloadState.Error).reason)
        assertEquals(0, fakeProvider.downloadAttachmentCalls)
        assertEquals(listOf("room.commit"), events)
        // Archivo inválido intacto; sin .tmp
        val cached = pdfCacheManager.getCachedFile("e20", "0.1")!!
        assertArrayEquals(invalidBytes, cached.readBytes())
        assertNoTmpResidues()
    }

    // ═══════════════════════════════════════════════════════════════
    // C21 — Cambio real de sesión con descarga antigua pendiente
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun c21_downloadPdf_session_change_pending_download_old_rejected_new_persists() = runTest {
        val metadata = PdfAttachmentMetadata(
            "report.pdf", "application/pdf", "att-1", null, partId = "0.1"
        )
        val sharedCache = PdfCacheManager(cacheDir)
        val realGuard = SessionWriteGuardImpl()
        realGuard.activate() // generación 1

        val oldProvider = FakeEmailProvider()
        val oldGate = CompletableDeferred<Unit>()
        oldProvider.downloadAttachmentDeferred = oldGate
        oldProvider.downloadAttachmentStarted = CompletableDeferred()
        val oldBytes = validPdfBytes(512)
        oldProvider.downloadAttachmentResult = oldBytes

        val oldRepo = EmailRepository(
            database = db, providerFactory = { oldProvider },
            pdfCacheManager = sharedCache, writeGuard = realGuard
        )

        val job = async { oldRepo.downloadPdf("e21", metadata) }
        oldProvider.downloadAttachmentStarted!!.await() // lease gen=1 capturado

        // La sesión cambia mientras la descarga antigua está pendiente
        realGuard.invalidate()
        realGuard.activate() // generación 2

        val newProvider = FakeEmailProvider()
        val newBytes = validPdfBytes(2_048)
        newProvider.downloadAttachmentResult = newBytes
        val newRepo = EmailRepository(
            database = db, providerFactory = { newProvider },
            pdfCacheManager = sharedCache, writeGuard = realGuard
        )

        // La sesión nueva descarga y almacena bytes nuevos en el mismo stableId
        val newState = newRepo.downloadPdf("e21", metadata)
        assertTrue("New session expected Ready, got $newState", newState is PdfDownloadState.Ready)
        assertEquals(newBytes.size.toLong(), (newState as PdfDownloadState.Ready).sizeBytes)

        // Liberar la descarga antigua
        oldGate.complete(Unit)
        val oldState = job.await()

        // El commit del lease antiguo es rechazado → NO_PROVIDER
        assertEquals(
            PdfDownloadFailure.NO_PROVIDER,
            (oldState as PdfDownloadState.Error).reason
        )

        // La caché conserva exclusivamente los bytes de la sesión nueva
        val cached = sharedCache.getCachedFile("e21", "0.1")!!
        assertArrayEquals(newBytes, cached.readBytes())
        assertNoTmpResidues()

        // Ambos providers recibieron exactamente una solicitud con los argumentos esperados
        assertEquals(1, oldProvider.downloadAttachmentCalls)
        assertEquals(
            listOf("e21" to "att-1"),
            oldProvider.receivedDownloadAttachmentRequests
        )
        assertEquals(1, newProvider.downloadAttachmentCalls)
        assertEquals(
            listOf("e21" to "att-1"),
            newProvider.receivedDownloadAttachmentRequests
        )
    }

    // ═══════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════

    private fun assertNoSideEffects() {
        assertNoRemoteOrCommit()
        assertCacheHasNoFiles()
    }

    private fun assertNoRemoteOrCommit() {
        assertEquals("Provider must not be resolved", 0, providerFactoryReads)
        assertEquals("downloadAttachment must not be called", 0, fakeProvider.downloadAttachmentCalls)
        assertEquals("commit must not be attempted", 0, fakeWriteGuard.commitCalls)
    }

    private fun assertCacheHasNoFiles() {
        val files = cacheDir.walkTopDown().filter { it.isFile }.toList()
        assertTrue("No cache files should be created: $files", files.isEmpty())
    }

    private fun assertNoTmpResidues() {
        val residues = cacheDir.walkTopDown().filter {
            it.isFile && it.name.endsWith(".tmp")
        }.toList()
        assertTrue("No .tmp residues should remain: $residues", residues.isEmpty())
    }

    private fun validPdfBytes(payloadSize: Int): ByteArray {
        val header = byteArrayOf(0x25, 0x50, 0x44, 0x46, 0x2D) // %PDF-
        val payload = ByteArray(payloadSize)
        val sample = "1 0 obj<</Type/Catalog>>endobj".toByteArray()
        for (i in payload.indices) {
            payload[i] = sample[i % sample.size]
        }
        return header + payload
    }
}
