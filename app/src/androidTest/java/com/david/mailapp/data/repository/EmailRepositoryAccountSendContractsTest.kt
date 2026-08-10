package com.david.mailapp.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.david.mailapp.data.local.MailDatabase
import com.david.mailapp.data.local.entity.EmailEntity
import com.david.mailapp.data.pdf.PdfCacheManager
import com.david.mailapp.data.remote.provider.ReplyContext
import com.david.mailapp.domain.model.EmailFolder
import com.david.mailapp.testhelpers.FakeEmailProvider
import com.david.mailapp.testhelpers.FakeSessionWriteGuard
import com.david.mailapp.testhelpers.SendRequest
import com.david.mailapp.testhelpers.testEmail
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Contratos de [EmailRepository.getUserEmail] y [EmailRepository.sendEmail]:
 * provider dinámico, resultado nulo, errores y cancelación para identidad;
 * delegación exacta de los seis argumentos, ausencia de provider, login/logout,
 * error y cancelación para envío. Todos los casos conservan la fila Room y el
 * PDF cacheado previamente intactos, sin commits ni temporales.
 */
class EmailRepositoryAccountSendContractsTest {

    private lateinit var db: MailDatabase
    private lateinit var fakeWriteGuard: FakeSessionWriteGuard
    private lateinit var cacheDir: File
    private lateinit var pdfCacheManager: PdfCacheManager
    private lateinit var events: MutableList<String>
    private lateinit var cachedPdfBytes: ByteArray
    private lateinit var seededEntity: EmailEntity

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, MailDatabase::class.java).build()
        fakeWriteGuard = FakeSessionWriteGuard()
        cacheDir = File(context.cacheDir, "account_test_${System.nanoTime()}")
        cacheDir.mkdirs()
        pdfCacheManager = PdfCacheManager(cacheDir)
        events = mutableListOf()
        fakeWriteGuard.eventLog = events
        cachedPdfBytes = validPdfBytes(512)
    }

    @After
    fun tearDown() {
        db.close()
        cacheDir.deleteRecursively()
    }

    private fun validPdfBytes(payloadSize: Int): ByteArray {
        val header = byteArrayOf(0x25, 0x50, 0x44, 0x46, 0x2D)
        val payload = ByteArray(payloadSize)
        val sample = "1 0 obj<</Type/Catalog>>endobj".toByteArray()
        for (i in payload.indices) payload[i] = sample[i % sample.size]
        return header + payload
    }

    private suspend fun seedRowAndPdf() {
        seededEntity = EmailEntity.fromDomain(
            testEmail(id = "s1", folder = EmailFolder.Inbox).copy(
                isRead = true,
                rfcMessageId = "<msg-s1@test.com>",
                rfcReferences = "<ref@test.com>"
            ),
            EmailFolder.Inbox
        )
        db.emailDao().upsertAll(listOf(seededEntity))
        pdfCacheManager.store("s1", "0.1", cachedPdfBytes)
    }

    private suspend fun assertRowAndPdfIntact() {
        val entity = db.emailDao().getEntitiesByIdsSync(listOf("s1")).firstOrNull()
        assertTrue("Seeded row must still exist", entity != null)
        assertEquals("Seeded Room entity must remain fully intact", seededEntity, entity)

        val cached = pdfCacheManager.getCachedFile("s1", "0.1")
        assertTrue("Cached PDF must still exist", cached?.exists() == true)
        assertArrayEquals(cachedPdfBytes, cached!!.readBytes())

        val residues = cacheDir.walkTopDown().filter {
            it.isFile && it.name.endsWith(".tmp")
        }.toList()
        assertTrue("No .tmp residues: $residues", residues.isEmpty())
    }

    // ═══════════════════════════════════════════════════════════════
    // C1 — getUserEmail con provider dinámico
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun c1_getUserEmail_dynamic_provider_no_reuse() = runTest {
        seedRowAndPdf()
        val providerA = FakeEmailProvider().apply { userEmailResult = "a@test.com"; eventLog = events }
        val providerB = FakeEmailProvider().apply { userEmailResult = "b@test.com"; eventLog = events }

        var current: FakeEmailProvider? = providerA
        val repo = EmailRepository(
            database = db, providerFactory = { current },
            pdfCacheManager = pdfCacheManager, writeGuard = fakeWriteGuard
        )

        assertEquals("a@test.com", repo.getUserEmail())
        current = null
        assertEquals(null, repo.getUserEmail())
        current = providerB
        assertEquals("b@test.com", repo.getUserEmail())

        assertEquals(1, providerA.getUserEmailCalls)
        assertEquals(1, providerB.getUserEmailCalls)
        assertEquals(0, fakeWriteGuard.commitCalls)
        assertEquals(
            listOf("gmail.getUserEmail", "gmail.getUserEmail"),
            events
        )
        assertRowAndPdfIntact()
    }

    // ═══════════════════════════════════════════════════════════════
    // C2 — getUserEmail con resultado nulo
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun c2_getUserEmail_null_result_preserved() = runTest {
        seedRowAndPdf()
        val provider = FakeEmailProvider().apply { userEmailResult = null; eventLog = events }
        val repo = EmailRepository(
            database = db, providerFactory = { provider },
            pdfCacheManager = pdfCacheManager, writeGuard = fakeWriteGuard
        )

        assertEquals(null, repo.getUserEmail())
        assertEquals(1, provider.getUserEmailCalls)
        assertEquals(0, fakeWriteGuard.commitCalls)
        assertEquals(listOf("gmail.getUserEmail"), events)
        assertRowAndPdfIntact()
    }

    // ═══════════════════════════════════════════════════════════════
    // C3 — getUserEmail con error remoto
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun c3_getUserEmail_remote_error_propagates_same_instance() = runTest {
        seedRowAndPdf()
        val sentinel = IOException("sentinel identity failure")
        val provider = FakeEmailProvider().apply {
            getUserEmailError = sentinel
            eventLog = events
        }
        val repo = EmailRepository(
            database = db, providerFactory = { provider },
            pdfCacheManager = pdfCacheManager, writeGuard = fakeWriteGuard
        )

        val thrown = try {
            repo.getUserEmail()
            null
        } catch (e: IOException) {
            e
        }

        assertSame(sentinel, thrown)
        assertEquals(1, provider.getUserEmailCalls)
        assertEquals(0, fakeWriteGuard.commitCalls)
        assertEquals(listOf("gmail.getUserEmail"), events)
        assertRowAndPdfIntact()
    }

    // ═══════════════════════════════════════════════════════════════
    // C4 — getUserEmail con cancelación
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun c4_getUserEmail_remote_cancellation_propagates_same_instance() = runTest {
        seedRowAndPdf()
        val sentinel = CancellationException("sentinel identity cancellation")
        val provider = FakeEmailProvider().apply {
            getUserEmailError = sentinel
            eventLog = events
        }
        val repo = EmailRepository(
            database = db, providerFactory = { provider },
            pdfCacheManager = pdfCacheManager, writeGuard = fakeWriteGuard
        )

        val thrown = try {
            repo.getUserEmail()
            null
        } catch (cancelled: CancellationException) {
            cancelled
        }

        assertSame(sentinel, thrown)
        assertEquals(1, provider.getUserEmailCalls)
        assertEquals(0, fakeWriteGuard.commitCalls)
        assertEquals(listOf("gmail.getUserEmail"), events)
        assertRowAndPdfIntact()
    }

    // ═══════════════════════════════════════════════════════════════
    // C5 — sendEmail con delegación exacta de argumentos
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun c5_sendEmail_exact_delegation_full_and_null_args() = runTest {
        seedRowAndPdf()
        val provider = FakeEmailProvider().apply { eventLog = events }
        val repo = EmailRepository(
            database = db, providerFactory = { provider },
            pdfCacheManager = pdfCacheManager, writeGuard = fakeWriteGuard
        )
        val replyContext = ReplyContext("thread-1", "<msg-1@t.com>", "<ref@t.com>")

        // Envío completo con todos los campos y ReplyContext
        repo.sendEmail("a@x.com", "c@x.com", "b@x.com", "Subject 1", "Body 1", replyContext)

        // Envío con cc, bcc y replyContext nulos
        repo.sendEmail("d@x.com", null, null, "Subject 2", "Body 2", null)

        assertEquals(
            listOf(
                SendRequest("a@x.com", "c@x.com", "b@x.com", "Subject 1", "Body 1", replyContext),
                SendRequest("d@x.com", null, null, "Subject 2", "Body 2", null)
            ),
            provider.receivedSendRequests
        )
        assertSame(replyContext, provider.receivedSendRequests.first().replyContext)
        assertEquals(2, provider.sendEmailCalls)
        assertEquals(0, fakeWriteGuard.commitCalls)
        assertEquals(listOf("gmail.sendEmail", "gmail.sendEmail"), events)
        assertRowAndPdfIntact()
    }

    // ═══════════════════════════════════════════════════════════════
    // C6 — sendEmail sin provider
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun c6_sendEmail_without_provider_throws_legacy_message() = runTest {
        seedRowAndPdf()
        val repo = EmailRepository(
            database = db, providerFactory = { null },
            pdfCacheManager = pdfCacheManager, writeGuard = fakeWriteGuard
        )

        val thrown = try {
            repo.sendEmail("to@x.com", null, null, "S", "B", null)
            null
        } catch (e: IllegalStateException) {
            e
        }

        assertTrue(thrown != null)
        assertEquals("No hay proveedor activo", thrown!!.message)
        assertEquals(0, fakeWriteGuard.commitCalls)
        assertTrue(events.isEmpty())
        assertRowAndPdfIntact()
    }

    // ═══════════════════════════════════════════════════════════════
    // C7 — sendEmail con provider dinámico durante login/logout
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun c7_sendEmail_dynamic_provider_login_and_logout() = runTest {
        seedRowAndPdf()
        val providerA = FakeEmailProvider().apply { eventLog = events }
        val providerB = FakeEmailProvider().apply { eventLog = events }

        var current: FakeEmailProvider? = providerA
        val repo = EmailRepository(
            database = db, providerFactory = { current },
            pdfCacheManager = pdfCacheManager, writeGuard = fakeWriteGuard
        )

        repo.sendEmail("a@x.com", null, null, "SA", "BA", null)
        current = null
        try { repo.sendEmail("null@x.com", null, null, "SN", "BN", null) } catch (_: IllegalStateException) {}
        current = providerB
        repo.sendEmail("b@x.com", null, null, "SB", "BB", null)

        assertEquals(1, providerA.sendEmailCalls)
        assertEquals(1, providerB.sendEmailCalls)
        assertEquals(listOf("gmail.sendEmail", "gmail.sendEmail"), events)
        assertEquals(0, fakeWriteGuard.commitCalls)
        assertRowAndPdfIntact()
    }

    // ═══════════════════════════════════════════════════════════════
    // C8 — sendEmail con error remoto
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun c8_sendEmail_remote_error_propagates_same_instance() = runTest {
        seedRowAndPdf()
        val sentinel = IOException("sentinel send failure")
        val provider = FakeEmailProvider().apply {
            sendEmailError = sentinel
            eventLog = events
        }
        val repo = EmailRepository(
            database = db, providerFactory = { provider },
            pdfCacheManager = pdfCacheManager, writeGuard = fakeWriteGuard
        )

        val thrown = try {
            repo.sendEmail("to@x.com", null, null, "S", "B", null)
            null
        } catch (e: IOException) {
            e
        }

        assertSame(sentinel, thrown)
        assertEquals(1, provider.sendEmailCalls)
        assertEquals(0, fakeWriteGuard.commitCalls)
        assertEquals(listOf("gmail.sendEmail"), events)
        assertRowAndPdfIntact()
    }

    // ═══════════════════════════════════════════════════════════════
    // C9 — sendEmail con cancelación
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun c9_sendEmail_remote_cancellation_propagates_same_instance() = runTest {
        seedRowAndPdf()
        val sentinel = CancellationException("sentinel send cancellation")
        val provider = FakeEmailProvider().apply {
            sendEmailError = sentinel
            eventLog = events
        }
        val repo = EmailRepository(
            database = db, providerFactory = { provider },
            pdfCacheManager = pdfCacheManager, writeGuard = fakeWriteGuard
        )

        val thrown = try {
            repo.sendEmail("to@x.com", null, null, "S", "B", null)
            null
        } catch (cancelled: CancellationException) {
            cancelled
        }

        assertSame(sentinel, thrown)
        assertEquals(1, provider.sendEmailCalls)
        assertEquals(0, fakeWriteGuard.commitCalls)
        assertEquals(listOf("gmail.sendEmail"), events)
        assertRowAndPdfIntact()
    }
}
